package net.ixdarklord.folios.internal.client.viewer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface ViewerBridge {
    String name();

    ItemStack hovered();

    @Nullable ViewerRecipe recipe(ResourceLocation id);
}
