package net.ixdarklord.folios.api.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.FoliosMemory;
import net.ixdarklord.folios.internal.client.FoliosProgress;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A condition the player makes progress on: an objective of a quest page, shown with a bar, and what a lock
 * ({@code "requires"}, {@link FoliosLock}) waits for. In JSON it is an object naming what it follows, with an optional
 * {@code "label"} (text with marks, or a translation key) in place of the one it makes for itself:
 * <ul>
 *     <li>{@code {"advancement": "minecraft:story/mine_stone"}}: done once the advancement is;</li>
 *     <li>{@code {"read": "basics/first_steps"}}: an entry of the book read;</li>
 *     <li>{@code {"read_category": "basics"}}: how much of a category has been read;</li>
 *     <li>{@code {"read_book": true}}: how much of the whole book has been read;</li>
 *     <li>{@code {"quest": "basics/first_quest"}}: a quest of the book (an entry with quest pages) done; or, written
 *     with its namespace ({@code "mymod:first_quest"}), one of the server's (data packs' {@code folios/quests});</li>
 *     <li>{@code {"flag": "mymod:met_the_wizard"}}: a flag set in code ({@link FoliosGuides#setFlag}; a leading {@code !}
 *     reverses it);</li>
 *     <li>{@code {"carried": "minecraft:diamond", "goal": 5}}: items carried now (an item, a {@code #tag} or a list);</li>
 *     <li>{@code {"obtained": "minecraft:diamond"}}: done once any of these items has been in the inventory, whatever
 *     became of it after;</li>
 *     <li>{@code {"source": "mymod:mana", "goal": 100}}: a value a mod reports ({@link FoliosObjectives#register}),
 *     done when it reaches the goal;</li>
 *     <li>{@code {"any": [{...}, {...}]}}: done once any one of these is.</li>
 * </ul>
 * {@code "hidden": true} shows it as {@code ???} until it is done.
 */
public final class FoliosObjective {
    public static final Codec<FoliosObjective> CODEC = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!json.isJsonObject()) return DataResult.error(() -> "An objective is an object");
        try {
            return DataResult.success(read(json.getAsJsonObject()));
        } catch (RuntimeException e) {
            return DataResult.error(e::getMessage);
        }
    }, objective -> new Dynamic<>(JsonOps.INSTANCE, objective.json));

    private enum Kind { ADVANCEMENT, READ, READ_CATEGORY, READ_BOOK, QUEST, FLAG, CARRIED, OBTAINED, SOURCE, ANY }

    /** How deep conditions may ask about conditions (a quest's, a category's entries' locks...) before a loop is assumed. */
    private static final int DEPTH = 8;
    private static int depth;

    private final JsonObject json;
    private final Kind kind;
    private final String target;
    private final int goal;
    private final @Nullable FoliosStack items;
    private final @Nullable String label;
    private final boolean hidden;
    private final List<FoliosObjective> any;
    // Those that must be done before it counts at all (a quest's earlier objectives, "in_order").
    private final List<FoliosObjective> after;

    private FoliosObjective(JsonObject json, Kind kind, String target, int goal, @Nullable FoliosStack items, @Nullable String label,
                            boolean hidden, List<FoliosObjective> any, List<FoliosObjective> after) {
        this.json = json;
        this.kind = kind;
        this.target = target;
        this.goal = Math.max(1, goal);
        this.items = items;
        this.label = label;
        this.hidden = hidden;
        this.any = List.copyOf(any);
        this.after = List.copyOf(after);
    }

    /** A condition as JSON writes it. */
    public static FoliosObjective read(JsonObject json) {
        String label = json.has("label") ? FoliosText.read(json.get("label")) : null;
        boolean hidden = json.has("hidden") && json.get("hidden").getAsBoolean();
        int goal = json.has("goal") ? json.get("goal").getAsInt() : 1;
        if (json.has("advancement")) return of(json, Kind.ADVANCEMENT, json.get("advancement").getAsString(), 1, null, label, hidden);
        if (json.has("read")) return of(json, Kind.READ, json.get("read").getAsString(), 1, null, label, hidden);
        if (json.has("read_category")) return of(json, Kind.READ_CATEGORY, json.get("read_category").getAsString(), 1, null, label, hidden);
        if (json.has("read_book")) return of(json, Kind.READ_BOOK, "", 1, null, label, hidden);
        if (json.has("quest")) return of(json, Kind.QUEST, json.get("quest").getAsString(), 1, null, label, hidden);
        if (json.has("flag")) return of(json, Kind.FLAG, json.get("flag").getAsString(), 1, null, label, hidden);
        if (json.has("carried")) return of(json, Kind.CARRIED, "", goal, stack(json.get("carried")), label, hidden);
        if (json.has("obtained")) return of(json, Kind.OBTAINED, "", 1, stack(json.get("obtained")), label, hidden);
        if (json.has("source")) return of(json, Kind.SOURCE, json.get("source").getAsString(), goal, null, label, hidden);
        if (json.has("any")) {
            List<FoliosObjective> any = new ArrayList<>();
            for (JsonElement one : json.getAsJsonArray("any")) any.add(read(one.getAsJsonObject()));
            if (any.isEmpty()) throw new IllegalArgumentException("\"any\" lists at least one condition");
            return new FoliosObjective(json, Kind.ANY, "", 1, null, label, hidden, any, List.of());
        }
        throw new IllegalArgumentException("A condition names one of advancement, read, read_category, read_book, quest, flag, carried, obtained, source or any: " + json);
    }

    /** One condition, or a list of them all of which must hold. */
    public static List<FoliosObjective> readAll(JsonElement json) {
        if (json.isJsonObject()) return List.of(read(json.getAsJsonObject()));
        List<FoliosObjective> all = new ArrayList<>();
        for (JsonElement one : json.getAsJsonArray()) all.add(read(one.getAsJsonObject()));
        return List.copyOf(all);
    }

    private static FoliosObjective of(JsonObject json, Kind kind, String target, int goal, @Nullable FoliosStack items, @Nullable String label, boolean hidden) {
        return new FoliosObjective(json, kind, target, goal, items, label, hidden, List.of(), List.of());
    }

    private static FoliosStack stack(JsonElement json) {
        return FoliosStack.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    /** These objectives, each counting only once all before it are done (a quest's {@code "in_order"}). */
    public static List<FoliosObjective> inOrder(List<FoliosObjective> objectives) {
        List<FoliosObjective> gated = new ArrayList<>();
        for (int index = 0; index < objectives.size(); index++) {
            FoliosObjective objective = objectives.get(index);
            gated.add(new FoliosObjective(objective.json, objective.kind, objective.target, objective.goal, objective.items, objective.label,
                    objective.hidden, objective.any, objectives.subList(0, index)));
        }
        return List.copyOf(gated);
    }

    /** How far along it is, 0 to 1. */
    public float progress(FoliosBook book) {
        if (depth >= DEPTH) return 0;
        depth++;
        try {
            for (FoliosObjective before : after) if (!before.isDone(book)) return 0;
            return this.own(book);
        } finally {
            depth--;
        }
    }

    private float own(FoliosBook book) {
        return switch (kind) {
            case ADVANCEMENT -> FoliosProgress.isDone(Identifier.parse(target)) ? 1 : 0;
            case READ -> FoliosMemory.of(book.id()).isRead(target) ? 1 : 0;
            case READ_CATEGORY -> read(book, target);
            case READ_BOOK -> read(book, null);
            case QUEST -> target.indexOf(':') >= 0 ? serverQuest(Identifier.parse(target)) : this.bookQuest(book);
            case FLAG -> FoliosProgress.flag(target) ? 1 : 0;
            case CARRIED -> Mth.clamp(this.carried() / (float) goal, 0, 1);
            case OBTAINED -> items != null && net.ixdarklord.folios.internal.client.ObtainedItems.has(items) ? 1 : 0;
            case SOURCE -> {
                FoliosObjectives.Source source = FoliosObjectives.get(Identifier.parse(target));
                LocalPlayer player = Minecraft.getInstance().player;
                yield source == null || player == null ? 0 : Mth.clamp(source.value(player) / goal, 0, 1);
            }
            case ANY -> {
                float best = 0;
                for (FoliosObjective one : any) best = Math.max(best, one.progress(book));
                yield best;
            }
        };
    }

    // A quest of the book: an entry with quest pages.
    private float bookQuest(FoliosBook book) {
        FoliosEntry entry = book.entry(target);
        return entry == null || !entry.isQuest() ? 0 : progress(book, entry.objectives());
    }

    // A quest of the server's ("mymod:first_quest"), as far along as the server found the player.
    private static float serverQuest(Identifier id) {
        var found = net.ixdarklord.folios.internal.client.ClientQuests.progress(id);
        if (found.done()) return 1;
        float sum = 0;
        for (float value : found.objectives()) sum += value;
        return found.objectives().isEmpty() ? 0 : sum / found.objectives().size() * 0.99F;
    }

    public boolean isDone(FoliosBook book) {
        return this.progress(book) >= 1;
    }

    /** Whether it waits on others before it counts (an objective of an {@code "in_order"} quest after one not done). */
    public boolean isWaiting(FoliosBook book) {
        for (FoliosObjective before : after) if (!before.isDone(book)) return true;
        return false;
    }

    /** Whether it is done or not done, with nothing between: no bar of its own is shown for it. */
    public boolean isAllOrNothing() {
        return kind != Kind.READ_CATEGORY && kind != Kind.READ_BOOK && kind != Kind.QUEST && kind != Kind.SOURCE && kind != Kind.ANY
                && !(kind == Kind.CARRIED && goal > 1);
    }

    /** What it says, with how far along it is where that is a count; {@code ???} while it is hidden and not done. */
    public Component label(FoliosBook book) {
        if (hidden && !this.isDone(book)) return Component.literal("???");
        Component text = label != null ? FoliosText.inline(label) : this.defaultLabel(book);
        return switch (kind) {
            case CARRIED -> goal > 1 ? text.copy().append(Component.literal(" " + Math.min(goal, this.carried()) + "/" + goal)) : text;
            case SOURCE -> {
                FoliosObjectives.Source source = FoliosObjectives.get(Identifier.parse(target));
                LocalPlayer player = Minecraft.getInstance().player;
                int value = source == null || player == null ? 0 : (int) Math.min(goal, source.value(player));
                yield text.copy().append(Component.literal(" " + value + "/" + goal));
            }
            default -> text;
        };
    }

    private Component defaultLabel(FoliosBook book) {
        return switch (kind) {
            case ADVANCEMENT -> advancementTitle(Identifier.parse(target));
            case READ -> {
                FoliosEntry entry = book.entry(target);
                yield Component.translatableWithFallback("folios.objective.read", "Read %s", entry != null ? entry.title() : Component.literal(target));
            }
            case READ_CATEGORY -> {
                FoliosCategory category = book.category(target);
                yield Component.translatableWithFallback("folios.objective.category", "Read all of %s",
                        category != null ? category.title() : Component.literal(target));
            }
            case READ_BOOK -> Component.translatableWithFallback("folios.objective.book", "Read the whole book");
            case QUEST -> {
                FoliosEntry entry = book.entry(target);
                yield Component.translatableWithFallback("folios.objective.quest", "Finish %s", entry != null ? entry.title() : Component.literal(target));
            }
            case FLAG -> Component.literal(target);
            case CARRIED -> {
                ItemStack first = items != null ? items.first() : ItemStack.EMPTY;
                yield Component.translatableWithFallback("folios.objective.item", "Collect %s", first.getHoverName());
            }
            case OBTAINED -> {
                ItemStack first = items != null ? items.first() : ItemStack.EMPTY;
                yield Component.translatableWithFallback("folios.objective.obtained", "Obtain %s", first.getHoverName());
            }
            case SOURCE -> Component.literal(target);
            case ANY -> {
                net.minecraft.network.chat.MutableComponent joined = Component.empty();
                for (int index = 0; index < any.size(); index++) {
                    if (index > 0) joined.append(Component.translatableWithFallback("folios.objective.or", " or "));
                    joined.append(any.get(index).label(book));
                }
                yield joined;
            }
        };
    }

    // An advancement's own title when the client was shown it, otherwise its name.
    private static Component advancementTitle(Identifier id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            AdvancementNode node = minecraft.getConnection().getAdvancements().getTree().get(id);
            if (node != null) {
                DisplayInfo display = node.holder().value().display().orElse(null);
                if (display != null) return display.getTitle();
            }
        }
        String path = id.getPath();
        return Component.literal(path.substring(path.lastIndexOf('/') + 1).replace('_', ' '));
    }

    /** The items it counts, for an icon; null for objectives that aren't about items. */
    public @Nullable FoliosStack items() {
        return items;
    }

    /** The items {@code "obtained"} conditions ask for, this one's and any it holds ({@code "any"}). */
    public List<FoliosStack> obtained() {
        List<FoliosStack> found = new ArrayList<>();
        if (kind == Kind.OBTAINED && items != null) found.add(items);
        for (FoliosObjective one : any) found.addAll(one.obtained());
        return found;
    }

    private int carried() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || items == null) return 0;
        Set<Item> wanted = new HashSet<>();
        for (ItemStack stack : items.stacks()) wanted.add(stack.getItem());
        int count = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (wanted.contains(stack.getItem())) count += stack.getCount();
        }
        return count;
    }

    // What part of a category (or of the book, for null) has been read.
    private static float read(FoliosBook book, @Nullable String category) {
        int read = 0;
        int total = 0;
        FoliosMemory memory = FoliosMemory.of(book.id());
        for (FoliosEntry entry : book.entries().values()) {
            if (category != null && !entry.category().equals(category) || !entry.isOpen()) continue;
            total++;
            if (memory.isRead(entry.id())) read++;
        }
        return total == 0 ? 0 : read / (float) total;
    }

    /** How far along several objectives are together: the average of each. */
    public static float progress(FoliosBook book, List<FoliosObjective> objectives) {
        if (objectives.isEmpty()) return 0;
        float sum = 0;
        for (FoliosObjective objective : objectives) sum += objective.progress(book);
        return sum / objectives.size();
    }

    /** The JSON it was read from, for writing it back. */
    public JsonObject json() {
        return json.deepCopy();
    }
}
