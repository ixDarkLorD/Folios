package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.folios.internal.compat.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/**
 * One of the books' small pixel-art icons: a white image (its softer pixels part see-through), drawn at its own size,
 * a pixel to a pixel, tinted with the colour it is drawn in. Read from assets/folios/textures/gui/icons/, so a
 * resource pack can redraw any of them (kept the same size). An icon with frames is a flipbook: its frames stacked
 * top to bottom in the one image, each {@code width} by {@code height}.
 */
public record PixelIcon(ResourceLocation texture, int width, int height, int frames) {
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
        this.draw(graphics, x, y, 0, color);
    }

    /** One of its frames, straight to the screen. */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int frame, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, frame * height, width, height, width, height, width, height * frames, color);
    }

    /** How long the rainbow shows, and how long it rests between showings, in seconds; and how long it fades in and out. */
    private static final float RAINBOW_SHOWS = 1.5F;
    private static final float RAINBOW_RESTS = 2.0F;
    private static final float RAINBOW_FADE = 0.3F;

    /**
     * Drawn in {@code base}, with a rainbow glinting over it now and then: for a second and a half the rainbow fades in,
     * each pixel its own hue, the hues running across it diagonally from its top left to its bottom right and cycling,
     * then fades out, and two seconds later shows again.
     */
    public void rainbow(GuidePainter painter, int x, int y, float seconds, int base) {
        float strength = rainbowStrength(seconds);
        if (strength <= 0) {
            this.draw(painter, x, y, base);
            return;
        }
        float v = 1.0F / (height * frames);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                painter.texture(texture, x + column, y + row, 1, 1, column / (float) width, row * v, (column + 1) / (float) width, (row + 1) * v,
                        hue(seconds, column + row, base, strength));
            }
        }
    }

    /** The same, straight to the screen. */
    public void rainbow(GuiGraphicsExtractor graphics, int x, int y, float seconds, int base) {
        float strength = rainbowStrength(seconds);
        if (strength <= 0) {
            this.draw(graphics, x, y, base);
            return;
        }
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + column, y + row, column, row, 1, 1, width, height * frames, hue(seconds, column + row, base, strength));
            }
        }
    }

    // How much of the rainbow shows at a time, 0 to 1: rising and falling at the ends of each showing, none between.
    private static float rainbowStrength(float seconds) {
        float into = seconds % (RAINBOW_SHOWS + RAINBOW_RESTS);
        if (into >= RAINBOW_SHOWS) return 0;
        return Math.min(1, Math.min(into, RAINBOW_SHOWS - into) / RAINBOW_FADE);
    }

    // The colour of a diagonal of the rainbow (a pixel's column plus its row) at a time, over the base as strongly as it shows.
    private static int hue(float seconds, int diagonal, int base, float strength) {
        int rainbow = 0xFF000000 | net.minecraft.util.Mth.hsvToRgb((seconds * 0.35F + diagonal * 0.04F) % 1.0F, 0.65F, 1.0F);
        return net.ixdarklord.folios.api.client.page.GuidePalette.mix(base, rainbow, strength);
    }

    /**
     * The frame of a sweep that plays once every {@code period} seconds, taking {@code length} of them; -1 between
     * sweeps, when nothing is to be drawn.
     */
    public int sweepFrame(float seconds, float period, float length) {
        float into = seconds % period;
        return into < length ? Math.min(frames - 1, (int) (into / length * frames)) : -1;
    }
}

