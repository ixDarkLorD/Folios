package net.ixdarklord.folios.internal.quest;

import com.google.gson.JsonElement;
import net.ixdarklord.coolcatcore.api.event.v2.common.PlayerEvents;
import net.ixdarklord.coolcatcore.api.event.v2.common.ServerTickEvents;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.api.registry.ReloadListeners;
import net.ixdarklord.folios.api.FoliosQuests;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.ClientQuests;
import net.ixdarklord.folios.internal.config.FoliosServerConfig;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The server's quests: loaded from data packs, followed for each player and rewarded. Each player's quests are checked
 * about once a second (players spread over the second's ticks), and straight away after what could finish one (an
 * advancement, a flag, an entry read); what is found is kept with the player and sent to them only when it changes.
 */
public final class QuestTracker extends SimpleJsonResourceReloadListener {
    private static final QuestTracker INSTANCE = new QuestTracker();
    /** Ticks between one check of a player's quests and the next. */
    private static final int EVERY = 20;

    private static Map<ResourceLocation, ServerQuest> quests = Map.of();
    // Every item some quest's "obtained" asks for: the only items the server notes a player having had.
    private static Set<ItemMatch> watched = Set.of();
    private static volatile boolean reloaded;
    // Players to check at the end of this tick, whatever their turn.
    private static final Set<UUID> DUE = new HashSet<>();

    private QuestTracker() {
        super(new com.google.gson.Gson(), "folios/quests");
    }

    public static void init() {
        ReloadListeners.registerServer(Folios.rl("quests"), INSTANCE);
        ServerTickEvents.END.register(QuestTracker::tick);
        PlayerEvents.JOIN.register(player -> {
            send(player);
            due(player);
        });
        PlayerEvents.LEAVE.register(player -> {
            DUE.remove(player.getUUID());
            QuestReading.leave(player.getUUID());
        });
        Network.registerClientbound(FoliosPayloads.Quests.TYPE, FoliosPayloads.Quests.STREAM_CODEC, (payload, context) -> ClientQuests.receive(payload));
        Network.registerServerbound(FoliosPayloads.Claim.TYPE, FoliosPayloads.Claim.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) claim(player, payload.quest());
        });
        Network.registerServerbound(FoliosPayloads.Viewing.TYPE, FoliosPayloads.Viewing.STREAM_CODEC, (payload, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) QuestReading.viewing(player, payload.book(), payload.entry());
        });
        QuestCommands.init();
        FoliosServerConfig.init();
    }

    // ---- Loading ----

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ServerQuest> read = new LinkedHashMap<>();
        Set<ItemMatch> items = new LinkedHashSet<>();
        new TreeMap<>(files).forEach((id, json) -> {
            try {
                ServerQuest quest = ServerQuest.read(id, json.getAsJsonObject());
                if (FoliosServerConfig.STRICT.get() && quest.rewards() != null && quest.readsEntries()) {
                    Folios.LOGGER.warn("Skipping the Folios quest {}: it is rewarded and asks for reading, which the strict setting doesn't allow", id);
                    return;
                }
                read.put(id, quest);
                quest.obtained(items);
            } catch (RuntimeException e) {
                Folios.LOGGER.error("Skipping the Folios quest {}: {}", id, e.getMessage());
            }
        });
        quests = Map.copyOf(read);
        watched = Set.copyOf(items);
        reloaded = true;
    }

    public static List<ResourceLocation> quests() {
        return List.copyOf(quests.keySet());
    }

    // ---- Sending ----

    private static void send(ServerPlayer player) {
        if (!Network.canPlayerReceive(player, FoliosPayloads.Quests.TYPE)) return;
        Map<ResourceLocation, String> written = new HashMap<>();
        quests.forEach((id, quest) -> written.put(id, quest.json().toString()));
        Network.sendToPlayer(player, new FoliosPayloads.Quests(written));
    }

    // ---- Checking ----

    /** Has a player's quests checked at the end of this tick. */
    public static void due(ServerPlayer player) {
        DUE.add(player.getUUID());
    }

    private static void tick(MinecraftServer server) {
        boolean all = reloaded;
        reloaded = false;
        int turn = server.getTickCount() % EVERY;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (all) send(player);
            QuestReading.tick(player);
            boolean now = all || DUE.remove(player.getUUID()) || Math.floorMod(player.getUUID().hashCode(), EVERY) == turn;
            if (now) check(player);
        }
    }

    public static PlayerQuests state(ServerPlayer player) {
        return FoliosRegistry.PLAYER_QUESTS.get(player);
    }

    private static void check(ServerPlayer player) {
        PlayerQuests before = state(player);
        PlayerQuests state = noteObtained(player, before);
        QuestContext context = new QuestContext(player, state);
        List<ResourceLocation> finished = new ArrayList<>();
        for (ServerQuest quest : quests.values()) {
            PlayerQuests.Progress was = state.progress(quest.id());
            if (was.done()) continue;
            // Locked, nothing counts yet; once open, it stays open.
            boolean unlocked = was.unlocked() || quest.unlocked(context);
            List<Float> each = unlocked ? quest.progress(context) : List.of();
            boolean done = unlocked && each.stream().allMatch(value -> value >= 1);
            PlayerQuests.Progress now = new PlayerQuests.Progress(each, unlocked, done, was.claimed());
            if (now.equals(was)) continue;
            state = state.with(quest.id(), now);
            context.state = state;
            if (done) finished.add(quest.id());
        }
        if (!state.equals(before)) FoliosRegistry.PLAYER_QUESTS.set(player, state);
        for (ResourceLocation quest : finished) FoliosQuests.completed(player, quest);
    }

    // The watched items in the player's inventory now that they haven't had before.
    private static PlayerQuests noteObtained(ServerPlayer player, PlayerQuests state) {
        if (watched.isEmpty()) return state;
        Set<ResourceLocation> found = new LinkedHashSet<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            for (ItemMatch match : watched) {
                if (match.matches(stack)) {
                    found.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    break;
                }
            }
        }
        return found.isEmpty() ? state : state.withObtained(found);
    }

    /** How far along a player is with a quest, 0 to 1: its objectives' average, or 1 once done. */
    public static float progress(ServerPlayer player, ResourceLocation quest) {
        PlayerQuests.Progress progress = state(player).progress(quest);
        if (progress.done()) return 1;
        if (progress.objectives().isEmpty()) return 0;
        float sum = 0;
        for (float value : progress.objectives()) sum += value;
        return sum / progress.objectives().size();
    }

    // ---- Changes ----

    public static void setFlag(ServerPlayer player, String flag, boolean on) {
        PlayerQuests state = state(player);
        PlayerQuests next = state.withFlag(flag, on);
        if (next == state) return;
        FoliosRegistry.PLAYER_QUESTS.set(player, next);
        due(player);
    }

    /** Notes an entry the server counted read, and checks the player's quests. */
    static void read(ServerPlayer player, ResourceLocation book, String entry) {
        PlayerQuests state = state(player);
        PlayerQuests next = state.withRead(book, entry);
        if (next == state) return;
        FoliosRegistry.PLAYER_QUESTS.set(player, next);
        due(player);
    }

    public static void complete(ServerPlayer player, ResourceLocation quest) {
        ServerQuest known = quests.get(quest);
        if (known == null) return;
        PlayerQuests.Progress was = state(player).progress(quest);
        if (was.done()) return;
        List<Float> full = new ArrayList<>();
        for (int index = 0; index < known.objectives().size(); index++) full.add(1F);
        FoliosRegistry.PLAYER_QUESTS.set(player, state(player).with(quest, new PlayerQuests.Progress(full, true, true, was.claimed())));
        FoliosQuests.completed(player, quest);
        due(player);
    }

    public static void reset(ServerPlayer player, ResourceLocation quest) {
        FoliosRegistry.PLAYER_QUESTS.set(player, state(player).with(quest, PlayerQuests.Progress.NONE));
        due(player);
    }

    /**
     * A player claiming a quest's reward: given once, if the quest is one the server has found them finishing. The
     * claim is noted before the items are given, so a claim sent twice gives them once.
     */
    static void claim(ServerPlayer player, ResourceLocation id) {
        ServerQuest quest = quests.get(id);
        PlayerQuests.Progress progress = state(player).progress(id);
        if (quest == null || quest.rewards() == null || !progress.done() || progress.claimed()) {
            if (quest != null && !progress.done()) Folios.LOGGER.warn("{} claimed the Folios quest {} before finishing it", player.getName().getString(), id);
            return;
        }
        FoliosRegistry.PLAYER_QUESTS.set(player, state(player).with(id, new PlayerQuests.Progress(progress.objectives(), true, true, true)));
        QuestRewards.read(quest.rewards(), player.level().registryAccess()).give(player);
    }
}
