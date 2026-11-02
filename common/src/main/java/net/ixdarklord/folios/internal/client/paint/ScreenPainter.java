package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.coolcatcanvas.internal.client.viewport.SceneViewportManager;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.ixdarklord.folios.internal.client.screen.BookCache;
import net.ixdarklord.folios.internal.mixin.client.GuiGraphicsInvoker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public final class ScreenPainter extends AbstractPainter {
    private static final int UV = 1 << 14;
    private final GuiGraphicsExtractor graphics;

    public ScreenPainter(GuiGraphicsExtractor graphics, float x, float y, float scale, FoliosPalette palette, FoliosSession session, Navigator navigator,
                         float time, float pointerX, float pointerY, List<Hit> hits) {
        super(palette, session, navigator, time, pointerX, pointerY, hits);
        this.graphics = graphics;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
    }

    public void end() {
        graphics.pose().popMatrix();
    }

    @Override
    public boolean flat() {
        return true;
    }

    private static int[] recorded = new int[1 << 12];
    private int recording = -1;

    public void baked(String site, Runnable draw) {
        recording = 0;
        int count;
        try {
            draw.run();
        } finally {
            count = recording;
            recording = -1;
        }
        BakedFills.Site baked = BakedFills.site(site, recorded, count, true);
        if (baked != null) {
            if (baked.region != null) {
                ScreenAtlas.draw(graphics, baked.region, baked.x0, baked.y0, baked.x0 + baked.width, baked.y0 + baked.height, 0, 0, 1, 1, 0xFFFFFFFF);
            } else {
                this.texture(baked.id, baked.x0, baked.y0, baked.width, baked.height, 0, 0, 1, 1, 0xFFFFFFFF);
            }
            return;
        }
        for (int at = 0; at < count; at += 5) ScreenAtlas.fill(graphics, recorded[at], recorded[at + 1], recorded[at + 2], recorded[at + 3], recorded[at + 4]);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (recording >= 0) {
            if (recording + 5 > recorded.length) recorded = Arrays.copyOf(recorded, recorded.length * 2);
            recorded[recording++] = x0;
            recorded[recording++] = y0;
            recorded[recording++] = x1;
            recorded[recording++] = y1;
            recorded[recording++] = argb;
            return;
        }
        ScreenAtlas.fill(graphics, x0, y0, x1, y1, argb);
    }

    @Override
    public void text(FormattedCharSequence text, int x, int y, int argb) {
        graphics.text(font, text, x, y, argb, false);
    }

    @Override
    public void texture(Identifier texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        if (ScreenAtlas.blit(graphics, texture, x, y, x + width, y + height, u0, v0, u1, v1, argb)) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u0 * UV, v0 * UV, width, height,
                Math.round((u1 - u0) * UV), Math.round((v1 - v0) * UV), UV, UV, argb);
    }

    @Override
    public void above(Runnable draw) {
        graphics.nextStratum();
        draw.run();
    }

    @Override
    protected void drawItem(ItemStack stack, int x, int y, int argb) {
        ScaledGuiItems.sharp(() -> TintedGuiItems.draw(argb, () -> graphics.fakeItem(stack, x, y)));
        if ((argb >>> 24) >= 160) graphics.itemDecorations(font, stack, x, y);
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height) {
        viewport.use();
        BookCache.markDynamic();
        GpuTextureView view = SceneViewportManager.view(viewport);
        if (view != null) {
            graphics.blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + width, y + height, 0.0F, 1.0F, 1.0F, 0.0F);
        }
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float u1) {
        viewport.use();
        BookCache.markDynamic();
        GpuTextureView view = SceneViewportManager.view(viewport);
        if (view != null) {
            graphics.blit(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + width, y + height, u0, u1, 1.0F, 0.0F);
        }
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        viewport.use();
        BookCache.markDynamic();
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
    public void clip(int x, int y, int width, int height, Runnable draw) {
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            draw.run();
        } finally {
            graphics.disableScissor();
        }
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
