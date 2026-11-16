package net.ixdarklord.folios.internal.client.viewer.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IJeiRuntime;
import net.ixdarklord.folios.internal.client.viewer.ViewerBridge;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class JeiBridge implements ViewerBridge {
    static final String NAME = "jei";
    private static final IScalableDrawable BARE = (graphics, x, y, width, height) -> {};

    private record Found<T>(IRecipeCategory<T> category, T recipe) {}

    private final IJeiRuntime runtime;
    private @Nullable Map<ResourceLocation, Found<?>> index;
    private final Map<ResourceLocation, Optional<ViewerRecipe>> made = new HashMap<>();

    JeiBridge(IJeiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ItemStack hovered() {
        ItemStack listed = runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (listed != null && !listed.isEmpty()) return listed;
        ItemStack marked = runtime.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (marked != null && !marked.isEmpty()) return marked;
        return runtime.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean key(InputConstants.Key key, ItemStack stack) {
        IJeiKeyMappings keys = runtime.getKeyMappings();
        RecipeIngredientRole role;
        if (keys.getShowRecipe().isActiveAndMatches(key)) role = RecipeIngredientRole.OUTPUT;
        else if (keys.getShowUses().isActiveAndMatches(key)) role = RecipeIngredientRole.INPUT;
        else return false;
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, stack));
        return true;
    }

    @Override
    public @Nullable ViewerRecipe recipe(ResourceLocation id) {
        return made.computeIfAbsent(id, key -> Optional.ofNullable(this.make(key))).orElse(null);
    }

    private @Nullable ViewerRecipe make(ResourceLocation id) {
        Found<?> found = this.index().get(id);
        return found == null ? null : this.layout(found);
    }

    private <T> @Nullable ViewerRecipe layout(Found<T> found) {
        IFocusGroup none = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        return runtime.getRecipeManager().createRecipeLayoutDrawable(found.category(), found.recipe(), none, BARE, 0)
                .<ViewerRecipe>map(drawable -> new JeiRecipe<>(runtime, drawable)).orElse(null);
    }

    private Map<ResourceLocation, Found<?>> index() {
        if (index == null) {
            Map<ResourceLocation, Found<?>> found = new HashMap<>();
            runtime.getRecipeManager().createRecipeCategoryLookup().get().forEach(category -> this.add(found, category));
            index = found;
        }
        return index;
    }

    private <T> void add(Map<ResourceLocation, Found<?>> into, IRecipeCategory<T> category) {
        runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).get().forEach(recipe -> {
            ResourceLocation id = category.getRegistryName(recipe);
            if (id != null) into.putIfAbsent(id, new Found<>(category, recipe));
        });
    }
}
