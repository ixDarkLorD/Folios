package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.FoliosReveal;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RevealPainter implements FoliosPainter {
    private static final int CELL = 6;
    private static final int PICTURE_CELL = 12;
    private static final ResourceLocation RUNES = ResourceLocation.withDefaultNamespace("alt");

    private final FoliosPainter painter;

    @Override
    public int pageWidth() {
        return painter.pageWidth();
    }

    @Override
    public int pageHeight() {
        return painter.pageHeight();
    }
    private final FoliosReveal effect;
    private final float progress;
    private final float pageX;
    private final Deque<float[]> stack = new ArrayDeque<>();
    private float offsetX;
    private float offsetY;
    private float scale = 1;

    private RevealPainter(FoliosPainter painter, FoliosReveal effect, float progress, float pageX) {
        this.painter = painter;
        this.effect = effect;
        this.progress = progress;
        this.pageX = pageX;
    }

    public static void draw(FoliosPainter painter, FoliosPageElement.Laid page, @Nullable FoliosReveal effect, float progress, float pageX) {
        page.draw(of(painter, effect, progress, pageX));
        if (effect != null && progress > 0 && progress < 1) above(painter, () -> effect.overlay(new EdgeFadePainter(painter, 0, 0, 1), progress, painter.time(), pageX));
    }

    public static FoliosPainter of(FoliosPainter painter, @Nullable FoliosReveal effect, float progress, float pageX) {
        return effect == null || progress >= 1 ? painter : new RevealPainter(painter, effect, progress, pageX);
    }

    private FoliosReveal.Look look(float x, float y) {
        return effect.look(progress, pageX + offsetX + x * scale, offsetY + y * scale);
    }

    private static int faded(int argb, float opacity) {
        int alpha = Math.round((argb >>> 24) * Math.max(0, Math.min(1, opacity)));
        return FoliosPalette.alpha(argb, alpha);
    }

    private static int shaded(int argb, FoliosReveal.Look look) {
        int tint = look.tint();
        if ((tint >>> 24) != 0) argb = FoliosPalette.alpha(FoliosPalette.mix(argb | 0xFF000000, tint | 0xFF000000, (tint >>> 24) / 255.0F), argb >>> 24);
        return faded(argb, look.opacity());
    }

    private static boolean same(FoliosReveal.Look a, FoliosReveal.Look b) {
        return Math.abs(a.opacity() - b.opacity()) < 0.02F && a.tint() == b.tint() && Math.abs(a.dx() - b.dx()) < 0.5F && Math.abs(a.dy() - b.dy()) < 0.5F;
    }

    private boolean even(float x0, float y0, float x1, float y1) {
        FoliosReveal.Look middle = this.look((x0 + x1) / 2, (y0 + y1) / 2);
        return same(middle, this.look(x0, y0)) && same(middle, this.look(x1, y0)) && same(middle, this.look(x0, y1)) && same(middle, this.look(x1, y1));
    }

    private void around(float x, float y, float width, float height) {
        effect.around(new EdgeFadePainter(painter, offsetX, offsetY, scale), progress, painter.time(), x, y, width, height,
                pageX + offsetX + (x + width / 2) * scale, offsetY + (y + height / 2) * scale);
    }

    private void aroundItem(int x, int y) {
        above(painter, () -> this.around(x, y, 16, 16));
    }

    private static void above(FoliosPainter painter, Runnable draw) {
        if (painter instanceof AbstractPainter drawing) drawing.above(draw);
        else draw.run();
    }

    private void moved(FoliosReveal.Look look, Runnable draw) {
        if (look.dx() == 0 && look.dy() == 0) {
            draw.run();
            return;
        }
        painter.push();
        painter.translate(look.dx() / scale, look.dy() / scale);
        draw.run();
        painter.pop();
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        if (this.even(x0, y0, x1, y1)) {
            FoliosReveal.Look look = this.look((x0 + x1) / 2.0F, (y0 + y1) / 2.0F);
            if (look.opacity() > 0.01F) this.moved(look, () -> painter.fill(x0, y0, x1, y1, shaded(argb, look)));
            return;
        }
        for (int y = y0; y < y1; y += CELL) {
            for (int x = x0; x < x1; x += CELL) {
                int left = x, top = y, right = Math.min(x1, x + CELL), bottom = Math.min(y1, y + CELL);
                FoliosReveal.Look look = this.look((left + right) / 2.0F, (top + bottom) / 2.0F);
                if (look.opacity() > 0.01F) this.moved(look, () -> painter.fill(left, top, right, bottom, shaded(argb, look)));
            }
        }
    }

    @Override
    public void text(FormattedCharSequence text, int x, int y, int argb) {
        Font font = painter.font();
        int lineWidth = font.width(text);
        if (this.even(x, y, x + lineWidth, y + 8)) {
            FoliosReveal.Look look = this.look(x + lineWidth / 2.0F, y + 4);
            if (look.opacity() < 0.1F) return;
            if (look.runes() <= 0.01F) {
                int colour = shaded(argb, look);
                this.moved(look, () -> painter.text(text, x, y, colour));
                this.around(x, y, lineWidth, 8);
                return;
            }
        }
        int flicker = (int) (painter.time() * 12);
        float[] at = {x};
        text.accept((index, style, codePoint) -> {
            FormattedCharSequence letter = FormattedCharSequence.codepoint(codePoint, style);
            float left = at[0];
            int width = font.width(letter);
            at[0] += width;
            if (codePoint == ' ') return true;
            FoliosReveal.Look look = this.look(left + width / 2.0F, y + 4);
            if (look.opacity() < 0.1F) return true;
            boolean rune = look.runes() > 0.01F && FoliosReveal.noise(Math.round(left) * 31 + y * 131 + flicker * 104729) < look.runes();
            FormattedCharSequence shown = rune ? FormattedCharSequence.codepoint(codePoint, style.withFont(RUNES)) : letter;
            int colour = shaded(argb, look);
            this.moved(look, () -> {
                painter.push();
                painter.translate(left - x, 0);
                painter.text(shown, x, y, colour);
                painter.pop();
            });
            this.around(left, y, width, 8);
            return true;
        });
    }

    @Override
    public void texture(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        if (this.even(x, y, x + width, y + height)) {
            FoliosReveal.Look look = this.look(x + width / 2.0F, y + height / 2.0F);
            if (look.opacity() > 0.01F) this.moved(look, () -> painter.texture(texture, x, y, width, height, u0, v0, u1, v1, shaded(argb, look)));
            if (width <= 24 && height <= 24) this.around(x, y, width, height);
            return;
        }
        for (int cy = 0; cy < height; cy += PICTURE_CELL) {
            for (int cx = 0; cx < width; cx += PICTURE_CELL) {
                int w = Math.min(PICTURE_CELL, width - cx), h = Math.min(PICTURE_CELL, height - cy);
                FoliosReveal.Look look = this.look(x + cx + w / 2.0F, y + cy + h / 2.0F);
                if (look.opacity() <= 0.01F) continue;
                float cu0 = u0 + (u1 - u0) * cx / width, cu1 = u0 + (u1 - u0) * (cx + w) / width;
                float cv0 = v0 + (v1 - v0) * cy / height, cv1 = v0 + (v1 - v0) * (cy + h) / height;
                int px = x + cx, py = y + cy;
                this.moved(look, () -> painter.texture(texture, px, py, w, h, cu0, cv0, cu1, cv1, shaded(argb, look)));
            }
        }
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        this.item(stack, x, y, -1);
    }

    @Override
    public void item(ItemStack stack, int x, int y, int argb) {
        FoliosReveal.Look look = this.look(x + 8, y + 8);
        if (look.opacity() > 0.02F) this.moved(look, () -> painter.item(stack, x, y, shaded(argb, look)));
        this.aroundItem(x, y);
    }

    @Override
    public void silhouette(ItemStack stack, int x, int y, int argb) {
        FoliosReveal.Look look = this.look(x + 8, y + 8);
        if (look.opacity() > 0.02F) this.moved(look, () -> painter.silhouette(stack, x, y, shaded(argb, look)));
        this.aroundItem(x, y);
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height) {
        this.scene(viewport, x, y, width, height, 0, 0, 1, 1, -1);
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float u1) {
        this.scene(viewport, x, y, width, height, u0, 0, u1, 1, -1);
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        if (this.even(x, y, x + width, y + height)) {
            FoliosReveal.Look look = this.look(x + width / 2.0F, y + height / 2.0F);
            if (look.opacity() > 0.01F) this.moved(look, () -> painter.scene(viewport, x, y, width, height, u0, v0, u1, v1, shaded(argb, look)));
            return;
        }
        for (int cy = 0; cy < height; cy += PICTURE_CELL) {
            for (int cx = 0; cx < width; cx += PICTURE_CELL) {
                int w = Math.min(PICTURE_CELL, width - cx), h = Math.min(PICTURE_CELL, height - cy);
                FoliosReveal.Look look = this.look(x + cx + w / 2.0F, y + cy + h / 2.0F);
                if (look.opacity() <= 0.01F) continue;
                float cu0 = u0 + (u1 - u0) * cx / width, cu1 = u0 + (u1 - u0) * (cx + w) / width;
                float cv0 = v0 + (v1 - v0) * cy / height, cv1 = v0 + (v1 - v0) * (cy + h) / height;
                int px = x + cx, py = y + cy;
                this.moved(look, () -> painter.scene(viewport, px, py, w, h, cu0, cv0, cu1, cv1, shaded(argb, look)));
            }
        }
    }

    @Override
    public void clip(int x, int y, int width, int height, Runnable draw) {
        painter.clip(x, y, width, height, draw);
    }

    @Override
    public void push() {
        stack.push(new float[]{offsetX, offsetY, scale});
        painter.push();
    }

    @Override
    public void pop() {
        float[] last = stack.pop();
        offsetX = last[0];
        offsetY = last[1];
        scale = last[2];
        painter.pop();
    }

    @Override
    public void translate(float x, float y) {
        offsetX += x * scale;
        offsetY += y * scale;
        painter.translate(x, y);
    }

    @Override
    public void scale(float factor) {
        scale *= factor;
        painter.scale(factor);
    }

    @Override
    public Font font() {
        return painter.font();
    }

    @Override
    public FoliosPalette palette() {
        return painter.palette();
    }

    @Override
    public float time() {
        return painter.time();
    }

    @Override
    public boolean flat() {
        return painter.flat();
    }

    @Override
    public boolean hovered(int x, int y, int width, int height) {
        return painter.hovered(x, y, width, height);
    }

    @Override
    public boolean link(int x, int y, int width, int height, String target) {
        return painter.link(x, y, width, height, target);
    }

    @Override
    public boolean action(int x, int y, int width, int height, @Nullable Component tooltip, Runnable onPress) {
        return painter.action(x, y, width, height, tooltip, onPress);
    }

    @Override
    public boolean drag(int x, int y, int width, int height, Drag drag) {
        return painter.drag(x, y, width, height, drag);
    }

    @Override
    public boolean scroll(int x, int y, int width, int height, Scroll scroll) {
        return painter.scroll(x, y, width, height, scroll);
    }

    @Override
    public void tooltip(Component text) {
        painter.tooltip(text);
    }

    @Override
    public void tooltip(java.util.List<Component> lines) {
        painter.tooltip(lines);
    }

    @Override
    public void tooltip(ItemStack stack) {
        painter.tooltip(stack);
    }
}
