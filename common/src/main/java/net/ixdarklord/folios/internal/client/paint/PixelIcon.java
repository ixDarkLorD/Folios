package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public record PixelIcon(Identifier texture, int width, int height, int frames) {
    public static PixelIcon of(String name, int width, int height) {
        return of(name, width, height, 1);
    }

    public static PixelIcon of(String name, int width, int height, int frames) {
        return new PixelIcon(Folios.rl("textures/gui/icons/" + name + ".png"), width, height, frames);
    }

    public void draw(FoliosPainter painter, int x, int y, int color) {
        this.draw(painter, x, y, 0, color);
    }

    public void draw(FoliosPainter painter, int x, int y, int frame, int color) {
        float v0 = frame / (float) frames;
        painter.texture(texture, x, y, width, height, 0, v0, 1, v0 + 1.0F / frames, color);
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int color) {
        this.draw(graphics, x, y, 0, color);
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int frame, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, frame * height, width, height, width, height, width, height * frames, color);
    }

    private static final float RAINBOW_SHOWS = 1.5F;
    private static final float RAINBOW_RESTS = 2.0F;
    private static final float RAINBOW_FADE = 0.3F;

    public void rainbow(FoliosPainter painter, int x, int y, float seconds, int base) {
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

    private static float rainbowStrength(float seconds) {
        float into = seconds % (RAINBOW_SHOWS + RAINBOW_RESTS);
        if (into >= RAINBOW_SHOWS) return 0;
        return Math.min(1, Math.min(into, RAINBOW_SHOWS - into) / RAINBOW_FADE);
    }

    private static int hue(float seconds, int diagonal, int base, float strength) {
        int rainbow = 0xFF000000 | net.minecraft.util.Mth.hsvToRgb((seconds * 0.35F + diagonal * 0.04F) % 1.0F, 0.65F, 1.0F);
        return net.ixdarklord.folios.api.client.page.FoliosPalette.mix(base, rainbow, strength);
    }

    public int sweepFrame(float seconds, float period, float length) {
        float into = seconds % period;
        return into < length ? Math.min(frames - 1, (int) (into / length * frames)) : -1;
    }
}
