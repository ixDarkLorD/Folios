package net.ixdarklord.folios.internal.client.viewer;

import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface ViewerRecipe {
    int width();

    int height();

    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    boolean click(int mouseX, int mouseY);

    Component title();

    List<ItemStack> outputs();

    List<ItemStack> stations();

    default boolean followsPose() {
        return true;
    }

    default void drawAt(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        this.draw(graphics, mouseX - x, mouseY - y);
    }
}
