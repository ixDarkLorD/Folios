package net.ixdarklord.folios.internal.client.screen;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosPopup;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.ixdarklord.folios.internal.client.FoliosTooltips;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.ixdarklord.folios.internal.client.paint.ScreenPainter;
import net.minecraft.ChatFormatting;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A book's popup windows ({@link FoliosPopup}), over everything else on the screen: the one a link under the pointer
 * shows, in place of a tooltip, and the one pressing it pinned, which stays where it was until the reader presses off
 * it, presses Esc or turns the page. A pinned one takes the pointer: its scene turns when dragged and zooms with the
 * wheel. Screens hand it the pointer in their own (GUI) pixels; its painter works in the popup's own.
 */
public final class PopupLayer {
    /** Room round the body, and the title's row over it, in page pixels. */
    private static final int PAD = 4;
    private static final int TITLE = 13;

    private final List<AbstractPainter.Hit> hits = new ArrayList<>();
    private @Nullable FoliosPopup placed;
    private FoliosReader.@Nullable Spot placedAt;
    private float x, y, scale = 1;
    // Where the popup under the pointer was last drawn: a pinned one stays there.
    private float hoverX, hoverY;
    private AbstractPainter.@Nullable Drag dragging;

    /**
     * Draws the pinned popup, else the one under the pointer; true when one under the pointer was drawn (the screen's
     * tooltip is then left out).
     */
    public boolean draw(GuiGraphicsExtractor graphics, FoliosReader reader, FoliosPalette palette, @Nullable FoliosPopup hovered,
                        int mouseX, int mouseY, float scale, int screenWidth, int screenHeight) {
        hits.clear();
        FoliosPopup pinned = reader.pinned();
        // Turned away from the page it was pinned on: put away.
        if (pinned != null && placed == pinned && placedAt != null && !placedAt.equals(reader.spot)) {
            reader.unpin();
            pinned = null;
        }
        if (pinned != null) {
            if (placed != pinned) {
                placed = pinned;
                placedAt = reader.spot;
                x = hoverX;
                y = hoverY;
                this.scale = scale;
            }
            this.box(graphics, reader, palette, pinned, x, y, this.scale, mouseX, mouseY, true);
            return false;
        }
        placed = null;
        dragging = null;
        if (hovered == null) return false;
        // As a tooltip sits: off the pointer's lower right, kept on the screen.
        float width = (hovered.width() + PAD * 2) * scale;
        float height = (hovered.height() + PAD * 2 + TITLE) * scale;
        hoverX = mouseX + 12 + width > screenWidth - 4 ? Math.max(4, mouseX - 12 - width) : mouseX + 12;
        hoverY = Math.max(4, Math.min(mouseY - 12, screenHeight - 4 - height));
        this.box(graphics, reader, palette, hovered, hoverX, hoverY, scale, mouseX, mouseY, false);
        return true;
    }

    // The frame in the book's panel colours, the title centred along its top, the body under it.
    private void box(GuiGraphicsExtractor graphics, FoliosReader reader, FoliosPalette palette, FoliosPopup popup, float x, float y, float scale,
                     int mouseX, int mouseY, boolean pinned) {
        int width = popup.width() + PAD * 2;
        int height = popup.height() + PAD * 2 + TITLE;
        graphics.nextStratum();
        FoliosTooltips.frame(graphics, Math.round(x), Math.round(y), Math.round(width * scale), Math.round(height * scale), reader.book);
        // The painter takes the pointer, and marks its places, in the popup's own pixels.
        ScreenPainter painter = new ScreenPainter(graphics, x, y, scale, palette, reader.session, reader, reader.seconds(),
                pinned ? (mouseX - x) / scale : Float.NaN, pinned ? (mouseY - y) / scale : Float.NaN, hits);
        try {
            FoliosPainter page = painter;
            page.centeredText(popup.title().copy().withStyle(ChatFormatting.BOLD), width / 2, PAD + 1, FoliosTooltips.titleColor(reader.book));
            page.push();
            page.translate(PAD, PAD + TITLE);
            popup.draw(page, pinned);
            page.pop();
        } finally {
            painter.end();
        }
    }

    /** Whether a pinned popup covers this point (GUI pixels). */
    private boolean over(FoliosReader reader, double mouseX, double mouseY) {
        FoliosPopup pinned = reader.pinned();
        if (pinned == null || placed != pinned) return false;
        return mouseX >= x && mouseY >= y && mouseX < x + (pinned.width() + PAD * 2) * scale && mouseY < y + (pinned.height() + PAD * 2 + TITLE) * scale;
    }

    /** A press: on a pinned popup, what is under it; off it, puts it away. True when the press was the popup's. */
    public boolean click(FoliosReader reader, double mouseX, double mouseY) {
        if (reader.pinned() == null || placed == null) return false;
        if (!this.over(reader, mouseX, mouseY)) {
            reader.unpin();
            return true;
        }
        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;
        for (int index = hits.size() - 1; index >= 0; index--) {
            AbstractPainter.Hit hit = hits.get(index);
            if (hit.scroll() != null || !hit.contains(localX, localY)) continue;
            if (hit.drag() != null) dragging = hit.drag();
            else hit.action().run();
            return true;
        }
        return true;
    }

    /** The pointer dragged (GUI pixels): true when it turns a pinned popup's scene. */
    public boolean drag(double dx, double dy) {
        if (dragging == null) return false;
        dragging.moved((float) (dx / scale), (float) (dy / scale));
        return true;
    }

    public void release() {
        dragging = null;
    }

    /** The wheel: true when the pointer is over a pinned popup (zooming its scene, if over one). */
    public boolean scroll(FoliosReader reader, double mouseX, double mouseY, double notches) {
        if (!this.over(reader, mouseX, mouseY)) return false;
        FoliosPainter.Scroll scroll = AbstractPainter.Hit.scrollAt(hits, (mouseX - x) / scale, (mouseY - y) / scale);
        if (scroll != null) scroll.scrolled(notches);
        return true;
    }

    /** Esc: true when it put a pinned popup away. */
    public boolean escape(FoliosReader reader) {
        if (reader.pinned() == null) return false;
        reader.unpin();
        return true;
    }
}
