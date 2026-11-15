package net.ixdarklord.folios.internal.client.viewer.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class EmiRecipeView implements ViewerRecipe {
    private final EmiRecipe recipe;
    private final int width;
    private final int height;
    private final List<Widget> widgets = new ArrayList<>();

    EmiRecipeView(EmiRecipe recipe) {
        this.recipe = recipe;
        this.width = recipe.getDisplayWidth();
        this.height = recipe.getDisplayHeight();
        recipe.addWidgets(new WidgetHolder() {
            @Override
            public int getWidth() {
                return width;
            }

            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public <T extends Widget> T add(T widget) {
                widgets.add(widget);
                return widget;
            }
        });
    }

    boolean shows(EmiRecipe other) {
        return recipe == other;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuiGraphics raw = graphics.raw();
        float delta = Minecraft.getInstance().getFrameTime();
        for (Widget widget : widgets) widget.render(raw, mouseX, mouseY, delta);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        for (Widget widget : widgets) {
            if (!(widget instanceof SlotWidget slot) || !widget.getBounds().contains(mouseX, mouseY)) continue;
            EmiIngredient stack = slot.getStack();
            if (stack.isEmpty()) return false;
            EmiApi.displayRecipes(stack);
            return true;
        }
        return false;
    }

    @Override
    public Component title() {
        return recipe.getCategory().getName();
    }

    @Override
    public List<ItemStack> outputs() {
        List<ItemStack> outputs = new ArrayList<>();
        for (EmiStack stack : recipe.getOutputs()) {
            ItemStack item = stack.getItemStack();
            if (!item.isEmpty()) outputs.add(item);
        }
        return outputs;
    }

    @Override
    public List<ItemStack> stations() {
        List<ItemStack> stations = new ArrayList<>();
        for (EmiIngredient station : EmiApi.getRecipeManager().getWorkstations(recipe.getCategory())) {
            ItemStack item = EmiBridge.item(station);
            if (!item.isEmpty() && stations.size() < 4) stations.add(item);
        }
        return stations;
    }
}
