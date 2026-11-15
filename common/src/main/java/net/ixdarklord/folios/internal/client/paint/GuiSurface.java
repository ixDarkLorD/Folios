package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;

public interface GuiSurface {
    boolean gui(int x, int y, int width, int height, Draw draw);

    @FunctionalInterface
    interface Draw {
        void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY);
    }

    static boolean gui(FoliosPainter painter, int x, int y, int width, int height, Draw draw) {
        return painter instanceof GuiSurface surface && surface.gui(x, y, width, height, draw);
    }
}
