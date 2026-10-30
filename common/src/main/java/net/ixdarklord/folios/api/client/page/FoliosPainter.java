package net.ixdarklord.folios.api.client.page;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a page draws with. The same calls draw it on a screen and on the pages of a book held in the world, so a page
 * is written once for every kind of book.
 * <p>
 * Coordinates are page pixels: (0, 0) is the page's top left, {@link FoliosLayout#PAGE_WIDTH} by
 * {@link FoliosLayout#PAGE_HEIGHT}, one pixel being one GUI pixel on a screen. {@link #push}, {@link #translate} and
 * {@link #scale} move what follows, and the pointer's place with it, so {@link #hovered} and {@link #link} take the
 * same coordinates as the drawing.
 * <p>
 * There is no click handler on a page: it marks what can be pressed ({@link #link}, {@link #action}) while it draws,
 * and a press lands on what was marked under the pointer in the last frame.
 */
public interface FoliosPainter {
    Font font();

    FoliosPalette palette();

    /** The size of the page being drawn: the default page's unless the book's pages are larger (a screen book at a small GUI scale). */
    default int pageWidth() {
        return FoliosLayout.PAGE_WIDTH;
    }

    default int pageHeight() {
        return FoliosLayout.PAGE_HEIGHT;
    }

    /** Where the page's content ends at the right and at the foot. */
    default int contentRight() {
        return this.pageWidth() - FoliosLayout.MARGIN;
    }

    default int contentBottom() {
        return this.pageHeight() - FoliosLayout.FOOT;
    }

    /** Seconds since the book was opened: for what moves or cycles on a page. */
    float time();

    /** Whether the page is on a screen (true), or on a book in the world (false). */
    boolean flat();

    void fill(int x0, int y0, int x1, int y1, int argb);

    void text(FormattedCharSequence text, int x, int y, int argb);

    default void text(Component text, int x, int y, int argb) {
        this.text(text.getVisualOrderText(), x, y, argb);
    }

    default void centeredText(Component text, int centerX, int y, int argb) {
        FormattedCharSequence line = text.getVisualOrderText();
        this.text(line, centerX - this.font().width(line) / 2, y, argb);
    }

    /** A part of a texture, from its (u0, v0) to its (u1, v1), each 0 to 1, tinted by {@code argb}. */
    void texture(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb);

    /** An item's icon, 16 pixels square; hovering shows its tooltip, and it links to the book's entry about it, if any. */
    void item(ItemStack stack, int x, int y);

    /**
     * As {@link #item(ItemStack, int, int)}, tinted by {@code argb}: its alpha fades the item, its colour tints it
     * (white leaves it as it is). A painter that can't fade items shows it once its alpha is past half.
     */
    default void item(ItemStack stack, int x, int y, int argb) {
        if ((argb >>> 24) >= 128) this.item(stack, x, y);
    }

    /**
     * An item's shape tinted by {@code argb} (dark, for a silhouette), with nothing of what it is: no count, no
     * tooltip, no link. What a locked page or element shows of the items it holds. A painter that can't tint items
     * draws nothing.
     */
    default void silhouette(ItemStack stack, int x, int y, int argb) {}

    /** A scene's picture; the painter keeps it in use while it is drawn. */
    void scene(SceneViewport viewport, int x, int y, int width, int height);

    /** As {@link #scene(SceneViewport, int, int, int, int)}, showing only the part of it from {@code u0} to {@code u1} across (0 to 1). */
    default void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float u1) {
        this.scene(viewport, x, y, width, height);
    }

    /**
     * As {@link #scene(SceneViewport, int, int, int, int)}, showing only the part of it from ({@code u0}, {@code v0})
     * to ({@code u1}, {@code v1}) (0 to 1, from its top left), tinted by {@code argb}: its alpha fades the picture.
     */
    default void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        if ((argb >>> 24) >= 128 && v0 <= 0 && v1 >= 1) this.scene(viewport, x, y, width, height, u0, u1);
    }

    /**
     * Draws with what is drawn kept inside a box, (x, y) {@code width} by {@code height} in the painter's pixels. A
     * painter that can't clip draws it all.
     */
    default void clip(int x, int y, int width, int height, Runnable draw) {
        draw.run();
    }

    void push();

    void pop();

    void translate(float x, float y);

    void scale(float scale);

    /** Whether the pointer is over a rectangle; never while the page is turning or covered. */
    boolean hovered(int x, int y, int width, int height);

    /**
     * Makes a rectangle open a link when pressed: {@code entry:<id>}, {@code entry:<id>#<marker>},
     * {@code category:<id>}, {@code http(s)://...}, or a bare entry id. Hovering it shows where it goes.
     *
     * @return whether it is hovered
     */
    boolean link(int x, int y, int width, int height, String target);

    /**
     * Makes a rectangle run something when pressed (on the client, during the press).
     *
     * @param tooltip shown while it is hovered; null for none
     * @return whether it is hovered
     */
    boolean action(int x, int y, int width, int height, @Nullable Component tooltip, Runnable onPress);

    /** What a dragged rectangle does as the pointer moves while it is held: by how far, in page pixels. */
    @FunctionalInterface
    interface Drag {
        void moved(float dx, float dy);
    }

    /**
     * Makes a rectangle draggable: held with the left button, it is told how far the pointer moves until let go.
     *
     * @return whether it is hovered
     */
    boolean drag(int x, int y, int width, int height, Drag drag);

    /** What a rectangle does when the mouse wheel turns over it: by how many notches, away from the reader positive. */
    @FunctionalInterface
    interface Scroll {
        void scrolled(double notches);
    }

    /**
     * Makes a rectangle take the mouse wheel while the pointer is over it, rather than the wheel turning the page.
     *
     * @return whether it is hovered
     */
    boolean scroll(int x, int y, int width, int height, Scroll scroll);

    /** Shows a tooltip at the pointer this frame. */
    void tooltip(Component text);

    /** A tooltip of several lines: a name, then lines saying more. */
    void tooltip(java.util.List<Component> lines);

    void tooltip(ItemStack stack);
}
