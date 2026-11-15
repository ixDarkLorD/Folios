package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.FoliosRecipes;
import net.ixdarklord.folios.internal.client.paint.GuiSurface;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.ixdarklord.folios.internal.client.viewer.ViewerTheme;
import net.ixdarklord.folios.internal.client.viewer.Viewers;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.joml.Vector2f;

import java.util.List;
import java.util.Optional;

public record ViewerRecipePageElement(List<Identifier> recipes, Optional<String> title, String text, int height, List<FoliosStack> items)
        implements FoliosPageElement {
    public static final MapCodec<ViewerRecipePageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.withAlternative(Identifier.CODEC.listOf(1, 16), Identifier.CODEC.xmap(List::of, List::getFirst)).fieldOf("recipe").forGetter(ViewerRecipePageElement::recipes),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(ViewerRecipePageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(ViewerRecipePageElement::text),
            Codec.intRange(32, 150).optionalFieldOf("height", 72).forGetter(ViewerRecipePageElement::height),
            Codec.withAlternative(FoliosStack.CODEC.listOf(), FoliosStack.CODEC.xmap(List::of, List::getFirst)).optionalFieldOf("item", List.of())
                    .forGetter(ViewerRecipePageElement::items)
    ).apply(instance, ViewerRecipePageElement::new));

    private static final int PAD = 5;
    private static final int BADGE = 5;
    private static final int SLACK = 2;
    private static final float CYCLE_SECONDS = 4;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        int box = Math.max(height, 40);
        int[] mouse = {-10000, -10000};
        int[] shown = {-1};
        return layout.flow(title.orElse(null), RecipePageElement.LIFT + box, text, (painter, y) -> this.draw(painter, y + RecipePageElement.LIFT, box, mouse, shown));
    }

    private void draw(FoliosPainter painter, int y, int box, int[] mouse, int[] shown) {
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        Shapes.panel(painter, left, y, right, y + box);
        int index = recipes.size() == 1 ? 0 : Mth.positiveModulo((int) (painter.time(CYCLE_SECONDS) / CYCLE_SECONDS), recipes.size());
        if (shown[0] >= 0 && painter.hovered(left, y, right - left, box)) index = shown[0];
        shown[0] = index;
        Identifier id = recipes.get(index);
        ViewerRecipe recipe = Viewers.recipe(id);
        if (recipe == null) {
            this.missing(painter, id, left, y, right, box);
            return;
        }
        if (!viewer(painter, recipe, left, y, right, box, mouse)) this.summary(painter, recipe, left, y, right, box);
    }

    static boolean viewer(FoliosPainter painter, ViewerRecipe recipe, int left, int y, int right, int box, int[] mouse) {
        int reserve = recipe.stations().isEmpty() ? 0 : BADGE;
        int innerWidth = right - left - PAD * 2;
        int innerHeight = box - PAD * 2 - reserve;
        int innerTop = y + PAD + reserve;
        float scale = Math.min(1, Math.min((innerWidth - SLACK * 2) / (float) Math.max(1, recipe.width()), (innerHeight - SLACK * 2) / (float) Math.max(1, recipe.height())));
        int drawnWidth = Math.round(recipe.width() * scale);
        int drawnHeight = Math.round(recipe.height() * scale);
        int offsetX = (innerWidth - drawnWidth) / 2;
        int offsetY = (innerHeight - drawnHeight) / 2;
        boolean drawn = GuiSurface.gui(painter, left + PAD, innerTop, innerWidth, innerHeight, (graphics, mouseX, mouseY) -> {
            if (!recipe.followsPose()) {
                Vector2f from = graphics.pose().transformPosition(0, 0, new Vector2f());
                Vector2f to = graphics.pose().transformPosition(innerWidth, innerHeight, new Vector2f());
                int x = Math.round(from.x + (to.x - from.x - recipe.width()) / 2);
                int top = Math.round(from.y + (to.y - from.y - recipe.height()) / 2);
                Minecraft minecraft = Minecraft.getInstance();
                mouse[0] = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
                mouse[1] = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
                ViewerTheme.begin(painter);
                try {
                    recipe.drawAt(graphics, x, top, mouse[0], mouse[1]);
                } finally {
                    ViewerTheme.end();
                }
                return;
            }
            int localX = (int) Math.floor((mouseX - offsetX) / scale);
            int localY = (int) Math.floor((mouseY - offsetY) / scale);
            mouse[0] = localX;
            mouse[1] = localY;
            graphics.pose().pushMatrix();
            graphics.pose().translate(offsetX, offsetY);
            graphics.pose().scale(scale, scale);
            ViewerTheme.begin(painter);
            try {
                recipe.draw(graphics, localX, localY);
            } finally {
                ViewerTheme.end();
            }
            graphics.pose().popMatrix();
        });
        if (drawn) RecipePageElement.station(painter, recipe.stations(), right, y);
        if (drawn) painter.action(left + PAD + offsetX, innerTop + offsetY, drawnWidth, drawnHeight, null, () -> recipe.click(mouse[0], mouse[1]));
        return drawn;
    }

    private void summary(FoliosPainter painter, ViewerRecipe recipe, int left, int y, int right, int box) {
        List<ItemStack> outputs = recipe.outputs();
        List<ItemStack> stations = recipe.stations();
        int middle = y + box / 2 - 12;
        if (!outputs.isEmpty()) {
            Shapes.slot(painter, left + 6, middle, 18);
            painter.item(outputs.getFirst(), left + 7, middle + 1);
        }
        for (int index = 0; index < stations.size(); index++) {
            painter.item(stations.get(index), right - 22 - index * 18, middle + 1);
        }
        painter.text(recipe.title().getVisualOrderText(), left + 30, middle + 1, painter.palette().text());
        Component note = Component.translatableWithFallback("folios.viewer_recipe.on_screen", "Open on screen to see this recipe");
        this.lines(painter, note, left, middle + 24, right);
    }

    private void missing(FoliosPainter painter, Identifier id, int left, int y, int right, int box) {
        List<RecipeDisplay> vanilla = FoliosRecipes.get(id);
        if (!Viewers.any() && vanilla != null && !vanilla.isEmpty() && box >= RecipePageElement.BOX) {
            RecipePageElement.drawRecipe(painter, id, y + (box - RecipePageElement.BOX) / 2);
            return;
        }
        Component message = Viewers.any()
                ? Component.translatableWithFallback("folios.viewer_recipe.missing", "No recipe %s", id.toString())
                : Component.translatableWithFallback("folios.viewer_recipe.needs_viewer", "Needs JEI or REI to show this recipe");
        this.lines(painter, message, left, y + box / 2 - 5, right);
    }

    private void lines(FoliosPainter painter, Component message, int left, int y, int right) {
        List<FormattedCharSequence> lines = painter.font().split(message, right - left - 10);
        int lineY = y - (lines.size() - 1) * 5;
        for (FormattedCharSequence line : lines) {
            painter.text(line, (left + right) / 2 - painter.font().width(line) / 2, lineY, painter.palette().muted());
            lineY += 10;
        }
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
