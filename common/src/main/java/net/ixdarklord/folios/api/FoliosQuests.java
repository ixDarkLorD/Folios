package net.ixdarklord.folios.api;

import net.ixdarklord.folios.internal.quest.QuestTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Quests the server follows: those data packs give ({@code data/<namespace>/folios/quests/<name>.json}), checked by the
 * server alone and rewarded by it. Books show them with a quest element naming one ({@code "quest": "mymod:first_quest"}).
 * <pre>{@code
 * FoliosQuests.onCompleted((player, quest) -> MyMod.LOGGER.info("{} finished {}", player.getName().getString(), quest));
 * FoliosQuests.registerValue(MyMod.id("mana"), player -> Mana.of(player).current());
 * FoliosQuests.setFlag(player, "mymod:met_the_wizard", true);
 * if (FoliosQuests.isDone(player, MyMod.id("first_quest"))) ...
 * }</pre>
 * Server side only: call these on the server thread.
 */
public final class FoliosQuests {
    private static final Map<ResourceLocation, ToDoubleFunction<ServerPlayer>> VALUES = new ConcurrentHashMap<>();
    private static final Map<String, Predicate<ServerPlayer>> FLAGS = new ConcurrentHashMap<>();
    private static final List<Completed> COMPLETED = new CopyOnWriteArrayList<>();

    private FoliosQuests() {}

    /** Told when a player finishes a server quest. */
    @FunctionalInterface
    public interface Completed {
        void completed(ServerPlayer player, ResourceLocation quest);
    }

    /** Listens for players finishing quests. */
    public static void onCompleted(Completed listener) {
        COMPLETED.add(listener);
    }

    /** A value for quests' {@code "source"} objectives to follow ({@code {"source": "mymod:mana", "goal": 100}}). */
    public static void registerValue(ResourceLocation id, ToDoubleFunction<ServerPlayer> value) {
        VALUES.put(id, value);
    }

    /** A flag a mod works out for each player, for quests' {@code "flag"} objectives. */
    public static void registerFlag(String flag, Predicate<ServerPlayer> on) {
        FLAGS.put(flag, on);
    }

    /** Sets or clears one of a player's own flags. Their quests are checked again straight away. */
    public static void setFlag(ServerPlayer player, String flag, boolean on) {
        QuestTracker.setFlag(player, flag, on);
    }

    /** Whether a player has finished a server quest. */
    public static boolean isDone(ServerPlayer player, ResourceLocation quest) {
        return QuestTracker.state(player).progress(quest).done();
    }

    /** Whether a player has claimed a server quest's reward. */
    public static boolean isClaimed(ServerPlayer player, ResourceLocation quest) {
        return QuestTracker.state(player).progress(quest).claimed();
    }

    /** How far along a player is with a server quest, 0 to 1. */
    public static float progress(ServerPlayer player, ResourceLocation quest) {
        return QuestTracker.progress(player, quest);
    }

    /** Finishes a quest for a player, as though they had done all of it. */
    public static void complete(ServerPlayer player, ResourceLocation quest) {
        QuestTracker.complete(player, quest);
    }

    /** Takes back a player's quest, and its claim: they can do it, and claim it, again. */
    public static void reset(ServerPlayer player, ResourceLocation quest) {
        QuestTracker.reset(player, quest);
    }

    /** The server quests there are. */
    public static List<ResourceLocation> quests() {
        return QuestTracker.quests();
    }

    // ---- For the quests themselves ----

    /** @hidden */
    public static float value(ServerPlayer player, ResourceLocation id) {
        ToDoubleFunction<ServerPlayer> value = VALUES.get(id);
        return value == null ? 0 : (float) value.applyAsDouble(player);
    }

    /** @hidden */
    public static boolean flag(ServerPlayer player, String flag) {
        Predicate<ServerPlayer> on = FLAGS.get(flag);
        return on != null && on.test(player);
    }

    /** @hidden */
    public static void completed(ServerPlayer player, ResourceLocation quest) {
        for (Completed listener : COMPLETED) listener.completed(player, quest);
    }

    /** @hidden Whether a value is known on the server, for loading quests that follow it. */
    public static boolean hasValue(@Nullable ResourceLocation id) {
        return id != null && VALUES.containsKey(id);
    }
}
