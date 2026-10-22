package net.ixdarklord.folios.internal;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.internal.client.GuideClientNetwork;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.ixdarklord.folios.api.GuideBookItem;
import net.ixdarklord.folios.api.GuideBooks;
import net.ixdarklord.folios.internal.core.GuideRegistry;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.ixdarklord.folios.internal.mixin.StructureTemplateAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// The server's side of guide books: opening them, answering recipe and structure questions, marking the book a player reads in their
// hands for everyone around to see, and keeping each player's client told which advancements they have (entries can be locked behind them, and a client only hears of the visible ones).
public final class GuideNetwork {
    // Advancements completed or revoked since the last tick, per player; sent together at the end of it.
    private static final Map<UUID, Changes> CHANGED = new HashMap<>();

    private record Changes(Set<ResourceLocation> done, Set<ResourceLocation> undone) {}

    // The hand each player reads a handheld book in.
    private static final Map<UUID, InteractionHand> READERS = new HashMap<>();

    // The spread each reader of a handheld book has open, told to the players near them to read along.
    private static final Map<UUID, GuidePayloads.ReadingAt> SPREADS = new HashMap<>();
    /** How near (blocks) a player is to be to read along with someone. */
    private static final double READ_ALONG = 16;

    private GuideNetwork() {}

    public static void init() {
        Network.registerClientbound(GuidePayloads.Open.TYPE, GuidePayloads.Open.STREAM_CODEC, (payload, context) -> GuideClientNetwork.open(payload));
        Network.registerClientbound(GuidePayloads.SharedReading.TYPE, GuidePayloads.SharedReading.STREAM_CODEC, (payload, context) -> GuideClientNetwork.sharedReading(payload));
        Network.registerServerbound(GuidePayloads.ReadingAt.TYPE, GuidePayloads.ReadingAt.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) readingAt(player, payload);
        });
        Network.registerClientbound(GuidePayloads.Structure.TYPE, GuidePayloads.Structure.STREAM_CODEC, (payload, context) -> GuideClientNetwork.structure(payload));
        Network.registerServerbound(GuidePayloads.StructureRequest.TYPE, GuidePayloads.StructureRequest.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) answerStructures(player, payload.structures());
        });
        Network.registerClientbound(GuidePayloads.Advancements.TYPE, GuidePayloads.Advancements.STREAM_CODEC, (payload, context) -> GuideClientNetwork.advancements(payload));
        PlayerEvents.JOIN.register(player -> {
            sendAll(player);
        });
        Network.registerServerbound(GuidePayloads.Reading.TYPE, GuidePayloads.Reading.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) reading(player, payload);
        });
        Network.registerServerbound(GuidePayloads.Progress.TYPE, GuidePayloads.Progress.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) progress(player, payload);
        });
        PlayerEvents.LEAVE.register(player -> {
            CHANGED.remove(player.getUUID());
            READERS.remove(player.getUUID());
            stopShared(player);
        });
        ServerTickEvents.END.register(GuideNetwork::flush);
    }

    public static void open(ServerPlayer player, ResourceLocation book, @Nullable String entry) {
        if (Network.canPlayerReceive(player, GuidePayloads.Open.TYPE)) Network.sendToPlayer(player, new GuidePayloads.Open(book, Optional.ofNullable(entry)));
    }

    // Marks the book in the reader's hand as being read, so everyone tracking them sees it open in their hands (the
    // component goes to them with the hand's stack), or takes the mark off both hands.
    private static void reading(ServerPlayer player, GuidePayloads.Reading payload) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (GuideRegistry.isReading(stack)) GuideRegistry.setReading(stack, false);
        }
        READERS.remove(player.getUUID());
        if (!payload.reading()) stopShared(player);
        if (!payload.reading() || payload.hand() < 0 || payload.hand() >= InteractionHand.values().length) return;
        InteractionHand hand = InteractionHand.values()[payload.hand()];
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof GuideBookItem) && GuideBooks.bookOf(stack) == null) return;
        GuideRegistry.setReading(stack, true);
        READERS.put(player.getUUID(), hand);
    }

    /** How many books' records a player keeps; more than any modpack has, fewer than a client could send to fill a save. */
    private static final int MAX_BOOKS = 256;

    // What a player has done with a book, sent by their client: kept with them, or, for a book that keeps its own, on
    // the book in the hand they read it in (only a guide book of that same book).
    private static void progress(ServerPlayer player, GuidePayloads.Progress payload) {
        GuideRecord record = payload.record().capped();
        if (payload.hand() < 0) {
            GuideRegistry.PLAYER_PROGRESS.update(player, all -> {
                if (!all.containsKey(payload.book()) && all.size() >= MAX_BOOKS) return all;
                Map<ResourceLocation, GuideRecord> next = new HashMap<>(all);
                next.put(payload.book(), record);
                return Map.copyOf(next);
            });
            return;
        }
        if (payload.hand() >= InteractionHand.values().length) return;
        ItemStack stack = player.getItemInHand(InteractionHand.values()[payload.hand()]);
        if (payload.book().equals(bookOf(stack))) GuideRegistry.BOOK_PROGRESS.set(stack, record.forStack());
    }

    /** The book a stack opens: a guide book item's, or any stack's that names one. */
    public static @Nullable ResourceLocation bookOf(ItemStack stack) {
        return stack.getItem() instanceof GuideBookItem item ? item.book(stack) : GuideBooks.bookOf(stack);
    }

    // A reader's open spread: kept, and told to those near them now (the rest hear of it as they come near).
    private static void readingAt(ServerPlayer player, GuidePayloads.ReadingAt payload) {
        if (!payload.open()) {
            stopShared(player);
            return;
        }
        SPREADS.put(player.getUUID(), payload);
        share(player, payload);
    }

    private static void stopShared(ServerPlayer player) {
        if (SPREADS.remove(player.getUUID()) == null) return;
        GuidePayloads.SharedReading closed = new GuidePayloads.SharedReading(player.getId(), new GuidePayloads.ReadingAt(false, GuidePayloads.NO_BOOK, "", 0, false));
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && Network.canPlayerReceive(other, GuidePayloads.SharedReading.TYPE)) Network.sendToPlayer(other, closed);
        }
    }

    private static void share(ServerPlayer player, GuidePayloads.ReadingAt payload) {
        GuidePayloads.SharedReading shared = new GuidePayloads.SharedReading(player.getId(), payload);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || other.distanceToSqr(player) > READ_ALONG * READ_ALONG) continue;
            if (Network.canPlayerReceive(other, GuidePayloads.SharedReading.TYPE)) Network.sendToPlayer(other, shared);
        }
    }

    /** Whether a stack is the one its holder is reading now; a mark on any other stack is left over and goes. */
    public static boolean isRead(Entity holder, ItemStack stack) {
        InteractionHand hand = READERS.get(holder.getUUID());
        return hand != null && holder instanceof ServerPlayer player && player.getItemInHand(hand) == stack;
    }

    // Recipe unlocks have their own advancements, one per recipe: books don't lock entries behind those, and there are
    // too many to send.
    private static boolean tracked(ResourceLocation advancement) {
        return !advancement.getPath().startsWith("recipes/");
    }

    /** Called by PlayerAdvancementsMixin when an advancement of a player's becomes done or no longer done. */
    public static void changed(ServerPlayer player, Advancement advancement, boolean done) {
        if (!tracked(advancement.getId())) return;
        Changes changes = CHANGED.computeIfAbsent(player.getUUID(), uuid -> new Changes(new LinkedHashSet<>(), new LinkedHashSet<>()));
        (done ? changes.undone : changes.done).remove(advancement.getId());
        (done ? changes.done : changes.undone).add(advancement.getId());
    }

    private static void sendAll(ServerPlayer player) {
        if (!Network.canPlayerReceive(player, GuidePayloads.Advancements.TYPE)) return;
        List<ResourceLocation> done = new ArrayList<>();
        for (Advancement advancement : player.getServer().getAdvancements().getAllAdvancements()) {
            if (tracked(advancement.getId()) && player.getAdvancements().getOrStartProgress(advancement).isDone()) done.add(advancement.getId());
        }
        Network.sendToPlayer(player, new GuidePayloads.Advancements(true, done, List.of()));
    }

    private static void flush(MinecraftServer server) {
        // Each second, those reading are told again to everyone near them: to whoever has come near since, and so a
        // client can let go of a book whose reader it no longer hears of.
        if (server.getTickCount() % 20 == 0 && !SPREADS.isEmpty()) {
            for (Map.Entry<UUID, GuidePayloads.ReadingAt> entry : SPREADS.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) share(player, entry.getValue());
            }
        }
        if (CHANGED.isEmpty()) return;
        for (Map.Entry<UUID, Changes> entry : CHANGED.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !Network.canPlayerReceive(player, GuidePayloads.Advancements.TYPE)) continue;
            Changes changes = entry.getValue();
            Network.sendToPlayer(player, new GuidePayloads.Advancements(false, List.copyOf(changes.done), List.copyOf(changes.undone)));
        }
        CHANGED.clear();
    }

    private static void answerStructures(ServerPlayer player, List<ResourceLocation> ids) {
        var structures = player.serverLevel().getServer().getStructureManager();
        for (ResourceLocation id : ids) {
            Optional<StructureTemplate> template;
            try {
                template = structures.get(id);
            } catch (Exception e) {
                template = Optional.empty();
            }
            Network.sendToPlayer(player, template.map(found -> structure(id, found))
                    .orElseGet(() -> new GuidePayloads.Structure(id, GuidePayloads.Structure.MISSING, List.of(), List.of())));
        }
    }

    // A structure's blocks as a page shows them: its first palette (a structure with several, as a shipwreck has, keeps
    // its others as variants), less air and the blocks only structures have; a jigsaw as the block it leaves behind.
    private static GuidePayloads.Structure structure(ResourceLocation id, StructureTemplate template) {
        List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).folios$palettes();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> indices = new HashMap<>();
        List<Integer> blocks = new ArrayList<>();
        if (!palettes.isEmpty()) {
            for (StructureTemplate.StructureBlockInfo block : palettes.get(0).blocks()) {
                BlockState state = block.state();
                if (state.is(Blocks.JIGSAW)) state = jigsawLeaves(block);
                if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK)) continue;
                if (blocks.size() >= GuidePayloads.MOST_STRUCTURE_BLOCKS * 4) {
                    return new GuidePayloads.Structure(id, GuidePayloads.Structure.TOO_LARGE, List.of(), List.of());
                }
                Integer index = indices.computeIfAbsent(state, added -> {
                    palette.add(added);
                    return palette.size() - 1;
                });
                blocks.add(block.pos().getX());
                blocks.add(block.pos().getY());
                blocks.add(block.pos().getZ());
                blocks.add(index);
            }
        }
        return new GuidePayloads.Structure(id, GuidePayloads.Structure.FOUND, palette, blocks);
    }

    private static BlockState jigsawLeaves(StructureTemplate.StructureBlockInfo jigsaw) {
        String finalState = jigsaw.nbt() != null && jigsaw.nbt().contains("final_state") ? jigsaw.nbt().getString("final_state") : "minecraft:air";
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), finalState, false).blockState();
        } catch (Exception e) {
            return Blocks.AIR.defaultBlockState();
        }
    }
}
