package net.ixdarklord.folios.internal.client.viewer.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class EmiRecipeView implements ViewerRecipe {
    private final EmiRecipe recipe;

    EmiRecipeView(EmiRecipe recipe) {
        this.recipe = recipe;
    }

    boolean shows(EmiRecipe other) {
        return recipe == other;
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
}
