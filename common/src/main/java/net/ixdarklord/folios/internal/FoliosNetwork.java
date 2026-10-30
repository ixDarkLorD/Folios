package net.ixdarklord.folios.internal;

import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.internal.client.FoliosClientNetwork;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.ixdarklord.folios.internal.mixin.StructureTemplateAccessor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FoliosNetwork {
    private static final Map<UUID, Changes> CHANGED = new HashMap<>();

    private record Changes(Set<Identifier> done, Set<Identifier> undone) {}

    private static final Map<UUID, InteractionHand> READERS = new HashMap<>();

    private static final Map<UUID, FoliosPayloads.ReadingAt> SPREADS = new HashMap<>();
    private static final double READ_ALONG = 16;

    private FoliosNetwork() {}

    public static void init() {
        Network.registerClientbound(FoliosPayloads.Open.TYPE, FoliosPayloads.Open.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.open(payload));
        Network.registerClientbound(FoliosPayloads.Recipe.TYPE, FoliosPayloads.Recipe.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.recipe(payload));
        Network.registerClientbound(FoliosPayloads.SharedReading.TYPE, FoliosPayloads.SharedReading.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.sharedReading(payload));
        Network.registerServerbound(FoliosPayloads.ReadingAt.TYPE, FoliosPayloads.ReadingAt.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) readingAt(player, payload);
        });
        Network.registerClientbound(FoliosPayloads.Advancements.TYPE, FoliosPayloads.Advancements.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.advancements(payload));
        Network.registerServerbound(FoliosPayloads.RecipeRequest.TYPE, FoliosPayloads.RecipeRequest.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) answerRecipes(player, payload.recipes());
        });
        Network.registerClientbound(FoliosPayloads.Structure.TYPE, FoliosPayloads.Structure.STREAM_CODEC, (payload, context) -> FoliosClientNetwork.structure(payload));
        Network.registerServerbound(FoliosPayloads.StructureRequest.TYPE, FoliosPayloads.StructureRequest.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) answerStructures(player, payload.structures());
        });
        PlayerEvents.JOIN.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                sendAll(serverPlayer);
                FoliosSourceData.sendAll(serverPlayer);
            }
        });
        net.ixdarklord.folios.internal.quest.QuestTracker.init();
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
            if (player instanceof ServerPlayer serverPlayer) stopShared(serverPlayer);
        });
        ServerTickEvents.END.register(FoliosNetwork::flush);
    }

    public static void open(ServerPlayer player, Identifier book, @Nullable String entry) {
        net.ixdarklord.folios.internal.quest.QuestReading.opened(player, book);
        if (Network.canPlayerReceive(player, FoliosPayloads.Open.TYPE)) Network.sendToPlayer(player, new FoliosPayloads.Open(book, Optional.ofNullable(entry)));
    }

    private static void reading(ServerPlayer player, FoliosPayloads.Reading payload) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.has(FoliosRegistry.READING.get())) stack.remove(FoliosRegistry.READING.get());
        }
        READERS.remove(player.getUUID());
        if (!payload.reading()) stopShared(player);
        if (!payload.reading() || payload.hand() < 0 || payload.hand() >= InteractionHand.values().length) return;
        InteractionHand hand = InteractionHand.values()[payload.hand()];
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof FoliosBookItem) && FoliosBooks.bookOf(stack) == null) return;
        stack.set(FoliosRegistry.READING.get(), Unit.INSTANCE);
        READERS.put(player.getUUID(), hand);
    }

    private static final int MAX_BOOKS = 256;

    private static void progress(ServerPlayer player, FoliosPayloads.Progress payload) {
        FoliosRecord record = payload.record().capped();
        if (payload.hand() < 0) {
            FoliosRegistry.PLAYER_PROGRESS.update(player, all -> {
                if (!all.containsKey(payload.book()) && all.size() >= MAX_BOOKS) return all;
                Map<Identifier, FoliosRecord> next = new HashMap<>(all);
                next.put(payload.book(), record);
                return Map.copyOf(next);
            });
            return;
        }
        if (payload.hand() >= InteractionHand.values().length) return;
        ItemStack stack = player.getItemInHand(InteractionHand.values()[payload.hand()]);
        if (payload.book().equals(bookOf(stack))) FoliosRegistry.BOOK_PROGRESS.set(stack, record.forStack());
    }

    public static @Nullable Identifier bookOf(ItemStack stack) {
        return stack.getItem() instanceof FoliosBookItem item ? item.book(stack) : FoliosBooks.bookOf(stack);
    }

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
        for (ServerPlayer other : player.level().players()) {
            if (other != player && Network.canPlayerReceive(other, FoliosPayloads.SharedReading.TYPE)) Network.sendToPlayer(other, closed);
        }
    }

    private static void share(ServerPlayer player, FoliosPayloads.ReadingAt payload) {
        FoliosPayloads.SharedReading shared = new FoliosPayloads.SharedReading(player.getId(), payload);
        for (ServerPlayer other : player.level().players()) {
            if (other == player || other.distanceToSqr(player) > READ_ALONG * READ_ALONG) continue;
            if (Network.canPlayerReceive(other, FoliosPayloads.SharedReading.TYPE)) Network.sendToPlayer(other, shared);
        }
    }

    public static boolean isRead(Entity holder, ItemStack stack) {
        InteractionHand hand = READERS.get(holder.getUUID());
        return hand != null && holder instanceof ServerPlayer player && player.getItemInHand(hand) == stack;
    }

    private static boolean tracked(Identifier advancement) {
        return !advancement.getPath().startsWith("recipes/");
    }

    public static void changed(ServerPlayer player, AdvancementHolder advancement, boolean done) {
        net.ixdarklord.folios.internal.quest.QuestTracker.due(player);
        if (!tracked(advancement.id())) return;
        Changes changes = CHANGED.computeIfAbsent(player.getUUID(), uuid -> new Changes(new LinkedHashSet<>(), new LinkedHashSet<>()));
        (done ? changes.undone : changes.done).remove(advancement.id());
        (done ? changes.done : changes.undone).add(advancement.id());
    }

    private static void sendAll(ServerPlayer player) {
        if (!Network.canPlayerReceive(player, FoliosPayloads.Advancements.TYPE)) return;
        List<Identifier> done = new ArrayList<>();
        for (AdvancementHolder holder : player.level().getServer().getAdvancements().getAllAdvancements()) {
            if (tracked(holder.id()) && player.getAdvancements().getOrStartProgress(holder).isDone()) done.add(holder.id());
        }
        Network.sendToPlayer(player, new FoliosPayloads.Advancements(true, done, List.of()));
    }

    private static void flush(MinecraftServer server) {
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

    private static void answerRecipes(ServerPlayer player, List<Identifier> ids) {
        var recipes = player.level().getServer().getRecipeManager();
        for (Identifier id : ids) {
            List<RecipeDisplay> displays = recipes.byKey(ResourceKey.create(Registries.RECIPE, id))
                    .map(holder -> {
                        Recipe<?> recipe = holder.value();
                        return recipe.display();
                    })
                    .orElse(List.of());
            Network.sendToPlayer(player, new FoliosPayloads.Recipe(id, displays.size() > 16 ? displays.subList(0, 16) : displays));
        }
    }

    private static void answerStructures(ServerPlayer player, List<Identifier> ids) {
        var structures = player.level().getServer().getStructureTemplateManager();
        for (Identifier id : ids) {
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

    private static FoliosPayloads.Structure structure(Identifier id, StructureTemplate template) {
        List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).folios$palettes();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> indices = new HashMap<>();
        List<Integer> blocks = new ArrayList<>();
        if (!palettes.isEmpty()) {
            for (StructureTemplate.StructureBlockInfo block : palettes.getFirst().blocks()) {
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
        String finalState = jigsaw.nbt() != null ? jigsaw.nbt().getStringOr("final_state", "minecraft:air") : "minecraft:air";
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, finalState, false).blockState();
        } catch (Exception e) {
            return Blocks.AIR.defaultBlockState();
        }
    }
}
