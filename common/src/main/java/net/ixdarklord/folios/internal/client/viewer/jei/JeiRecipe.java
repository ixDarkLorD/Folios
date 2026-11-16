package net.ixdarklord.folios.internal.client.viewer.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class JeiRecipe<T> implements ViewerRecipe {
    private final IJeiRuntime runtime;
    private final IRecipeLayoutDrawable<T> drawable;
    private long ticked;
    private @Nullable Optional<Object> recipeBookmark;
    private Method toggle;
    private Method contains;

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
        drawable.drawRecipe(graphics, mouseX, mouseY);
        drawable.drawOverlays(graphics, mouseX, mouseY);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        Optional<ItemStack> under = drawable.getItemStackUnderMouse(mouseX, mouseY);
        if (under.isEmpty()) return false;
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, under.get()));
        return true;
    }

    @Override
    public ItemStack under(int mouseX, int mouseY) {
        return drawable.getItemStackUnderMouse(mouseX, mouseY).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean canBookmark() {
        return this.recipeBookmark() != null || this.itemBookmark() != null;
    }

    @Override
    public boolean bookmarked() {
        Object recipe = this.recipeBookmark();
        if (recipe != null) {
            try {
                return (boolean) contains.invoke(runtime.getBookmarkManager(), recipe);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                recipeBookmark = Optional.empty();
            }
        }
        ITypedIngredient<ItemStack> item = this.itemBookmark();
        return item != null && runtime.getBookmarkManager().contains(item);
    }

    @Override
    public void toggleBookmark() {
        Object recipe = this.recipeBookmark();
        if (recipe != null) {
            try {
                toggle.invoke(runtime.getBookmarkManager(), recipe);
                return;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                recipeBookmark = Optional.empty();
            }
        }
        ITypedIngredient<ItemStack> item = this.itemBookmark();
        if (item == null) return;
        if (runtime.getBookmarkManager().contains(item)) runtime.getBookmarkManager().remove(item);
        else runtime.getBookmarkManager().add(item);
    }

    private @Nullable Object recipeBookmark() {
        if (recipeBookmark == null) {
            Object made = null;
            try {
                ClassLoader loader = runtime.getClass().getClassLoader();
                Class<?> service = Class.forName("mezz.jei.common.transfer.RecipeTransferService", true, loader);
                Class<?> bookmark = Class.forName("mezz.jei.gui.bookmarks.RecipeBookmark", true, loader);
                Class<?> any = Class.forName("mezz.jei.gui.bookmarks.IBookmark", true, loader);
                Object transfers = service.getConstructor(IRecipeTransferManager.class).newInstance(runtime.getRecipeTransferManager());
                made = bookmark.getMethod("create", IRecipeLayoutDrawable.class, IIngredientManager.class, service)
                        .invoke(null, drawable, runtime.getIngredientManager(), transfers);
                Class<?> list = runtime.getBookmarkManager().getClass();
                toggle = list.getMethod("toggleBookmark", any);
                contains = list.getMethod("contains", any);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                made = null;
            }
            recipeBookmark = Optional.ofNullable(made);
        }
        return recipeBookmark.orElse(null);
    }

    private @Nullable ITypedIngredient<ItemStack> itemBookmark() {
        List<ItemStack> outputs = this.outputs();
        if (outputs.isEmpty()) return null;
        return runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, outputs.getFirst()).orElse(null);
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
        return runtime.getRecipeManager().createCraftingStationLookup(drawable.getRecipeCategory().getRecipeType()).getItemStack().limit(4).toList();
    }
}
