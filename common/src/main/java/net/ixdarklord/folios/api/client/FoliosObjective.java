package net.ixdarklord.folios.api.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.GuideMemory;
import net.ixdarklord.folios.internal.client.GuideProgress;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Something the player makes progress on, shown with a bar: an objective of a quest page, and what a quest's entry
 * shows on its row. In JSON it is an object naming what it follows, with an optional {@code "label"} (text with marks,
 * or a translation key) in place of the one it makes for itself:
 * <ul>
 *     <li>{@code {"advancement": "minecraft:story/mine_stone"}}: done once the advancement is;</li>
 *     <li>{@code {"read": "basics/first_steps"}}: an entry of the book read;</li>
 *     <li>{@code {"category": "basics"}}: how much of a category has been read;</li>
 *     <li>{@code {"book": true}}: how much of the whole book has been read;</li>
 *     <li>{@code {"flag": "mymod:met_the_wizard"}}: a flag set in code ({@link FoliosGuides#setFlag});</li>
 *     <li>{@code {"item": "minecraft:diamond", "count": 5}}: items carried (an item, a {@code #tag} or a list);</li>
 *     <li>{@code {"source": "mymod:mana", "goal": 100}}: a value a mod reports ({@link FoliosObjectives#register}),
 *     done when it reaches the goal.</li>
 * </ul>
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

    private enum Kind { ADVANCEMENT, READ, CATEGORY, BOOK, FLAG, ITEM, SOURCE }

    private final JsonObject json;
    private final Kind kind;
    private final String target;
    private final int goal;
    private final @Nullable FoliosStack items;
    private final @Nullable String label;

    private FoliosObjective(JsonObject json, Kind kind, String target, int goal, @Nullable FoliosStack items, @Nullable String label) {
        this.json = json;
        this.kind = kind;
        this.target = target;
        this.goal = Math.max(1, goal);
        this.items = items;
        this.label = label;
    }

    private static FoliosObjective read(JsonObject json) {
        String label = json.has("label") ? json.get("label").getAsString() : null;
        int goal = json.has("goal") ? json.get("goal").getAsInt() : json.has("count") ? json.get("count").getAsInt() : 1;
        if (json.has("advancement")) return new FoliosObjective(json, Kind.ADVANCEMENT, json.get("advancement").getAsString(), 1, null, label);
        if (json.has("read")) return new FoliosObjective(json, Kind.READ, json.get("read").getAsString(), 1, null, label);
        if (json.has("category")) return new FoliosObjective(json, Kind.CATEGORY, json.get("category").getAsString(), 1, null, label);
        if (json.has("book")) return new FoliosObjective(json, Kind.BOOK, "", 1, null, label);
        if (json.has("flag")) return new FoliosObjective(json, Kind.FLAG, json.get("flag").getAsString(), 1, null, label);
        if (json.has("item")) {
            FoliosStack items = FoliosStack.CODEC.parse(JsonOps.INSTANCE, json.get("item")).getOrThrow(false, error -> {});
            return new FoliosObjective(json, Kind.ITEM, "", goal, items, label);
        }
        if (json.has("source")) return new FoliosObjective(json, Kind.SOURCE, json.get("source").getAsString(), goal, null, label);
        throw new IllegalArgumentException("An objective names one of advancement, read, category, book, flag, item or source");
    }

    /** How far along it is, 0 to 1. */
    public float progress(FoliosBook book) {
        return switch (kind) {
            case ADVANCEMENT -> GuideProgress.isDone(new ResourceLocation(target)) ? 1 : 0;
            case READ -> GuideMemory.of(book.id()).isRead(target) ? 1 : 0;
            case CATEGORY -> read(book, target);
            case BOOK -> read(book, null);
            case FLAG -> GuideProgress.flag(target) ? 1 : 0;
            case ITEM -> Mth.clamp(this.carried() / (float) goal, 0, 1);
            case SOURCE -> {
                FoliosObjectives.Source source = FoliosObjectives.get(new ResourceLocation(target));
                LocalPlayer player = Minecraft.getInstance().player;
                yield source == null || player == null ? 0 : Mth.clamp(source.value(player) / goal, 0, 1);
            }
        };
    }

    public boolean isDone(FoliosBook book) {
        return this.progress(book) >= 1;
    }

    /** Whether it is done or not done, with nothing between: no bar of its own is shown for it. */
    public boolean isAllOrNothing() {
        return kind == Kind.ADVANCEMENT || kind == Kind.READ || kind == Kind.FLAG || kind == Kind.ITEM && goal == 1;
    }

    /** What it says, with how far along it is where that is a count. */
    public Component label(FoliosBook book) {
        Component text = label != null ? FoliosText.inline(label) : this.defaultLabel(book);
        return switch (kind) {
            case ITEM -> text.copy().append(Component.literal(" " + Math.min(goal, this.carried()) + "/" + goal));
            case SOURCE -> {
                FoliosObjectives.Source source = FoliosObjectives.get(new ResourceLocation(target));
                LocalPlayer player = Minecraft.getInstance().player;
                int value = source == null || player == null ? 0 : (int) Math.min(goal, source.value(player));
                yield text.copy().append(Component.literal(" " + value + "/" + goal));
            }
            default -> text;
        };
    }

    private Component defaultLabel(FoliosBook book) {
        return switch (kind) {
            case ADVANCEMENT -> advancementTitle(new ResourceLocation(target));
            case READ -> {
                FoliosEntry entry = book.entry(target);
                yield Component.translatableWithFallback("folios.objective.read", "Read %s", entry != null ? entry.title() : Component.literal(target));
            }
            case CATEGORY -> {
                FoliosCategory category = book.category(target);
                yield Component.translatableWithFallback("folios.objective.category", "Read all of %s",
                        category != null ? category.title() : Component.literal(target));
            }
            case BOOK -> Component.translatableWithFallback("folios.objective.book", "Read the whole book");
            case FLAG -> Component.literal(target);
            case ITEM -> {
                ItemStack first = items != null ? items.first() : ItemStack.EMPTY;
                yield Component.translatableWithFallback("folios.objective.item", "Collect %s", first.getHoverName());
            }
            case SOURCE -> Component.literal(target);
        };
    }

    // An advancement's own title when the client was shown it, otherwise its name.
    private static Component advancementTitle(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            Advancement advancement = minecraft.getConnection().getAdvancements().getAdvancements().get(id);
            if (advancement != null) {
                DisplayInfo display = advancement.getDisplay();
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
        GuideMemory memory = GuideMemory.of(book.id());
        for (FoliosEntry entry : book.entries().values()) {
            if (category != null && !entry.category().equals(category) || !entry.isOpen()) continue;
            total++;
            if (memory.isRead(entry.id())) read++;
        }
        return total == 0 ? 0 : read / (float) total;
    }

    /** How far along several objectives are together: the average of each. */
    public static float progress(FoliosBook book, java.util.List<FoliosObjective> objectives) {
        if (objectives.isEmpty()) return 0;
        float sum = 0;
        for (FoliosObjective objective : objectives) sum += objective.progress(book);
        return sum / objectives.size();
    }
}
