package net.ixdarklord.folios.internal.client.viewer.rei;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class ReiRecipe implements ViewerRecipe {
    private final Display display;

    private ReiRecipe(Display display) {
        this.display = display;
    }

    static @Nullable ReiRecipe of(Display display) {
        return CategoryRegistry.getInstance().tryGet(display.getCategoryIdentifier()).isPresent() ? new ReiRecipe(display) : null;
    }

    @Override
    public List<ItemStack> outputs() {
        List<ItemStack> stacks = new ArrayList<>();
        for (EntryIngredient ingredient : display.getOutputEntries()) {
            for (EntryStack<?> entry : ingredient) {
                ItemStack stack = ReiBridge.item(entry);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                    break;
                }
            }
        }
        return stacks;
    }
}
