package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.ixdarklord.folios.internal.client.FoliosRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// One or two recipes as the game shows them: a crafting grid, a furnace, a stonecutter or a smithing table, each
// slot showing in turn every item it takes. Recipes are asked of the server the first time the page is shown.
public record RecipePageElement(List<Identifier> recipes, Optional<String> title, String text) implements FoliosPageElement {
    public static final MapCodec<RecipePageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // One recipe, or a list of up to two.
            Codec.withAlternative(Identifier.CODEC.listOf(1, 2), Identifier.CODEC.xmap(List::of, List::getFirst)).fieldOf("recipe").forGetter(RecipePageElement::recipes),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(RecipePageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(RecipePageElement::text)
    ).apply(instance, RecipePageElement::new));

    private static final int BOX = 62;
    private static final int GAP = 4;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        Body body = this.body(layout);
        return layout.flow(title.orElse(null), body.height(), text, body.part());
    }

    /** What it shows between its title and its text. */
    Body body(FoliosLayout layout) {
        Map<SlotDisplay, List<ItemStack>> resolved = new IdentityHashMap<>();
        int room = recipes.size() * BOX + (recipes.size() - 1) * GAP;
        // Without a title of its own, a page of one recipe is named for what it makes, once that is known.
        boolean named = title.isEmpty() && recipes.size() == 1;
        net.ixdarklord.folios.api.client.page.FoliosIcon icon = layout.titleIcon();
        return new Body(room + (named ? FoliosLayout.TITLE_HEIGHT : 0), (painter, y) -> {
            int top = y;
            if (named) {
                List<RecipeDisplay> first = FoliosRecipes.get(recipes.getFirst());
                if (first != null && !first.isEmpty()) {
                    ItemStack made = stacks(resolved, first.getFirst().result()).stream().findFirst().orElse(ItemStack.EMPTY);
                    if (!made.isEmpty()) FoliosLayout.title(painter, made.getHoverName(), y, icon);
                }
                top += FoliosLayout.TITLE_HEIGHT;
            }
            for (Identifier recipe : recipes) {
                draw(painter, resolved, recipe, top);
                top += BOX + GAP;
            }
        });
    }

    private static void draw(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, Identifier id, int y) {
        FoliosPalette palette = painter.palette();
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        Shapes.panel(painter, left, y, right, y + BOX);
        List<RecipeDisplay> displays = FoliosRecipes.get(id);
        if (displays == null || displays.isEmpty()) {
            Component message = displays == null
                    ? Component.translatableWithFallback("folios.recipe.loading", "Loading recipe...")
                    : Component.translatableWithFallback("folios.recipe.missing", "No recipe %s", id.toString());
            List<net.minecraft.util.FormattedCharSequence> lines = painter.font().split(message, right - left - 10);
            int lineY = y + BOX / 2 - lines.size() * 5;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                painter.text(line, (left + right) / 2 - painter.font().width(line) / 2, lineY, palette.muted());
                lineY += 10;
            }
            return;
        }
        // A recipe with several ways to look shows each in turn.
        RecipeDisplay display = displays.get(Mth.positiveModulo((int) (painter.time() / (FoliosStack.CYCLE_SECONDS * 3)), displays.size()));
        int gridX = left + 7;
        int gridY = y + 4;
        int resultX = right - 30;
        int resultY = y + BOX / 2 - 13;
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
                Component time = Component.literal(String.format(java.util.Locale.ROOT, "%.1fs", furnace.duration() / 20.0F)).withStyle(ChatFormatting.ITALIC);
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
            default -> painter.text(Component.literal("?"), gridX + 24, gridY + 22, palette.muted());
        }
        // The result in a box near the top and, under it, a smaller box of the same make for the block it is made at,
        // joined to it by a short stem.
        List<ItemStack> stations = stacks(resolved, display.craftingStation());
        int top = stations.isEmpty() ? resultY : y + 6;
        Shapes.arrow(painter, resultX - 17, top + 10, palette.accent());
        box(painter, resultX - 4, top - 4, resultX + 26, top + 26, true);
        slot(painter, resolved, display.result(), resultX + 2, top + 2, true);
        if (!stations.isEmpty()) {
            ItemStack station = stations.get(Mth.positiveModulo((int) (painter.time() / FoliosStack.CYCLE_SECONDS), stations.size()));
            int middle = resultX + 11;
            int stationTop = top + 31;
            painter.fill(middle - 1, top + 26, middle + 1, stationTop, frame(palette, false));
            box(painter, middle - 10, stationTop, middle + 10, stationTop + 20, false);
            painter.item(station, middle - 8, stationTop + 2);
        }
    }

    private static int frame(FoliosPalette palette, boolean result) {
        return FoliosPalette.mix(palette.line(), palette.accent(), result ? 0.55F : 0.3F);
    }

    // A box with softened corners: a hairline edge, a face washed with the book's colour (stronger for a result),
    // and a light line under its top edge.
    private static void box(FoliosPainter painter, int x0, int y0, int x1, int y1, boolean result) {
        FoliosPalette palette = painter.palette();
        int edge = frame(palette, result);
        int face = FoliosPalette.mix(palette.panel(), palette.accent(), result ? 0.16F : 0.07F);
        painter.fill(x0 + 1, y0, x1 - 1, y1, edge);
        painter.fill(x0, y0 + 1, x1, y1 - 1, edge);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, face);
        painter.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, FoliosPalette.mix(face, 0xFFFFFFFF, 0.35F));
    }

    private static void grid(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) Shapes.slot(painter, x + column * 18, y + row * 18);
        }
    }

    private static void slot(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, SlotDisplay display, int x, int y, boolean count) {
        List<ItemStack> stacks = stacks(resolved, display);
        if (stacks.isEmpty()) return;
        ItemStack stack = stacks.get(Mth.positiveModulo((int) (painter.time() / FoliosStack.CYCLE_SECONDS), stacks.size()));
        painter.item(count ? stack : stack.copyWithCount(1), x + 1, y + 1);
    }

    private static void flame(FoliosPainter painter, int x, int y) {
        int color = 0xFFE8862A;
        painter.fill(x + 3, y, x + 5, y + 2, color);
        painter.fill(x + 2, y + 2, x + 6, y + 5, color);
        painter.fill(x + 1, y + 5, x + 7, y + 8, color);
        painter.fill(x + 3, y + 5, x + 5, y + 8, 0xFFF6D04D);
    }

    private static List<ItemStack> stacks(Map<SlotDisplay, List<ItemStack>> resolved, SlotDisplay display) {
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
