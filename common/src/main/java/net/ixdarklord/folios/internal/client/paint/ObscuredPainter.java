package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ObscuredPainter implements FoliosPainter {
    private static final ResourceLocation RUNES = ResourceLocation.withDefaultNamespace("alt");

    private final FoliosPainter painter;
    private final int ink;
    private final int shade;
    private final float opacity;

    public ObscuredPainter(FoliosPainter painter) {
        this(painter, 1);
    }

    public ObscuredPainter(FoliosPainter painter, float opacity) {
        this.painter = painter;
        this.opacity = opacity;
        FoliosPalette palette = painter.palette();
        this.ink = FoliosPalette.alpha(palette.muted(), faded(0x40));
        this.shade = faded(0x24) << 24;
    }

    private int faded(float alpha) {
        return Math.min(0xFF, Math.round(alpha * opacity));
    }

    @Override
    public int pageWidth() {
        return painter.pageWidth();
    }

    @Override
    public int pageHeight() {
        return painter.pageHeight();
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
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        painter.fill(x0, y0, x1, y1, FoliosPalette.alpha(argb, faded((argb >>> 24) * 0.3F)));
    }

    @Override
    public void text(FormattedCharSequence text, int x, int y, int argb) {
        FormattedCharSequence runes = sink -> text.accept((index, style, codePoint) -> sink.accept(index, style.withFont(RUNES), codePoint));
        painter.text(runes, x, y, FoliosPalette.alpha(ink, Math.round((ink >>> 24) * ((argb >>> 24) / 255.0F))));
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        this.text(text.getVisualOrderText(), x, y, argb);
    }

    @Override
    public void texture(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        painter.texture(texture, x, y, width, height, u0, v0, u1, v1, FoliosPalette.alpha(argb, faded((argb >>> 24) * 0.2F)));
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        this.silhouette(stack, x, y, -1);
    }

    @Override
    public void item(ItemStack stack, int x, int y, int argb) {
        this.silhouette(stack, x, y, argb);
    }

    @Override
    public void silhouette(ItemStack stack, int x, int y, int argb) {
        int tint = FoliosPalette.alpha(shade, Math.round((shade >>> 24) * ((argb >>> 24) / 255.0F)));
        painter.silhouette(stack, x, y, tint);
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
        painter.scene(viewport, x, y, width, height, u0, v0, u1, v1, shade);
    }

    @Override
    public void clip(int x, int y, int width, int height, Runnable draw) {
        painter.clip(x, y, width, height, draw);
    }

    @Override
    public void push() {
        painter.push();
    }

    @Override
    public void pop() {
        painter.pop();
    }

    @Override
    public void translate(float x, float y) {
        painter.translate(x, y);
    }

    @Override
    public void scale(float scale) {
        painter.scale(scale);
    }

    @Override
    public boolean hovered(int x, int y, int width, int height) {
        return false;
    }

    @Override
    public boolean link(int x, int y, int width, int height, String target) {
        return false;
    }

    @Override
    public boolean action(int x, int y, int width, int height, @Nullable Component tooltip, Runnable onPress) {
        return false;
    }

    @Override
    public boolean drag(int x, int y, int width, int height, Drag drag) {
        return false;
    }

    @Override
    public boolean scroll(int x, int y, int width, int height, Scroll scroll) {
        return false;
    }

    @Override
    public void tooltip(Component text) {}

    @Override
    public void tooltip(List<Component> lines) {}

    @Override
    public void tooltip(ItemStack stack) {}
}
