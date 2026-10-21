package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * One of the books' small pixel-art icons: a white image (its softer pixels part see-through), drawn at its own size,
 * a pixel to a pixel, tinted with the colour it is drawn in. Read from assets/folios/textures/gui/icons/, so a
 * resource pack can redraw any of them (kept the same size). An icon with frames is a flipbook: its frames stacked
 * top to bottom in the one image, each {@code width} by {@code height}.
 */
public record PixelIcon(Identifier texture, int width, int height, int frames) {
    public static PixelIcon of(String name, int width, int height) {
        return of(name, width, height, 1);
    }

    /** A flipbook of {@code frames} frames, stacked top to bottom. */
    public static PixelIcon of(String name, int width, int height, int frames) {
        return new PixelIcon(Folios.rl("textures/gui/icons/" + name + ".png"), width, height, frames);
    }

    /** Draws it with its top left at (x, y), in the colour given. */
    public void draw(GuidePainter painter, int x, int y, int color) {
        this.draw(painter, x, y, 0, color);
    }

    /** Draws one of its frames (0 for the top one). */
    public void draw(GuidePainter painter, int x, int y, int frame, int color) {
        float v0 = frame / (float) frames;
        painter.texture(texture, x, y, width, height, 0, v0, 1, v0 + 1.0F / frames, color);
    }

    /** The same, straight to the screen. */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, width, height, width, height * frames, color);
    }
}
