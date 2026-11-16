package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.minecraft.world.item.ItemStack;

public interface ItemHover {
    void hoverItem(ItemStack stack);

    static void hover(FoliosPainter painter, ItemStack stack) {
        if (painter instanceof ItemHover hover && !stack.isEmpty()) hover.hoverItem(stack);
    }
}
