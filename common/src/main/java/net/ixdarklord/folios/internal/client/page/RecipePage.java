package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.GuideText;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideStack;
import net.ixdarklord.folios.internal.client.GuideRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.ixdarklord.folios.internal.compat.RecipeDisplay;
import net.ixdarklord.folios.internal.compat.RecipeDisplay.FurnaceRecipeDisplay;
import net.ixdarklord.folios.internal.compat.RecipeDisplay.ShapedCraftingRecipeDisplay;
import net.ixdarklord.folios.internal.compat.RecipeDisplay.ShapelessCraftingRecipeDisplay;
import net.ixdarklord.folios.internal.compat.RecipeDisplay.SlotDisplay;
import net.ixdarklord.folios.internal.compat.RecipeDisplay.SmithingRecipeDisplay;
import net.ixdarklord.folios.internal.compat.RecipeDisplay.StonecutterRecipeDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// One or two recipes as the game shows them: a crafting grid, a furnace, a stonecutter or a smithing table, each
// slot showing in turn every item it takes. Recipes are read from those the client was sent.
public record RecipePage(List<ResourceLocation> recipes, Optional<String> title, String text) implements GuidePage {
    public static final MapCodec<RecipePage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.mapEither(net.ixdarklord.folios.internal.compat.Compat.listOf(ResourceLocation.CODEC, 1, 2).fieldOf("recipes"), ResourceLocation.CODEC.fieldOf("recipe"))
                    .xmap(either -> either.map(list -> list, List::of), Either::<List<ResourceLocation>, ResourceLocation>left).forGetter(RecipePage::recipes),
            Codec.STRING.optionalFieldOf("title").forGetter(RecipePage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(RecipePage::text)
    ).apply(instance, RecipePage::new));

    private static final int BOX = 62;
    private static final int GAP = 4;

    @Override
    public List<Laid> lay(GuideLayout layout) {
        Map<SlotDisplay, List<ItemStack>> resolved = new IdentityHashMap<>();
        int room = recipes.size() * BOX + (recipes.size() - 1) * GAP;
        // Without a title of its own, a page of one recipe is named for what it makes, once that is known.
        boolean named = title.isEmpty() && recipes.size() == 1;
        return layout.flow(title.orElse(null), room + (named ? GuideLayout.TITLE_HEIGHT : 0), text, (painter, y) -> {
            int top = y;
            if (named) {
                List<RecipeDisplay> first = GuideRecipes.get(recipes.get(0));
                if (first != null && !first.isEmpty()) {
                    ItemStack made = stacks(resolved, first.get(0).result()).stream().findFirst().orElse(ItemStack.EMPTY);
                    if (!made.isEmpty()) GuideLayout.title(painter, made.getHoverName(), y);
                }
                top += GuideLayout.TITLE_HEIGHT;
            }
            for (ResourceLocation recipe : recipes) {
                draw(painter, resolved, recipe, top);
                top += BOX + GAP;
            }
        });
    }

    private static void draw(GuidePainter painter, Map<SlotDisplay, List<ItemStack>> resolved, ResourceLocation id, int y) {
        GuidePalette palette = painter.palette();
        int left = GuideLayout.CONTENT_LEFT;
        int right = GuideLayout.CONTENT_RIGHT;
        Shapes.panel(painter, left, y, right, y + BOX);
        List<RecipeDisplay> displays = GuideRecipes.get(id);
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
        RecipeDisplay display = displays.get(Mth.positiveModulo((int) (painter.time() / (GuideStack.CYCLE_SECONDS * 3)), displays.size()));
        int gridX = left + 7;
        int gridY = y + 4;
        int resultX = right - 30;
        int resultY = y + BOX / 2 - 13;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            grid(painter, resolved, gridX, gridY, 3, 3);
            for (int row = 0; row < shaped.height(); row++) {
                for (int column = 0; column < shaped.width(); column++) {
                    slot(painter, resolved, shaped.ingredients().get(row * shaped.width() + column), gridX + column * 18, gridY + row * 18, false);
                }
            }
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            grid(painter, resolved, gridX, gridY, 3, 3);
            for (int index = 0; index < Math.min(9, shapeless.ingredients().size()); index++) {
                slot(painter, resolved, shapeless.ingredients().get(index), gridX + index % 3 * 18, gridY + index / 3 * 18, false);
            }
            painter.text(Component.literal("~"), resultX - 30, y + 6, palette.muted());
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            int x = gridX + 18;
            Shapes.slot(painter, x, gridY);
            slot(painter, resolved, furnace.ingredient(), x, gridY, false);
            flame(painter, x + 5, gridY + 22);
            Shapes.slot(painter, x, gridY + 36);
            slot(painter, resolved, furnace.fuel(), x, gridY + 36, false);
            Component time = Component.literal(String.format(java.util.Locale.ROOT, "%.1fs", furnace.duration() / 20.0F)).withStyle(ChatFormatting.ITALIC);
            painter.text(time, x + 22, gridY + 41, palette.muted());
        } else if (display instanceof StonecutterRecipeDisplay cutting) {
            Shapes.slot(painter, gridX + 18, gridY + 18);
            slot(painter, resolved, cutting.input(), gridX + 18, gridY + 18, false);
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            SlotDisplay[] parts = {smithing.template(), smithing.base(), smithing.addition()};
            for (int index = 0; index < 3; index++) {
                Shapes.slot(painter, gridX + index * 19, gridY + 18);
                slot(painter, resolved, parts[index], gridX + index * 19, gridY + 18, false);
            }
        } else {
            painter.text(Component.literal("?"), gridX + 24, gridY + 22, palette.muted());
        }
        // The result in a box near the top and, under it, a smaller box of the same make for the block it is made at,
        // joined to it by a short stem.
        List<ItemStack> stations = stacks(resolved, display.craftingStation());
        int top = stations.isEmpty() ? resultY : y + 6;
        Shapes.arrow(painter, resultX - 17, top + 10, palette.accent());
        box(painter, resultX - 4, top - 4, resultX + 26, top + 26, true);
        slot(painter, resolved, display.result(), resultX + 2, top + 2, true);
        if (!stations.isEmpty()) {
            ItemStack station = stations.get(Mth.positiveModulo((int) (painter.time() / GuideStack.CYCLE_SECONDS), stations.size()));
            int middle = resultX + 11;
            int stationTop = top + 31;
            painter.fill(middle - 1, top + 26, middle + 1, stationTop, frame(palette, false));
            box(painter, middle - 10, stationTop, middle + 10, stationTop + 20, false);
            painter.item(station, middle - 8, stationTop + 2);
        }
    }

    private static int frame(GuidePalette palette, boolean result) {
        return GuidePalette.mix(palette.line(), palette.accent(), result ? 0.55F : 0.3F);
    }

    // A box with softened corners: a hairline edge, a face washed with the book's colour (stronger for a result),
    // and a light line under its top edge.
    private static void box(GuidePainter painter, int x0, int y0, int x1, int y1, boolean result) {
        GuidePalette palette = painter.palette();
        int edge = frame(palette, result);
        int face = GuidePalette.mix(palette.panel(), palette.accent(), result ? 0.16F : 0.07F);
        painter.fill(x0 + 1, y0, x1 - 1, y1, edge);
        painter.fill(x0, y0 + 1, x1, y1 - 1, edge);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, face);
        painter.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, GuidePalette.mix(face, 0xFFFFFFFF, 0.35F));
    }

    private static void grid(GuidePainter painter, Map<SlotDisplay, List<ItemStack>> resolved, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) Shapes.slot(painter, x + column * 18, y + row * 18);
        }
    }

    private static void slot(GuidePainter painter, Map<SlotDisplay, List<ItemStack>> resolved, SlotDisplay display, int x, int y, boolean count) {
        List<ItemStack> stacks = stacks(resolved, display);
        if (stacks.isEmpty()) return;
        ItemStack stack = stacks.get(Mth.positiveModulo((int) (painter.time() / GuideStack.CYCLE_SECONDS), stacks.size()));
        painter.item(count ? stack : stack.copyWithCount(1), x + 1, y + 1);
    }

    private static void flame(GuidePainter painter, int x, int y) {
        int color = 0xFFE8862A;
        painter.fill(x + 3, y, x + 5, y + 2, color);
        painter.fill(x + 2, y + 2, x + 6, y + 5, color);
        painter.fill(x + 1, y + 5, x + 7, y + 8, color);
        painter.fill(x + 3, y + 5, x + 5, y + 8, 0xFFF6D04D);
    }

    private static List<ItemStack> stacks(Map<SlotDisplay, List<ItemStack>> resolved, SlotDisplay display) {
        return display.stacks();
    }

    @Override
    public String searchText() {
        return GuideText.resolve(title.orElse("")) + " " + GuideText.resolve(text);
    }
}
