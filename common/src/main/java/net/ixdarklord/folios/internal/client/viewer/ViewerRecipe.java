package net.ixdarklord.folios.internal.client.viewer;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface ViewerRecipe {
    List<ItemStack> outputs();

    default boolean canBookmark() {
        return false;
    }

    default boolean bookmarked() {
        return false;
    }

    default void toggleBookmark() {}
}
