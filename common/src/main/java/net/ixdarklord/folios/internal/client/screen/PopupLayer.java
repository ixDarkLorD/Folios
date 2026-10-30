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

public final class PopupLayer {
    private static final int PAD = 4;
    private static final int TITLE = 13;

    private final List<AbstractPainter.Hit> hits = new ArrayList<>();
    private @Nullable FoliosPopup placed;
    private FoliosReader.@Nullable Spot placedAt;
    private float x, y, scale = 1;
    private float hoverX, hoverY;
    private AbstractPainter.@Nullable Drag dragging;

    public boolean draw(GuiGraphicsExtractor graphics, FoliosReader reader, FoliosPalette palette, @Nullable FoliosPopup hovered,
                        int mouseX, int mouseY, float scale, int screenWidth, int screenHeight) {
        hits.clear();
        FoliosPopup pinned = reader.pinned();
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
        float width = (hovered.width() + PAD * 2) * scale;
        float height = (hovered.height() + PAD * 2 + TITLE) * scale;
        hoverX = mouseX + 12 + width > screenWidth - 4 ? Math.max(4, mouseX - 12 - width) : mouseX + 12;
        hoverY = Math.max(4, Math.min(mouseY - 12, screenHeight - 4 - height));
        this.box(graphics, reader, palette, hovered, hoverX, hoverY, scale, mouseX, mouseY, false);
        return true;
    }

    private void box(GuiGraphicsExtractor graphics, FoliosReader reader, FoliosPalette palette, FoliosPopup popup, float x, float y, float scale,
                     int mouseX, int mouseY, boolean pinned) {
        int width = popup.width() + PAD * 2;
        int height = popup.height() + PAD * 2 + TITLE;
        graphics.nextStratum();
        FoliosTooltips.frame(graphics, Math.round(x), Math.round(y), Math.round(width * scale), Math.round(height * scale), reader.book);
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

    private boolean over(FoliosReader reader, double mouseX, double mouseY) {
        FoliosPopup pinned = reader.pinned();
        if (pinned == null || placed != pinned) return false;
        return mouseX >= x && mouseY >= y && mouseX < x + (pinned.width() + PAD * 2) * scale && mouseY < y + (pinned.height() + PAD * 2 + TITLE) * scale;
    }

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
            if (hit.drag() != null) {
                dragging = hit.drag();
                AbstractPainter.hold(hit);
            }
            else hit.action().run();
            return true;
        }
        return true;
    }

    public boolean drag(double dx, double dy) {
        if (dragging == null) return false;
        dragging.moved((float) (dx / scale), (float) (dy / scale));
        return true;
    }

    public void release() {
        dragging = null;
        AbstractPainter.hold(null);
    }

    public boolean scroll(FoliosReader reader, double mouseX, double mouseY, double notches) {
        if (!this.over(reader, mouseX, mouseY)) return false;
        FoliosPainter.Scroll scroll = AbstractPainter.Hit.scrollAt(hits, (mouseX - x) / scale, (mouseY - y) / scale);
        if (scroll != null) scroll.scrolled(notches);
        return true;
    }

    public boolean escape(FoliosReader reader) {
        if (reader.pinned() == null) return false;
        reader.unpin();
        return true;
    }
}
