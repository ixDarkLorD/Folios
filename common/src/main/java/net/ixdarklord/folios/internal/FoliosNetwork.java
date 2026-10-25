package net.ixdarklord.folios.internal;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.internal.client.FoliosClientNetwork;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.ixdarklord.folios.api.FoliosBookItem;
import net.ixdarklord.folios.api.FoliosBooks;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
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
public final class FoliosNetwork {
    // Advancements completed or revoked since the last tick, per player; sent together at the end of it.
    private static final Map<UUID, Changes> CHANGED = new HashMap<>();

    private record Changes(Set<ResourceLocation> done, Set<ResourceLocation> undone) {}

    // The hand each player reads a handheld book in.
    private static final Map<UUID, InteractionHand> READERS = new HashMap<>();

    // The spread each reader of a handheld book has open, told to the players near them to read along.
    private static final Map<UUID, FoliosPayloads.ReadingAt> SPREADS = new HashMap<>();
    /** How near (blocks) a player is to be to read along with someone. */
    private static final double READ_ALONG = 16;

    private FoliosNetwork() {}

    public static void init() {
        Network.registerClientbound(FoliosPayloads.Open.TYPE, FoliosPayloads.Open.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.open(payload));
        Network.registerClientbound(FoliosPayloads.SharedReading.TYPE, FoliosPayloads.SharedReading.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.sharedReading(payload));
        Network.registerServerbound(FoliosPayloads.ReadingAt.TYPE, FoliosPayloads.ReadingAt.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) readingAt(player, payload);
        });
        Network.registerClientbound(FoliosPayloads.Structure.TYPE, FoliosPayloads.Structure.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.structure(payload));
        Network.registerServerbound(FoliosPayloads.StructureRequest.TYPE, FoliosPayloads.StructureRequest.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) answerStructures(player, payload.structures());
        });
        Network.registerClientbound(FoliosPayloads.Advancements.TYPE, FoliosPayloads.Advancements.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.advancements(payload));
        PlayerEvents.JOIN.register(player -> {
            sendAll(player);
            FoliosSourceData.sendAll(player);
        });
        net.ixdarklord.folios.internal.quest.QuestTracker.init();
        // Sources (FoliosSources): their rows sent from the server; a data pack's loaded with its data.
        Network.registerClientbound(FoliosPayloads.Source.TYPE, FoliosPayloads.Source.STREAM_CODEC, (payload, context) -> FoliosSourceData.receive(payload));
        net.ixdarklord.coolcatcore.api.registry.ReloadListeners.registerServer(net.ixdarklord.folios.internal.core.Folios.rl("sources"), FoliosSourceData.INSTANCE);
        ServerTickEvents.END.register(FoliosSourceData::tick);
        Network.registerServerbound(FoliosPayloads.Reading.TYPE, FoliosPayloads.Reading.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) reading(player, payload);
        });
        Network.registerServerbound(FoliosPayloads.Progress.TYPE, FoliosPayloads.Progress.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) progress(player, payload);
        });
        PlayerEvents.LEAVE.register(player -> {
            CHANGED.remove(player.getUUID());
            READERS.remove(player.getUUID());
            stopShared(player);
        });
        ServerTickEvents.END.register(FoliosNetwork::flush);
    }

    public static void open(ServerPlayer player, ResourceLocation book, @Nullable String entry) {
        net.ixdarklord.folios.internal.quest.QuestReading.opened(player, book);
        if (Network.canPlayerReceive(player, FoliosPayloads.Open.TYPE)) Network.sendToPlayer(player, new FoliosPayloads.Open(book, Optional.ofNullable(entry)));
    }

    // Marks the book in the reader's hand as being read, so everyone tracking them sees it open in their hands (the
    // component goes to them with the hand's stack), or takes the mark off both hands.
    private static void reading(ServerPlayer player, FoliosPayloads.Reading payload) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (FoliosRegistry.isReading(stack)) FoliosRegistry.setReading(stack, false);
        }
        READERS.remove(player.getUUID());
        if (!payload.reading()) stopShared(player);
        if (!payload.reading() || payload.hand() < 0 || payload.hand() >= InteractionHand.values().length) return;
        InteractionHand hand = InteractionHand.values()[payload.hand()];
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof FoliosBookItem) && FoliosBooks.bookOf(stack) == null) return;
        FoliosRegistry.setReading(stack, true);
        READERS.put(player.getUUID(), hand);
    }

    /** How many books' records a player keeps; more than any modpack has, fewer than a client could send to fill a save. */
    private static final int MAX_BOOKS = 256;

    // What a player has done with a book, sent by their client: kept with them, or, for a book that keeps its own, on
    // the book in the hand they read it in (only a guide book of that same book).
    private static void progress(ServerPlayer player, FoliosPayloads.Progress payload) {
        FoliosRecord record = payload.record().capped();
        if (payload.hand() < 0) {
            FoliosRegistry.PLAYER_PROGRESS.update(player, all -> {
                if (!all.containsKey(payload.book()) && all.size() >= MAX_BOOKS) return all;
                Map<ResourceLocation, FoliosRecord> next = new HashMap<>(all);
                next.put(payload.book(), record);
                return Map.copyOf(next);
            });
            return;
        }
        if (payload.hand() >= InteractionHand.values().length) return;
        ItemStack stack = player.getItemInHand(InteractionHand.values()[payload.hand()]);
        if (payload.book().equals(bookOf(stack))) FoliosRegistry.BOOK_PROGRESS.set(stack, record.forStack());
    }

    /** The book a stack opens: a guide book item's, or any stack's that names one. */
    public static @Nullable ResourceLocation bookOf(ItemStack stack) {
        return stack.getItem() instanceof FoliosBookItem item ? item.book(stack) : FoliosBooks.bookOf(stack);
    }

    // A reader's open spread: kept, and told to those near them now (the rest hear of it as they come near).
    private static void readingAt(ServerPlayer player, FoliosPayloads.ReadingAt payload) {
        if (!payload.open()) {
            stopShared(player);
            return;
        }
        SPREADS.put(player.getUUID(), payload);
        share(player, payload);
    }

    private static void stopShared(ServerPlayer player) {
        if (SPREADS.remove(player.getUUID()) == null) return;
        FoliosPayloads.SharedReading closed = new FoliosPayloads.SharedReading(player.getId(), new FoliosPayloads.ReadingAt(false, FoliosPayloads.NO_BOOK, "", 0, false));
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && Network.canPlayerReceive(other, FoliosPayloads.SharedReading.TYPE)) Network.sendToPlayer(other, closed);
        }
    }

    private static void share(ServerPlayer player, FoliosPayloads.ReadingAt payload) {
        FoliosPayloads.SharedReading shared = new FoliosPayloads.SharedReading(player.getId(), payload);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || other.distanceToSqr(player) > READ_ALONG * READ_ALONG) continue;
            if (Network.canPlayerReceive(other, FoliosPayloads.SharedReading.TYPE)) Network.sendToPlayer(other, shared);
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
        net.ixdarklord.folios.internal.quest.QuestTracker.due(player);
        if (!tracked(advancement.getId())) return;
        Changes changes = CHANGED.computeIfAbsent(player.getUUID(), uuid -> new Changes(new LinkedHashSet<>(), new LinkedHashSet<>()));
        (done ? changes.undone : changes.done).remove(advancement.getId());
        (done ? changes.done : changes.undone).add(advancement.getId());
    }

    private static void sendAll(ServerPlayer player) {
        if (!Network.canPlayerReceive(player, FoliosPayloads.Advancements.TYPE)) return;
        List<ResourceLocation> done = new ArrayList<>();
        for (Advancement advancement : player.getServer().getAdvancements().getAllAdvancements()) {
            if (tracked(advancement.getId()) && player.getAdvancements().getOrStartProgress(advancement).isDone()) done.add(advancement.getId());
        }
        Network.sendToPlayer(player, new FoliosPayloads.Advancements(true, done, List.of()));
    }

    private static void flush(MinecraftServer server) {
        // Each second, those reading are told again to everyone near them: to whoever has come near since, and so a
        // client can let go of a book whose reader it no longer hears of.
        if (server.getTickCount() % 20 == 0 && !SPREADS.isEmpty()) {
            for (Map.Entry<UUID, FoliosPayloads.ReadingAt> entry : SPREADS.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) share(player, entry.getValue());
            }
        }
        if (CHANGED.isEmpty()) return;
        for (Map.Entry<UUID, Changes> entry : CHANGED.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !Network.canPlayerReceive(player, FoliosPayloads.Advancements.TYPE)) continue;
            Changes changes = entry.getValue();
            Network.sendToPlayer(player, new FoliosPayloads.Advancements(false, List.copyOf(changes.done), List.copyOf(changes.undone)));
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
                    .orElseGet(() -> new FoliosPayloads.Structure(id, FoliosPayloads.Structure.MISSING, List.of(), List.of())));
        }
    }

    // A structure's blocks as a page shows them: its first palette (a structure with several, as a shipwreck has, keeps
    // its others as variants), less air and the blocks only structures have; a jigsaw as the block it leaves behind.
    private static FoliosPayloads.Structure structure(ResourceLocation id, StructureTemplate template) {
        List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).folios$palettes();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> indices = new HashMap<>();
        List<Integer> blocks = new ArrayList<>();
        if (!palettes.isEmpty()) {
            for (StructureTemplate.StructureBlockInfo block : palettes.get(0).blocks()) {
                BlockState state = block.state();
                if (state.is(Blocks.JIGSAW)) state = jigsawLeaves(block);
                if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK)) continue;
                if (blocks.size() >= FoliosPayloads.MOST_STRUCTURE_BLOCKS * 4) {
                    return new FoliosPayloads.Structure(id, FoliosPayloads.Structure.TOO_LARGE, List.of(), List.of());
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
        return new FoliosPayloads.Structure(id, FoliosPayloads.Structure.FOUND, palette, blocks);
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
