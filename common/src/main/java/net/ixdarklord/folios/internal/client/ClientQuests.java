package net.ixdarklord.folios.internal.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.ixdarklord.folios.internal.client.screen.FoliosScreen;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.ixdarklord.folios.internal.quest.PlayerQuests;
import net.ixdarklord.folios.internal.quest.QuestRewards;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's quests on the client: what the server sent of each (books show them as they are written), what it found
 * of the player's progress, and claiming their rewards. Also tells the server which entry is open, for its reading.
 */
public final class ClientQuests {
    /** A server quest as a book shows it. */
    public record Quest(List<FoliosObjective> objectives, boolean inOrder, @Nullable JsonObject rewards) {}

    private static final Map<Identifier, Quest> QUESTS = new ConcurrentHashMap<>();
    private static final Map<Identifier, QuestRewards> REWARDS = new ConcurrentHashMap<>();
    // The entry last said to be open, so it is said again only when it changes.
    private static @Nullable String told;

    private ClientQuests() {}

    /** The quests the server sent: kept, and books laid out again so their quest pages show them. */
    public static void receive(FoliosPayloads.Quests payload) {
        QUESTS.clear();
        REWARDS.clear();
        payload.quests().forEach((id, text) -> {
            try {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                List<FoliosObjective> objectives = new ArrayList<>();
                for (JsonElement one : json.getAsJsonArray("objectives")) objectives.add(FoliosObjective.read(one.getAsJsonObject()));
                boolean inOrder = json.has("in_order") && json.get("in_order").getAsBoolean();
                QUESTS.put(id, new Quest(List.copyOf(objectives), inOrder, json.has("rewards") ? json.getAsJsonObject("rewards") : null));
            } catch (RuntimeException e) {
                Folios.LOGGER.error("Skipping the Folios quest {} the server sent: {}", id, e.getMessage());
            }
        });
        FoliosProgress.changed();
    }

    public static @Nullable Quest get(Identifier id) {
        return QUESTS.get(id);
    }

    /** A quest's rewards, read with the client's world; null where it has none, or before the world is there. */
    public static @Nullable QuestRewards rewards(Identifier id) {
        QuestRewards known = REWARDS.get(id);
        if (known != null) return known;
        Quest quest = QUESTS.get(id);
        var level = Minecraft.getInstance().level;
        if (quest == null || quest.rewards() == null || level == null) return null;
        QuestRewards read = QuestRewards.read(quest.rewards(), level.registryAccess());
        REWARDS.put(id, read);
        return read;
    }

    /** What the server found of the player's progress with a quest. */
    public static PlayerQuests.Progress progress(Identifier id) {
        var player = Minecraft.getInstance().player;
        return player == null ? PlayerQuests.Progress.NONE : FoliosRegistry.PLAYER_QUESTS.get(player).progress(id);
    }

    /** What the server found of the player's quests changed: books are laid out again if any opened or was done. */
    public static void synced(PlayerQuests old, PlayerQuests now) {
        for (Identifier id : QUESTS.keySet()) {
            PlayerQuests.Progress before = old.progress(id);
            PlayerQuests.Progress after = now.progress(id);
            if (before.isOpen() != after.isOpen() || before.done() != after.done()) {
                FoliosProgress.changed();
                return;
            }
        }
    }

    /** Asks the server for a quest's reward. */
    public static void claim(Identifier id) {
        Network.sendToServer(new FoliosPayloads.Claim(id));
    }

    /** Every client tick: tells the server which entry is open, when that has changed. */
    public static void tick(Minecraft minecraft) {
        FoliosReader reader = minecraft.screen instanceof FoliosScreen screen ? screen.reader() : HandheldBook.current();
        var entry = reader != null ? reader.entry() : null;
        Identifier book = entry != null ? reader.book.id() : FoliosPayloads.NO_BOOK;
        String now = entry != null ? book + "|" + entry.id() : null;
        if (Objects.equals(now, told) || minecraft.getConnection() == null) return;
        told = now;
        Network.sendToServer(new FoliosPayloads.Viewing(book, entry != null ? entry.id() : ""));
    }

    /** Forgets what a server sent, as the client leaves it. */
    public static void forget() {
        QUESTS.clear();
        REWARDS.clear();
        told = null;
    }
}
