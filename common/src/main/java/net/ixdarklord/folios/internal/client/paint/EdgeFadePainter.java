package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
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
import java.util.List;

/**
 * What a reveal effect draws over a page, faded out towards the page's edges and its fold, so sparks, smoke, light and
 * runes melt into the paper instead of stopping short at its border. Each thing drawn takes the fade of where its
 * middle is on the page; past the page it isn't drawn.
 */
public final class EdgeFadePainter implements FoliosPainter {
    /** How far in from each edge (page pixels) the fade reaches. */
    private static final float MARGIN = 14;

    private final FoliosPainter painter;

    // The page's size is the painter's it wraps.
    @Override
    public int pageWidth() {
        return painter.pageWidth();
    }

    @Override
    public int pageHeight() {
        return painter.pageHeight();
    }
    private float offsetX;
    private float offsetY;
    private float scale;
    private final Deque<float[]> stack = new ArrayDeque<>();

    /** Wraps a page's painter; (x, y) and {@code scale} are where its own pixels are on the page now. */
    public EdgeFadePainter(FoliosPainter painter, float x, float y, float scale) {
        this.painter = painter;
        this.offsetX = x;
        this.offsetY = y;
        this.scale = scale;
    }

    // How much shows at a point of the painter's pixels: 1 well inside, falling smoothly to 0 at the edges.
    private float fade(float x, float y) {
        float px = offsetX + x * scale;
        float py = offsetY + y * scale;
        float inside = Math.min(Math.min(px, this.pageWidth() - px), Math.min(py, this.pageHeight() - py));
        float t = Math.max(0, Math.min(1, inside / MARGIN));
        return t * t * (3 - 2 * t);
    }

    private static int faded(int argb, float fade) {
        return FoliosPalette.alpha(argb, Math.round((argb >>> 24) * fade));
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        float fade = this.fade((x0 + x1) / 2.0F, (y0 + y1) / 2.0F);
        if (fade > 0.01F) painter.fill(x0, y0, x1, y1, faded(argb, fade));
    }

    @Override
    public void text(FormattedCharSequence text, int x, int y, int argb) {
        float fade = this.fade(x + painter.font().width(text) / 2.0F, y + 4);
        if (fade > 0.01F) painter.text(text, x, y, faded(argb, fade));
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        this.text(text.getVisualOrderText(), x, y, argb);
    }

    @Override
    public void texture(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        float fade = this.fade(x + width / 2.0F, y + height / 2.0F);
        if (fade > 0.01F) painter.texture(texture, x, y, width, height, u0, v0, u1, v1, faded(argb, fade));
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        this.item(stack, x, y, -1);
    }

    @Override
    public void item(ItemStack stack, int x, int y, int argb) {
        float fade = this.fade(x + 8, y + 8);
        if (fade > 0.01F) painter.item(stack, x, y, faded(argb, fade));
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
        float fade = this.fade(x + width / 2.0F, y + height / 2.0F);
        if (fade > 0.01F) painter.scene(viewport, x, y, width, height, u0, v0, u1, v1, faded(argb, fade));
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
    public void scale(float scale) {
        this.scale *= scale;
        painter.scale(scale);
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
    public void tooltip(List<Component> lines) {
        painter.tooltip(lines);
    }

    @Override
    public void tooltip(ItemStack stack) {
        painter.tooltip(stack);
    }
}
