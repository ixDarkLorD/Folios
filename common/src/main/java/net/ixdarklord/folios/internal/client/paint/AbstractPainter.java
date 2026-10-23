package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.GuideEntry;
import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.internal.client.GuideSession;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// What both painters share: the pointer in page pixels through the painter's own moves, the places marked to be
// pressed, and the tooltip for this frame.
public abstract class AbstractPainter implements GuidePainter {
    /** Where a book goes when something on a page is pressed. */
    public interface Navigator {
        void follow(String link);

        void open(GuideBook.Location location);

        /** The page shown, to tell links to it apart from links elsewhere; null when none. */
        GuideBook.@Nullable Location here();
    }

    /** A rectangle that can be pressed, dragged or scrolled over, in page pixels. One that takes the wheel is never pressed. */
    public record Hit(float x0, float y0, float x1, float y1, Runnable action, @Nullable Drag drag, @Nullable Scroll scroll) {
        public Hit(float x0, float y0, float x1, float y1, Runnable action) {
            this(x0, y0, x1, y1, action, null, null);
        }

        public Hit(float x0, float y0, float x1, float y1, Runnable action, @Nullable Drag drag) {
            this(x0, y0, x1, y1, action, drag, null);
        }

        /** The wheel handler of the last place among these the pointer is over that takes it; null for none. */
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
    protected final GuidePalette palette;
    protected final GuideSession session;
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

    /**
     * @param pointerX the pointer on the page, in page pixels; NaN when it isn't on it or the page can't be pressed
     * @param hits     where this page's pressable places are put
     */
    // The page's size is its book's layout's: larger on a screen book at a small GUI scale.
    @Override
    public int pageWidth() {
        return session.layout().pageWidth();
    }

    @Override
    public int pageHeight() {
        return session.layout().pageHeight();
    }

    protected AbstractPainter(GuidePalette palette, GuideSession session, Navigator navigator, float time, float pointerX, float pointerY, List<Hit> hits) {
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
    public GuidePalette palette() {
        return palette;
    }

    @Override
    public float time() {
        return time;
    }

    public @Nullable List<Component> tooltipLines() {
        return tooltip;
    }

    // ---- Moves ----

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

    /** Where a point of the painter's own coordinates is on the page, through its moves. */
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

    // ---- The pointer ----

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
        if (over) this.tooltip(session.describe(target));
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
        // Moves in page pixels at this painter's scale.
        float scale = this.scale;
        hits.add(new Hit(offsetX + x * scale, offsetY + y * scale, offsetX + (x + width) * scale, offsetY + (y + height) * scale, () -> {},
                (dx, dy) -> drag.moved(dx / scale, dy / scale)));
        return over;
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

    // ---- Items ----

    @Override
    public void item(ItemStack stack, int x, int y) {
        this.item(stack, x, y, -1);
    }

    @Override
    public void item(ItemStack stack, int x, int y, int argb) {
        if (stack.isEmpty() || (argb >>> 24) == 0) return;
        this.drawItem(stack, x, y, argb);
        if (!this.hovered(x, y, 16, 16)) return;
        this.tooltip(stack);
        GuideBook.Location about = session.book().about(stack);
        GuideBook.Location here = navigator.here();
        if (about == null || here != null && here.entry().equals(about.entry())) return;
        GuideEntry entry = session.book().entry(about.entry());
        if (entry == null || !entry.isOpen()) return;
        if (tooltip != null) tooltip.add(Component.translatableWithFallback("folios.item_link", "Press to read: %s", entry.title())
                .withStyle(ChatFormatting.ITALIC).withColor(palette.link()));
        mark(x, y, 16, 16, () -> navigator.open(about));
    }

    /** Draws an item, tinted by {@code argb} (white and opaque: as it is). */
    protected abstract void drawItem(ItemStack stack, int x, int y, int argb);

    /**
     * Draws over everything drawn so far, items included (which otherwise lie over flat things drawn after them): for
     * a reveal's effect about an item or over a whole page.
     */
    public void above(Runnable draw) {
        draw.run();
    }
}
