package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.systems.RenderSystem;

// Items drawn on a screen tinted and faded: 1.21.1 draws a screen's item at once, flushing its buffers before it
// returns, so the shader colour set around the draw tints everything it draws.
public final class TintedGuiItems {
    private TintedGuiItems() {}

    /** Draws what {@code draw} hands the screen tinted by {@code argb}. */
    public static void draw(int argb, Runnable draw) {
        if (argb == -1) {
            draw.run();
            return;
        }
        float[] before = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
        RenderSystem.enableBlend();
        try {
            draw.run();
        } finally {
            RenderSystem.setShaderColor(before[0], before[1], before[2], before[3]);
        }
    }
}
