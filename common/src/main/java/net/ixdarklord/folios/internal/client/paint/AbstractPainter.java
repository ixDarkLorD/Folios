package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public abstract class AbstractPainter implements FoliosPainter {
    public interface Navigator {
        void follow(String link);

        void open(FoliosBook.Location location);

        FoliosBook.@Nullable Location here();
    }

    public record Hit(float x0, float y0, float x1, float y1, Runnable action, @Nullable Drag drag, @Nullable Scroll scroll) {
        public Hit(float x0, float y0, float x1, float y1, Runnable action) {
            this(x0, y0, x1, y1, action, null, null);
        }

        public Hit(float x0, float y0, float x1, float y1, Runnable action, @Nullable Drag drag) {
            this(x0, y0, x1, y1, action, drag, null);
        }

        public static @Nullable Scroll scrollAt(List<Hit> hits, double x, double y) {
            for (int index = hits.size() - 1; index >= 0; index--) {
                Hit hit = hits.get(index);
                if (hit.scroll != null && hit.contains(x, y)) return hit.scroll;
            }
            return null;
        }

        public boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    protected final Font font = Minecraft.getInstance().font;
    protected final FoliosPalette palette;
    protected final FoliosSession session;
    protected final Navigator navigator;
    private final float time;
    private final float pointerX;
    private final float pointerY;
    private final List<Hit> hits;
    private final Deque<float[]> stack = new ArrayDeque<>();
    private float offsetX;
    private float offsetY;
    private float scale = 1;
    private @Nullable List<Component> tooltip;

    @Override
    public int pageWidth() {
        return session.layout().pageWidth();
    }

    @Override
    public int pageHeight() {
        return session.layout().pageHeight();
    }

    protected AbstractPainter(FoliosPalette palette, FoliosSession session, Navigator navigator, float time, float pointerX, float pointerY, List<Hit> hits) {
        this.palette = palette;
        this.session = session;
        this.navigator = navigator;
        this.time = time;
        this.pointerX = pointerX;
        this.pointerY = pointerY;
        this.hits = hits;
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public FoliosPalette palette() {
        return palette;
    }

    @Override
    public float time() {
        return time;
    }

    private net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup popupHovered;

    public net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup popup() {
        return popupHovered;
    }

    public @Nullable List<Component> tooltipLines() {
        return tooltip;
    }

    @Override
    public void push() {
        stack.push(new float[]{offsetX, offsetY, scale});
        this.pushed();
    }

    @Override
    public void pop() {
        float[] last = stack.pop();
        offsetX = last[0];
        offsetY = last[1];
        scale = last[2];
        this.popped();
    }

    @Override
    public void translate(float x, float y) {
        offsetX += x * scale;
        offsetY += y * scale;
        this.translated(x, y);
    }

    @Override
    public void scale(float factor) {
        scale *= factor;
        this.scaled(factor);
    }

    protected float pageX(float x) {
        return offsetX + x * scale;
    }

    protected float pageY(float y) {
        return offsetY + y * scale;
    }

    protected float pageScale() {
        return scale;
    }

    protected abstract void pushed();

    protected abstract void popped();

    protected abstract void translated(float x, float y);

    protected abstract void scaled(float factor);

    @Override
    public boolean hovered(int x, int y, int width, int height) {
        if (Float.isNaN(pointerX)) return false;
        float localX = (pointerX - offsetX) / scale;
        float localY = (pointerY - offsetY) / scale;
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    private void mark(int x, int y, int width, int height, Runnable action) {
        hits.add(new Hit(offsetX + x * scale, offsetY + y * scale, offsetX + (x + width) * scale, offsetY + (y + height) * scale, action));
    }

    @Override
    public boolean link(int x, int y, int width, int height, String target) {
        boolean over = this.hovered(x, y, width, height);
        if (over) {
            net.ixdarklord.folios.api.client.page.FoliosPopup popup = session.popup(target);
            if (popup != null) this.popupHovered = popup;
            else this.tooltip(session.tooltip(target));
        }
        mark(x, y, width, height, () -> navigator.follow(target));
        return over;
    }

    @Override
    public boolean action(int x, int y, int width, int height, @Nullable Component tooltip, Runnable onPress) {
        boolean over = this.hovered(x, y, width, height);
        if (over && tooltip != null) this.tooltip(tooltip);
        mark(x, y, width, height, onPress);
        return over;
    }

    @Override
    public boolean drag(int x, int y, int width, int height, Drag drag) {
        boolean over = this.hovered(x, y, width, height);
        float scale = this.scale;
        Hit hit = new Hit(offsetX + x * scale, offsetY + y * scale, offsetX + (x + width) * scale, offsetY + (y + height) * scale, () -> {},
                (dx, dy) -> drag.moved(dx / scale, dy / scale));
        hits.add(hit);
        Hit held = HELD;
        return over || held != null && Math.abs(held.x0 - hit.x0) < 0.5F && Math.abs(held.y0 - hit.y0) < 0.5F
                && Math.abs(held.x1 - hit.x1) < 0.5F && Math.abs(held.y1 - hit.y1) < 0.5F;
    }

    private static volatile @Nullable Hit HELD;

    public static void hold(@Nullable Hit hit) {
        HELD = hit;
    }

    @Override
    public boolean scroll(int x, int y, int width, int height, Scroll scroll) {
        boolean over = this.hovered(x, y, width, height);
        hits.add(new Hit(offsetX + x * this.scale, offsetY + y * this.scale, offsetX + (x + width) * this.scale, offsetY + (y + height) * this.scale,
                () -> {}, null, scroll));
        return over;
    }

    @Override
    public void tooltip(List<Component> lines) {
        tooltip = new ArrayList<>(lines);
    }

    @Override
    public void tooltip(Component text) {
        tooltip = new ArrayList<>(List.of(text));
    }

    @Override
    public void tooltip(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, stack));
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        this.item(stack, x, y, -1);
    }

    public void quietItem(ItemStack stack, int x, int y, int argb) {
        if (!stack.isEmpty() && (argb >>> 24) != 0) this.drawItem(stack, x, y, argb);
    }

    @Override
    public void item(ItemStack stack, int x, int y, int argb) {
        if (stack.isEmpty() || (argb >>> 24) == 0) return;
        this.drawItem(stack, x, y, argb);
        if (!this.hovered(x, y, 16, 16)) return;
        this.tooltip(stack);
        FoliosBook.Location about = session.book().about(stack);
        FoliosBook.Location here = navigator.here();
        if (about == null || here != null && here.entry().equals(about.entry())) return;
        FoliosEntry entry = session.book().entry(about.entry());
        if (entry == null || !entry.isOpen()) return;
        if (tooltip != null) tooltip.add(Component.translatableWithFallback("folios.item_link", "Press to read: %s", entry.title())
                .withStyle(ChatFormatting.ITALIC).withStyle(style -> style.withColor(palette.link())));
        mark(x, y, 16, 16, () -> navigator.open(about));
    }

    @Override
    public void silhouette(ItemStack stack, int x, int y, int argb) {
        if (!stack.isEmpty() && (argb >>> 24) != 0) this.drawItem(stack.copyWithCount(1), x, y, argb);
    }

    protected abstract void drawItem(ItemStack stack, int x, int y, int argb);

    public void above(Runnable draw) {
        draw.run();
    }
}
