package net.ixdarklord.folios.internal.client.viewer.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.ixdarklord.folios.internal.client.viewer.ViewerBridge;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class EmiBridge implements ViewerBridge {
    static final String NAME = "emi";

    private final Map<ResourceLocation, EmiRecipeView> made = new HashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ItemStack hovered() {
        return item(EmiApi.getHoveredStack(false).getStack());
    }

    static ItemStack item(@Nullable EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return ItemStack.EMPTY;
        for (EmiStack stack : ingredient.getEmiStacks()) {
            ItemStack item = stack.getItemStack();
            if (!item.isEmpty()) return item;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @Nullable ViewerRecipe recipe(ResourceLocation id) {
        EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(id);
        if (recipe == null) return null;
        EmiRecipeView view = made.get(id);
        if (view == null || !view.shows(recipe)) {
            view = new EmiRecipeView(recipe);
            made.put(id, view);
        }
        return view;
    }
}
