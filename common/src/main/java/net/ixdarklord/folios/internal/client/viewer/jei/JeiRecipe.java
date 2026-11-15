package net.ixdarklord.folios.internal.client.viewer.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class JeiRecipe<T> implements ViewerRecipe {
    private final IJeiRuntime runtime;
    private final IRecipeLayoutDrawable<T> drawable;
    private long ticked;

    JeiRecipe(IJeiRuntime runtime, IRecipeLayoutDrawable<T> drawable) {
        this.runtime = runtime;
        this.drawable = drawable;
        drawable.setPosition(0, 0);
        drawable.setPosition(-drawable.getRectWithBorder().getX(), -drawable.getRectWithBorder().getY());
    }

    @Override
    public int width() {
        return drawable.getRectWithBorder().getWidth();
    }

    @Override
    public int height() {
        return drawable.getRectWithBorder().getHeight();
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        long now = Util.getMillis();
        if (now - ticked >= 50) {
            drawable.tick();
            ticked = now;
        }
        GuiGraphics raw = graphics.raw();
        drawable.drawRecipe(raw, mouseX, mouseY);
        drawable.drawOverlays(raw, mouseX, mouseY);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        Optional<ItemStack> under = drawable.getItemStackUnderMouse(mouseX, mouseY);
        if (under.isEmpty()) return false;
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, under.get()));
        return true;
    }

    @Override
    public Component title() {
        return drawable.getRecipeCategory().getTitle();
    }

    @Override
    public List<ItemStack> outputs() {
        List<ItemStack> outputs = new ArrayList<>();
        for (IRecipeSlotView slot : drawable.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT)) slot.getDisplayedItemStack().ifPresent(outputs::add);
        return outputs;
    }

    @Override
    public List<ItemStack> stations() {
        return runtime.getRecipeManager().createRecipeCatalystLookup(drawable.getRecipeCategory().getRecipeType()).get(VanillaTypes.ITEM_STACK).limit(4).toList();
    }
}
