package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.internal.client.GuideSession;
import net.ixdarklord.folios.internal.mixin.client.GuiGraphicsInvoker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// A page drawn on a screen: page pixels are GUI pixels, from the page's top left at (x, y), scaled by `scale`.
public final class ScreenPainter extends AbstractPainter {
    private static final int UV = 1 << 14;
    private final GuiGraphicsExtractor graphics;

    public ScreenPainter(GuiGraphicsExtractor graphics, float x, float y, float scale, GuidePalette palette, GuideSession session, Navigator navigator,
                         float time, float pointerX, float pointerY, List<Hit> hits) {
        super(palette, session, navigator, time, pointerX, pointerY, hits);
        this.graphics = graphics;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
    }

    /** Ends the page: undoes the painter's place on the screen. */
    public void end() {
        graphics.pose().popMatrix();
    }

    @Override
    public boolean flat() {
        return true;
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        graphics.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(FormattedCharSequence text, int x, int y, int argb) {
        graphics.text(font, text, x, y, argb, false);
    }

    @Override
    public void texture(Identifier texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u0 * UV, v0 * UV, width, height,
                Math.round((u1 - u0) * UV), Math.round((v1 - v0) * UV), UV, UV, argb);
    }

    // Items go in a pass of their own over the flat things of their layer; a new layer is over both.
    @Override
    public void above(Runnable draw) {
        graphics.nextStratum();
        draw.run();
    }

    @Override
    protected void drawItem(ItemStack stack, int x, int y, int argb) {
        TintedGuiItems.draw(argb, () -> graphics.fakeItem(stack, x, y));
        // The count and bars can't fade: shown once the item is mostly there.
        if ((argb >>> 24) >= 160) graphics.itemDecorations(font, stack, x, y);
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height) {
        viewport.use();
        GpuTextureView view = SceneViewportManager.view(viewport);
        // Drawn to at the start of the next frame the first time; and upside down, as every texture drawn to is.
        if (view != null) {
            graphics.blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + width, y + height, 0.0F, 1.0F, 1.0F, 0.0F);
        }
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float u1) {
        viewport.use();
        GpuTextureView view = SceneViewportManager.view(viewport);
        if (view != null) {
            graphics.blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + width, y + height, u0, u1, 1.0F, 0.0F);
        }
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        viewport.use();
        GpuTextureView view = SceneViewportManager.view(viewport);
        if (view != null) {
            ((GuiGraphicsInvoker) graphics).folios$blit(RenderPipelines.GUI_TEXTURED, view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                    x, y, x + width, y + height, u0, u1, 1 - v0, 1 - v1, argb);
        }
    }

    @Override
    protected void pushed() {
        graphics.pose().pushMatrix();
    }

    @Override
    protected void popped() {
        graphics.pose().popMatrix();
    }

    @Override
    protected void translated(float x, float y) {
        graphics.pose().translate(x, y);
    }

    @Override
    protected void scaled(float factor) {
        graphics.pose().scale(factor, factor);
    }
}
