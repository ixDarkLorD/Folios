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

public final class ClientQuests {
    public record Quest(List<FoliosObjective> objectives, boolean inOrder, @Nullable JsonObject rewards) {}

    private static final Map<Identifier, Quest> QUESTS = new ConcurrentHashMap<>();
    private static final Map<Identifier, QuestRewards> REWARDS = new ConcurrentHashMap<>();
    private static @Nullable String told;

    private ClientQuests() {}

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

    public static PlayerQuests.Progress progress(Identifier id) {
        var player = Minecraft.getInstance().player;
        return player == null ? PlayerQuests.Progress.NONE : FoliosRegistry.PLAYER_QUESTS.get(player).progress(id);
    }

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

    public static void claim(Identifier id) {
        Network.sendToServer(new FoliosPayloads.Claim(id));
    }

    public static void tick(Minecraft minecraft) {
        FoliosReader reader = minecraft.gui.screen() instanceof FoliosScreen screen ? screen.reader() : HandheldBook.current();
        var entry = reader != null ? reader.entry() : null;
        Identifier book = entry != null ? reader.book.id() : FoliosPayloads.NO_BOOK;
        String now = entry != null ? book + "|" + entry.id() : null;
        if (Objects.equals(now, told) || minecraft.getConnection() == null) return;
        told = now;
        Network.sendToServer(new FoliosPayloads.Viewing(book, entry != null ? entry.id() : ""));
    }

    public static void forget() {
        QUESTS.clear();
        REWARDS.clear();
        told = null;
    }
}
