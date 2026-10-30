package net.ixdarklord.folios.internal.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record ServerQuest(Identifier id, @Nullable Identifier book, List<ServerObjective> requires, List<ServerObjective> objectives,
                          boolean inOrder, @Nullable JsonObject rewards, JsonObject json) {
    public static final int MOST = 32;

    public static ServerQuest read(Identifier id, JsonObject json) {
        Identifier book = json.has("book") ? Identifier.parse(json.get("book").getAsString()) : null;
        List<ServerObjective> requires = json.has("requires") ? conditions(json.get("requires"), book) : List.of();
        List<ServerObjective> objectives = conditions(json.get("objectives"), book);
        if (objectives.isEmpty() || objectives.size() > MOST) throw new IllegalArgumentException("A quest has 1 to " + MOST + " objectives");
        boolean inOrder = json.has("in_order") && json.get("in_order").getAsBoolean();
        JsonObject rewards = json.has("rewards") ? json.getAsJsonObject("rewards") : null;
        return new ServerQuest(id, book, requires, objectives, inOrder, rewards, json);
    }

    private static List<ServerObjective> conditions(JsonElement json, @Nullable Identifier book) {
        List<ServerObjective> read = new ArrayList<>();
        if (json.isJsonObject()) read.add(ServerObjective.read(json.getAsJsonObject(), book));
        else for (JsonElement one : json.getAsJsonArray()) read.add(ServerObjective.read(one.getAsJsonObject(), book));
        return List.copyOf(read);
    }

    boolean unlocked(QuestContext context) {
        for (ServerObjective condition : requires) if (condition.progress(context) < 1) return false;
        return true;
    }

    List<Float> progress(QuestContext context) {
        List<Float> each = new ArrayList<>(objectives.size());
        boolean blocked = false;
        for (ServerObjective objective : objectives) {
            float progress = blocked ? 0 : objective.progress(context);
            each.add(progress);
            if (inOrder && progress < 1) blocked = true;
        }
        return each;
    }

    boolean readsEntries() {
        for (ServerObjective objective : objectives) if (objective.readsEntries()) return true;
        for (ServerObjective condition : requires) if (condition.readsEntries()) return true;
        return false;
    }

    void obtained(Set<ItemMatch> into) {
        for (ServerObjective objective : objectives) objective.obtained(into);
        for (ServerObjective condition : requires) condition.obtained(into);
    }
}
