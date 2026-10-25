package net.ixdarklord.folios.internal.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An objective of a server quest, read from the same JSON a book's quest takes and checked by the server alone. Those
 * the server can't check (how much of a category or a book is read: it doesn't have the books) are refused as read.
 */
public final class ServerObjective {
    enum Kind { ADVANCEMENT, READ, QUEST, FLAG, CARRIED, OBTAINED, SOURCE, ANY }

    private final Kind kind;
    private final String target;
    private final int goal;
    private final @Nullable ItemMatch items;
    private final List<ServerObjective> any;
    // For a "read": the book it is read in.
    private final @Nullable ResourceLocation book;

    private ServerObjective(Kind kind, String target, int goal, @Nullable ItemMatch items,
                            List<ServerObjective> any, @Nullable ResourceLocation book) {
        this.kind = kind;
        this.target = target;
        this.goal = Math.max(1, goal);
        this.items = items;
        this.any = List.copyOf(any);
        this.book = book;
    }

    private static ServerObjective named(Kind kind, String target) {
        return new ServerObjective(kind, target, 1, null, List.of(), null);
    }

    private static ServerObjective ofItems(Kind kind, JsonElement items, int goal) {
        return new ServerObjective(kind, "", goal, ItemMatch.read(items), List.of(), null);
    }

    // ---- Reading ----

    /** An objective as JSON writes it; {@code book} is the quest's, for its {@code "read"} objectives. */
    public static ServerObjective read(JsonObject json, @Nullable ResourceLocation book) {
        int goal = json.has("goal") ? json.get("goal").getAsInt() : 1;
        if (json.has("advancement")) return named(Kind.ADVANCEMENT, id(json, "advancement"));
        if (json.has("read")) return readEntry(json.get("read").getAsString(), book);
        if (json.has("quest")) return named(Kind.QUEST, id(json, "quest"));
        if (json.has("flag")) return named(Kind.FLAG, json.get("flag").getAsString());
        if (json.has("carried")) return ofItems(Kind.CARRIED, json.get("carried"), goal);
        if (json.has("obtained")) return ofItems(Kind.OBTAINED, json.get("obtained"), 1);
        if (json.has("source")) return new ServerObjective(Kind.SOURCE, id(json, "source"), goal, null, List.of(), null);
        if (json.has("any")) return readAny(json, book);
        if (json.has("read_category") || json.has("read_book")) {
            throw new IllegalArgumentException("The server can't follow \"read_category\" or \"read_book\" "
                    + "(it doesn't have the books); name the entries with \"read\"");
        }
        throw new IllegalArgumentException("A condition names one of advancement, read, quest, flag, carried, "
                + "obtained, source or any: " + json);
    }

    // "basics/first_steps" in the quest's book, or "mymod:handbook|basics/first_steps" in its own.
    private static ServerObjective readEntry(String written, @Nullable ResourceLocation book) {
        int bar = written.indexOf('|');
        ResourceLocation in = bar > 0 ? new ResourceLocation(written.substring(0, bar)) : book;
        if (in == null) {
            throw new IllegalArgumentException("\"read\" needs the quest's \"book\", "
                    + "or the book written before the entry (\"mymod:book|entry\")");
        }
        String entry = bar > 0 ? written.substring(bar + 1) : written;
        return new ServerObjective(Kind.READ, entry, 1, null, List.of(), in);
    }

    private static ServerObjective readAny(JsonObject json, @Nullable ResourceLocation book) {
        List<ServerObjective> any = new ArrayList<>();
        for (JsonElement one : json.getAsJsonArray("any")) any.add(read(one.getAsJsonObject(), book));
        if (any.isEmpty()) throw new IllegalArgumentException("\"any\" lists at least one condition");
        return new ServerObjective(Kind.ANY, "", 1, null, any, null);
    }

    private static String id(JsonObject json, String key) {
        return new ResourceLocation(json.get(key).getAsString()).toString();
    }

    // ---- Checking ----

    /** How far along it is for a player, 0 to 1. */
    float progress(QuestContext context) {
        return switch (kind) {
            case ADVANCEMENT -> done(context.advancement(new ResourceLocation(target)));
            case READ -> done(context.read(book, target));
            case QUEST -> done(context.questDone(new ResourceLocation(target)));
            case FLAG -> done(context.flag(target));
            case CARRIED -> share(context.carried(items));
            case OBTAINED -> done(context.obtained(items));
            case SOURCE -> share(context.value(new ResourceLocation(target)));
            case ANY -> this.best(context);
        };
    }

    private static float done(boolean done) {
        return done ? 1 : 0;
    }

    private float share(float value) {
        return Mth.clamp(value / goal, 0, 1);
    }

    private float best(QuestContext context) {
        float best = 0;
        for (ServerObjective one : any) best = Math.max(best, one.progress(context));
        return best;
    }

    /** Whether it asks for reading anywhere in it: the one thing the server takes the client's word for. */
    boolean readsEntries() {
        if (kind == Kind.READ) return true;
        for (ServerObjective one : any) if (one.readsEntries()) return true;
        return false;
    }

    /** The items its "obtained" conditions ask for, so the server watches for them. */
    void obtained(Set<ItemMatch> into) {
        if (kind == Kind.OBTAINED && items != null) into.add(items);
        for (ServerObjective one : any) one.obtained(into);
    }
}
