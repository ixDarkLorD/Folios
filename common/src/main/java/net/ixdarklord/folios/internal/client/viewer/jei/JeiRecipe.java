package net.ixdarklord.folios.internal.client.viewer.jei;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class JeiRecipe<T> implements ViewerRecipe {
    private final IJeiRuntime runtime;
    private final IRecipeLayoutDrawable<T> drawable;
    private @Nullable Optional<Object> recipeBookmark;
    private Object list;
    private Method toggle;
    private Method contains;

    JeiRecipe(IJeiRuntime runtime, IRecipeLayoutDrawable<T> drawable) {
        this.runtime = runtime;
        this.drawable = drawable;
    }

    @Override
    public boolean canBookmark() {
        return this.recipeBookmark() != null;
    }

    @Override
    public boolean bookmarked() {
        Object recipe = this.recipeBookmark();
        if (recipe == null) return false;
        try {
            return (boolean) contains.invoke(list, recipe);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            recipeBookmark = Optional.empty();
            return false;
        }
    }

    @Override
    public void toggleBookmark() {
        Object recipe = this.recipeBookmark();
        if (recipe == null) return;
        try {
            toggle.invoke(list, recipe);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            recipeBookmark = Optional.empty();
        }
    }

    private @Nullable Object recipeBookmark() {
        if (recipeBookmark == null) {
            Object made = null;
            try {
                ClassLoader loader = runtime.getClass().getClassLoader();
                Class<?> service = Class.forName("mezz.jei.common.transfer.RecipeTransferService", true, loader);
                Class<?> bookmark = Class.forName("mezz.jei.gui.bookmarks.RecipeBookmark", true, loader);
                Class<?> any = Class.forName("mezz.jei.gui.bookmarks.IBookmark", true, loader);
                Object overlay = runtime.getBookmarkOverlay();
                Field field = overlay.getClass().getDeclaredField("bookmarkList");
                field.setAccessible(true);
                list = field.get(overlay);
                Object transfers = service.getConstructor(IRecipeTransferManager.class).newInstance(runtime.getRecipeTransferManager());
                made = bookmark.getMethod("create", IRecipeLayoutDrawable.class, IIngredientManager.class, service)
                        .invoke(null, drawable, runtime.getIngredientManager(), transfers);
                toggle = list.getClass().getMethod("toggleBookmark", any);
                contains = list.getClass().getMethod("contains", any);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                made = null;
            }
            recipeBookmark = Optional.ofNullable(made);
        }
        return recipeBookmark.orElse(null);
    }

    @Override
    public List<ItemStack> outputs() {
        List<ItemStack> outputs = new ArrayList<>();
        for (IRecipeSlotView slot : drawable.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT)) slot.getDisplayedItemStack().ifPresent(outputs::add);
        return outputs;
    }
}
