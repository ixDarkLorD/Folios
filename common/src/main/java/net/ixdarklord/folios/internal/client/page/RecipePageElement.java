package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosRecipeLayout;
import net.ixdarklord.folios.api.client.page.FoliosRecipeLayouts;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.FoliosRecipes;
import net.ixdarklord.folios.internal.client.ItemRow;
import net.ixdarklord.folios.internal.client.paint.DrawsAbove;
import net.ixdarklord.folios.internal.client.paint.PixelIcon;
import net.ixdarklord.folios.internal.client.paint.TooltipMore;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.ixdarklord.folios.internal.client.viewer.Viewers;
import net.ixdarklord.folios.internal.config.FoliosClientConfig;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record RecipePageElement(List<Identifier> recipes, Optional<String> title, String text) implements FoliosPageElement {
    public static final MapCodec<RecipePageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.withAlternative(Identifier.CODEC.listOf(1, 2), Identifier.CODEC.xmap(List::of, List::getFirst)).fieldOf("recipe").forGetter(RecipePageElement::recipes),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(RecipePageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(RecipePageElement::text)
    ).apply(instance, RecipePageElement::new));

    static final int BOX = 62;
    private static final int GAP = 4;
    static final int LIFT = 6;
    private static final float ARROW_SCALE = 1.5F;
    private static final Set<Identifier> FAILED = new HashSet<>();
    private static final Map<Class<?>, List<Field>> PARTS = new IdentityHashMap<>();

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        Body body = this.body(layout);
        return layout.flow(title.orElse(null), body.height(), text, body.part());
    }

    Body body(FoliosLayout layout) {
        Map<SlotDisplay, List<ItemStack>> resolved = new IdentityHashMap<>();
        int room = recipes.size() * (LIFT + BOX) + (recipes.size() - 1) * GAP;
        boolean named = title.isEmpty() && recipes.size() == 1;
        FoliosIcon icon = layout.titleIcon();
        return new Body(room + (named ? FoliosLayout.TITLE_HEIGHT : 0), (painter, y) -> {
            int top = y;
            if (named) {
                List<RecipeDisplay> first = FoliosRecipes.get(recipes.getFirst());
                ItemStack made = first != null && !first.isEmpty() ? stacks(resolved, first.getFirst().result()).stream().findFirst().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
                ViewerRecipe viewed = made.isEmpty() ? Viewers.recipe(recipes.getFirst()) : null;
                if (viewed != null && !viewed.outputs().isEmpty()) made = viewed.outputs().getFirst();
                if (!made.isEmpty()) FoliosLayout.title(painter, made.getHoverName(), y, icon);
                top += FoliosLayout.TITLE_HEIGHT;
            }
            for (int index = 0; index < recipes.size(); index++) {
                draw(painter, resolved, recipes.get(index), top + LIFT);
                top += LIFT + BOX + GAP;
            }
        });
    }

    private static void draw(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, Identifier id, int y) {
        drawRecipe(painter, resolved, id, y);
        bookmark(painter, id, y);
    }

    private static void drawRecipe(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, Identifier id, int y) {
        FoliosPalette palette = painter.palette();
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        Shapes.panel(painter, left, y, right, y + BOX);
        List<RecipeDisplay> displays = FoliosRecipes.get(id);
        if (displays == null || displays.isEmpty()) {
            message(painter, displays == null
                    ? Component.translatableWithFallback("folios.recipe.loading", "Loading recipe...")
                    : Component.translatableWithFallback("folios.recipe.missing", "No recipe %s", id.toString()), left, y, right, palette.muted());
            return;
        }
        RecipeDisplay display = displays.get(Mth.positiveModulo((int) (painter.time(FoliosStack.CYCLE_SECONDS * 3) / (FoliosStack.CYCLE_SECONDS * 3)), displays.size()));
        try {
            if (!display(painter, resolved, display, left, y, right)) {
                message(painter, Component.translatableWithFallback("folios.recipe.unknown", "This recipe can't be shown"), left, y, right, error(palette));
            }
        } catch (RuntimeException failure) {
            fail(id, "its display", failure);
            Shapes.panel(painter, left, y, right, y + BOX);
            message(painter, Component.translatableWithFallback("folios.recipe.error", "This recipe couldn't be drawn"), left, y, right, error(palette));
        }
    }

    private static boolean display(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, RecipeDisplay display, int left, int y, int right) {
        FoliosRecipeLayout<RecipeDisplay> layout = FoliosRecipeLayouts.get(display);
        if (layout != null) {
            RecipeCanvas.of(display, layout).draw(painter, resolved, left, y, right, BOX);
            return true;
        }
        FoliosPalette palette = painter.palette();
        int gridX = left + 7;
        int gridY = y + 4;
        int resultX = right - 30;
        int resultY = y + BOX / 2 - 10;
        switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> {
                grid(painter, resolved, gridX, gridY, 3, 3);
                for (int row = 0; row < shaped.height(); row++) {
                    for (int column = 0; column < shaped.width(); column++) {
                        slot(painter, resolved, shaped.ingredients().get(row * shaped.width() + column), gridX + column * 18, gridY + row * 18, false);
                    }
                }
            }
            case ShapelessCraftingRecipeDisplay shapeless -> {
                grid(painter, resolved, gridX, gridY, 3, 3);
                for (int index = 0; index < Math.min(9, shapeless.ingredients().size()); index++) {
                    slot(painter, resolved, shapeless.ingredients().get(index), gridX + index % 3 * 18, gridY + index / 3 * 18, false);
                }
                painter.text(Component.literal("~"), resultX - 30, y + 6, palette.muted());
            }
            case FurnaceRecipeDisplay furnace -> {
                int x = gridX + 18;
                Shapes.slot(painter, x, gridY);
                slot(painter, resolved, furnace.ingredient(), x, gridY, false);
                flame(painter, x + 5, gridY + 22);
                Shapes.slot(painter, x, gridY + 36);
                slot(painter, resolved, furnace.fuel(), x, gridY + 36, false);
                Component time = Component.literal(String.format(Locale.ROOT, "%.1fs", furnace.duration() / 20.0F)).withStyle(ChatFormatting.ITALIC);
                painter.text(time, x + 22, gridY + 41, palette.muted());
            }
            case StonecutterRecipeDisplay cutting -> {
                Shapes.slot(painter, gridX + 18, gridY + 18);
                slot(painter, resolved, cutting.input(), gridX + 18, gridY + 18, false);
            }
            case SmithingRecipeDisplay smithing -> {
                SlotDisplay[] parts = {smithing.template(), smithing.base(), smithing.addition()};
                for (int index = 0; index < 3; index++) {
                    Shapes.slot(painter, gridX + index * 19, gridY + 18);
                    slot(painter, resolved, parts[index], gridX + index * 19, gridY + 18, false);
                }
            }
            default -> {
                List<SlotDisplay> inputs = inputs(display);
                if (inputs.isEmpty() && stacks(resolved, display.result()).isEmpty()) return false;
                int pages = Math.max(1, (inputs.size() + 8) / 9);
                int page = Mth.positiveModulo((int) (painter.time(FoliosStack.CYCLE_SECONDS * 2) / (FoliosStack.CYCLE_SECONDS * 2)), pages);
                List<SlotDisplay> shown = inputs.subList(Math.min(inputs.size(), page * 9), Math.min(inputs.size(), page * 9 + 9));
                int columns = Math.min(3, shown.size());
                int rows = (shown.size() + 2) / 3;
                int startX = gridX + (3 - columns) * 9;
                int startY = y + (BOX - rows * 18) / 2;
                for (int index = 0; index < shown.size(); index++) {
                    int x = startX + index % 3 * 18;
                    int top = startY + index / 3 * 18;
                    Shapes.slot(painter, x, top);
                    slot(painter, resolved, shown.get(index), x, top, true);
                }
                if (pages > 1) painter.text(Component.literal((page + 1) + "/" + pages), gridX, y + BOX - 9, palette.muted());
            }
        }
        int top = resultY;
        if (display instanceof FurnaceRecipeDisplay furnace && furnace.duration() > 0) {
            float seconds = furnace.duration() / 20.0F;
            float progress = (painter.time() % seconds) / seconds;
            arrow(painter, resultX - 11.5F, top + 10, FoliosPalette.mix(palette.panel(), palette.line(), 0.75F), 1);
            arrow(painter, resultX - 11.5F, top + 10, palette.accent(), progress);
        } else {
            arrow(painter, resultX - 11.5F, top + 10, palette.accent(), 1);
        }
        Shapes.resultSlot(painter, resultX - 3, top - 3, 26);
        slot(painter, resolved, display.result(), resultX + 1, top + 1, true);
        station(painter, stacks(resolved, display.craftingStation()), right, y);
        return true;
    }

    static void arrow(FoliosPainter painter, float centerX, float centerY, int color, float shown) {
        painter.push();
        painter.translate(centerX - 9 * ARROW_SCALE / 2, centerY - 7 * ARROW_SCALE / 2);
        painter.scale(ARROW_SCALE);
        if (shown >= 1) Shapes.arrow(painter, 0, 0, color);
        else painter.clip(0, 0, Math.round(9 * shown), 7, () -> Shapes.arrow(painter, 0, 0, color));
        painter.pop();
    }

    private static final PixelIcon RIBBON = PixelIcon.of("bookmark", 7, 9);
    private static final PixelIcon RIBBON_EDGE = PixelIcon.of("bookmark_edge", 7, 9);

    private static void bookmark(FoliosPainter painter, Identifier id, int y) {
        if (!FoliosClientConfig.BOOKMARK_BUTTON.get() || FAILED.contains(id)) return;
        ViewerRecipe viewed;
        try {
            viewed = Viewers.recipe(id);
            if (viewed == null || !viewed.canBookmark()) return;
        } catch (RuntimeException | LinkageError failure) {
            fail(id, "the recipe viewer's bookmarks", failure);
            return;
        }
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        int width = 7;
        int height = 9;
        FoliosClientConfig.Corner corner = FoliosClientConfig.BOOKMARK_CORNER.get();
        int x = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> left + 3;
            case TOP_RIGHT, BOTTOM_RIGHT -> right - width - 3;
        };
        int top = switch (corner) {
            case TOP_LEFT -> y + 3;
            case TOP_RIGHT -> y + 14;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> y + BOX - height - 3;
        };
        boolean marked = viewed.bookmarked();
        Component name = marked ? Component.translatableWithFallback("folios.recipe.unbookmark", "Remove from JEI bookmarks")
                : Component.translatableWithFallback("folios.recipe.bookmark", "Bookmark in JEI");
        boolean hovered = painter.action(x - 1, top - 1, width + 2, height + 2, name, viewed::toggleBookmark);
        FoliosPalette palette = painter.palette();
        int face = marked ? palette.accent() : FoliosPalette.mix(palette.paper(), 0xFFFFFFFF, 0.5F);
        if (hovered) face = FoliosPalette.mix(face, marked ? 0xFFFFFFFF : palette.accent(), marked ? 0.25F : 0.35F);
        int edge = FoliosPalette.mix(marked ? palette.accent() : palette.line(), 0xFF000000, marked ? 0.45F : 0.35F);
        int fill = face;
        DrawsAbove.above(painter, () -> {
            RIBBON.draw(painter, x, top, fill);
            RIBBON_EDGE.draw(painter, x, top, edge);
        });
    }

    static void station(FoliosPainter painter, List<ItemStack> stations, int right, int y) {
        if (stations.isEmpty()) return;
        ItemStack station = stations.get(Mth.positiveModulo((int) (painter.time(FoliosStack.CYCLE_SECONDS) / FoliosStack.CYCLE_SECONDS), stations.size()));
        int x0 = right - 16;
        int y0 = y - LIFT + 1;
        FoliosPalette palette = painter.palette();
        painter.fill(x0 - 1, y0 - 1, x0 + 15, y0 + 15, markerColor(palette));
        Shapes.slot(painter, x0, y0, 14);
        painter.push();
        painter.translate(x0 + 2, y0 + 2);
        painter.scale(0.625F);
        painter.item(station, 0, 0);
        painter.pop();
    }

    private static List<SlotDisplay> inputs(RecipeDisplay display) {
        List<Field> fields = PARTS.computeIfAbsent(display.getClass(), RecipePageElement::parts);
        List<SlotDisplay> inputs = new ArrayList<>();
        SlotDisplay result = display.result();
        SlotDisplay station = display.craftingStation();
        for (Field field : fields) {
            Object value;
            try {
                value = field.get(display);
            } catch (IllegalAccessException failure) {
                continue;
            }
            if (value instanceof SlotDisplay part) {
                if (part != result && part != station) inputs.add(part);
            } else if (value instanceof List<?> list) {
                for (Object entry : list) if (entry instanceof SlotDisplay part) inputs.add(part);
            }
        }
        return inputs;
    }

    private static List<Field> parts(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        List<String> order = new ArrayList<>();
        if (type.isRecord()) for (RecordComponent component : type.getRecordComponents()) order.add(component.getName());
        for (Class<?> at = type; at != null && at != Object.class; at = at.getSuperclass()) {
            for (Field field : at.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!SlotDisplay.class.isAssignableFrom(field.getType()) && !List.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                } catch (RuntimeException failure) {
                    continue;
                }
                fields.add(field);
            }
        }
        fields.sort((a, b) -> Integer.compare(index(order, a.getName()), index(order, b.getName())));
        return fields;
    }

    private static int index(List<String> order, String name) {
        int at = order.indexOf(name);
        return at < 0 ? order.size() : at;
    }

    private static void fail(Identifier id, String part, Throwable failure) {
        if (FAILED.add(id)) Folios.LOGGER.warn("Couldn't draw recipe {} through {}", id, part, failure);
    }

    private static int error(FoliosPalette palette) {
        return FoliosPalette.mix(palette.text(), 0xFFB3261E, 0.7F);
    }

    private static void message(FoliosPainter painter, Component message, int left, int y, int right, int color) {
        List<FormattedCharSequence> lines = painter.font().split(message, right - left - 12);
        int lineY = y + BOX / 2 - lines.size() * 5 + 1;
        for (FormattedCharSequence line : lines) {
            painter.text(line, (left + right) / 2 - painter.font().width(line) / 2, lineY, color);
            lineY += 10;
        }
    }

    private static void grid(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) Shapes.slot(painter, x + column * 18, y + row * 18);
        }
    }

    private static void slot(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, SlotDisplay display, int x, int y, boolean count) {
        items(painter, stacks(resolved, display), tag(display), x, y, count);
    }

    static @Nullable String tag(SlotDisplay display) {
        return display instanceof SlotDisplay.TagSlotDisplay tag ? tag.tag().unwrapKey().map(key -> key.location().toString()).orElse(null) : null;
    }

    static boolean items(FoliosPainter painter, List<ItemStack> stacks, @Nullable String tag, int x, int y, boolean count) {
        if (stacks.isEmpty()) return false;
        ItemStack stack = stacks.get(Mth.positiveModulo((int) (painter.time(FoliosStack.CYCLE_SECONDS) / FoliosStack.CYCLE_SECONDS), stacks.size()));
        painter.item(count ? stack : stack.copyWithCount(1), x + 1, y + 1);
        boolean hovered = painter.hovered(x + 1, y + 1, 16, 16);
        if (stacks.size() < 2) return hovered;
        DrawsAbove.above(painter, () -> choice(painter, x, y, tag != null));
        if (hovered) TooltipMore.more(painter, accepts(tag, stacks));
        return hovered;
    }

    private static final String[] TAG = {".#.#.", "#####", ".#.#.", "#####", ".#.#."};
    private static final String[] LIST = {"#.###", ".....", "#.###", ".....", "#.###"};

    private static int markerColor(FoliosPalette palette) {
        return FoliosPalette.mix(palette.accent(), 0xFFFFFFFF, 0.4F);
    }

    private static void choice(FoliosPainter painter, int x, int y, boolean tag) {
        int accent = painter.palette().accent();
        int color = markerColor(painter.palette());
        int shadow = FoliosPalette.mix(accent, 0xFF000000, 0.7F);
        String[] glyph = tag ? TAG : LIST;
        for (int pass = 0; pass < 2; pass++) {
            int offset = pass == 0 ? 1 : 0;
            int tint = pass == 0 ? shadow : color;
            for (int row = 0; row < glyph.length; row++) {
                for (int column = 0; column < glyph[row].length(); column++) {
                    if (glyph[row].charAt(column) != '#') continue;
                    int px = x + 2 + column + offset, py = y + 2 + row + offset;
                    painter.fill(px, py, px + 1, py + 1, tint);
                }
            }
        }
    }

    private static List<Component> accepts(@Nullable String tag, List<ItemStack> stacks) {
        List<Component> lines = new ArrayList<>();
        lines.add(tag != null
                ? Component.translatableWithFallback("folios.recipe.any_tag", "Accepts any of #%s:", tag).withStyle(ChatFormatting.GRAY)
                : Component.translatableWithFallback("folios.recipe.any", "Accepts any of:").withStyle(ChatFormatting.GRAY));
        int shown = Math.min(stacks.size(), ItemRow.COLUMNS * 3);
        lines.add(ItemRow.of(stacks.subList(0, shown)));
        if (stacks.size() > shown) {
            lines.add(Component.translatableWithFallback("folios.recipe.any_more", "and %s more", stacks.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static void flame(FoliosPainter painter, int x, int y) {
        flame(painter, x, y, true);
    }

    static void flame(FoliosPainter painter, int x, int y, boolean lit) {
        FoliosPalette palette = painter.palette();
        int color = lit ? 0xFFE8862A : FoliosPalette.mix(palette.panel(), palette.line(), 0.75F);
        int core = lit ? 0xFFF6D04D : color;
        painter.fill(x + 3, y, x + 5, y + 2, color);
        painter.fill(x + 2, y + 2, x + 6, y + 5, color);
        painter.fill(x + 1, y + 5, x + 7, y + 8, color);
        painter.fill(x + 3, y + 5, x + 5, y + 8, core);
    }

    static List<ItemStack> stacks(Map<SlotDisplay, List<ItemStack>> resolved, SlotDisplay display) {
        List<ItemStack> stacks = resolved.get(display);
        if (stacks == null) {
            ContextMap context = context();
            if (context == null) return List.of();
            stacks = display.resolveForStacks(context);
            resolved.put(display, stacks);
        }
        return stacks;
    }

    private static @Nullable ContextMap context() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : SlotDisplayContext.fromLevel(level);
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
