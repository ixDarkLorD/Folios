package net.ixdarklord.folios.internal.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A quest a data pack gives ({@code data/<namespace>/folios/quests/<name>.json}): its objectives, followed by the server,
 * and what it gives once done. A book shows it with a quest element naming it ({@code "quest": "mymod:first_quest"}).
 * <pre>{@code
 * {
 *   "book": "mymod:handbook",
 *   "requires": {"quest": "mymod:first_steps"},
 *   "objectives": [{"read": "basics/first_steps"}, {"obtained": "minecraft:diamond"}],
 *   "in_order": false,
 *   "rewards": {"items": ["minecraft:emerald*4"], "random": ["minecraft:gold_ingot*8", "minecraft:iron_ingot*16"]}
 * }
 * }</pre>
 *
 * @param json the file as written, sent to players for their books to show it
 */
public record ServerQuest(ResourceLocation id, @Nullable ResourceLocation book, List<ServerObjective> requires, List<ServerObjective> objectives,
                          boolean inOrder, @Nullable JsonObject rewards, JsonObject json) {
    /** The most objectives a quest has. */
    public static final int MOST = 32;

    public static ServerQuest read(ResourceLocation id, JsonObject json) {
        ResourceLocation book = json.has("book") ? new ResourceLocation(json.get("book").getAsString()) : null;
        // What opens it: until then its objectives don't count.
        List<ServerObjective> requires = json.has("requires") ? conditions(json.get("requires"), book) : List.of();
        List<ServerObjective> objectives = conditions(json.get("objectives"), book);
        if (objectives.isEmpty() || objectives.size() > MOST) throw new IllegalArgumentException("A quest has 1 to " + MOST + " objectives");
        boolean inOrder = json.has("in_order") && json.get("in_order").getAsBoolean();
        JsonObject rewards = json.has("rewards") ? json.getAsJsonObject("rewards") : null;
        return new ServerQuest(id, book, requires, objectives, inOrder, rewards, json);
    }

    // A condition, or a list of them.
    private static List<ServerObjective> conditions(JsonElement json, @Nullable ResourceLocation book) {
        List<ServerObjective> read = new ArrayList<>();
        if (json.isJsonObject()) read.add(ServerObjective.read(json.getAsJsonObject(), book));
        else for (JsonElement one : json.getAsJsonArray()) read.add(ServerObjective.read(one.getAsJsonObject(), book));
        return List.copyOf(read);
    }

    /** Whether a player has met what opens it. */
    boolean unlocked(QuestContext context) {
        for (ServerObjective condition : requires) if (condition.progress(context) < 1) return false;
        return true;
    }

    /** Each objective's progress for a player; in an "in_order" quest, nothing past one not yet done. */
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
