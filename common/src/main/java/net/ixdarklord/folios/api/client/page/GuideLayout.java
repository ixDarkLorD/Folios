package net.ixdarklord.folios.api.client.page;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where pages are laid out: the page's size and margins, the font, and helpers for the parts most pages have (a
 * title, and text that continues onto further pages).
 * <p>
 * Every page is {@value #PAGE_WIDTH} by {@value #PAGE_HEIGHT} pixels, in both kinds of book; content sits between
 * the margins, from {@link #CONTENT_LEFT} to {@link #CONTENT_RIGHT} and {@link #CONTENT_TOP} to
 * {@link #CONTENT_BOTTOM}.
 */
public final class GuideLayout {
    public static final int PAGE_WIDTH = 136;
    public static final int PAGE_HEIGHT = 180;
    public static final int MARGIN = 10;
    public static final int CONTENT_LEFT = MARGIN;
    public static final int CONTENT_RIGHT = PAGE_WIDTH - MARGIN;
    public static final int CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_LEFT;
    public static final int CONTENT_TOP = 12;
    /** Under this is the page's number. */
    public static final int CONTENT_BOTTOM = PAGE_HEIGHT - 18;
    /** How much room a title takes, its rule under it included. */
    public static final int TITLE_HEIGHT = 18;

    private static final AtomicInteger VIEWPORTS = new AtomicInteger();

    private final GuideBook book;
    private final Font font;
    private final List<SceneViewport> viewports = new ArrayList<>();

    public GuideLayout(GuideBook book, Font font) {
        this.book = book;
        this.font = font;
    }

    public GuideBook book() {
        return book;
    }

    public Font font() {
        return font;
    }

    /** Text set out to the content's width. */
    public GuideText text(String raw) {
        return GuideText.of(font, raw, CONTENT_WIDTH);
    }

    /**
     * A page with an optional title, a part of its own {@code reserved} pixels tall under it, and text under that;
     * text that doesn't fit runs on over as many more pages as it needs.
     *
     * @param part draws the page's own part, given the height it starts at; null for none
     */
    public List<GuidePage.Laid> flow(@Nullable String title, int reserved, @Nullable String text, GuidePage.@Nullable Part part) {
        Component heading = title == null || title.isEmpty() ? null : GuideText.inline(title);
        int partTop = CONTENT_TOP + (heading != null ? TITLE_HEIGHT : 0);
        int textTop = partTop + reserved + (reserved > 0 ? 6 : 0);
        List<GuideText> pieces = text == null || text.isEmpty() ? List.of() : this.text(text).split(Math.max(0, CONTENT_BOTTOM - textTop), CONTENT_BOTTOM - CONTENT_TOP);
        // Text with no room left on the first page starts on the next.
        boolean moved = !pieces.isEmpty() && pieces.getFirst().isEmpty();
        List<GuidePage.Laid> pages = new ArrayList<>();
        GuideText first = pieces.isEmpty() || moved ? null : pieces.getFirst();
        pages.add(painter -> {
            if (heading != null) title(painter, heading, CONTENT_TOP);
            if (part != null) part.draw(painter, partTop);
            if (first != null) first.draw(painter, CONTENT_LEFT, textTop);
        });
        for (int index = 1; index < pieces.size(); index++) {
            GuideText piece = pieces.get(index);
            pages.add(painter -> piece.draw(painter, CONTENT_LEFT, CONTENT_TOP));
        }
        return pages;
    }

    /** A page's title: bold, with the book's mark under it. */
    public static void title(GuidePainter painter, Component title, int y) {
        GuidePalette palette = painter.palette();
        Font font = painter.font();
        Component bold = title.copy().withStyle(net.minecraft.ChatFormatting.BOLD);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(bold, CONTENT_WIDTH);
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence line : lines.subList(0, Math.min(1, lines.size()))) {
            painter.text(line, CONTENT_LEFT, lineY, palette.heading());
            lineY += 10;
        }
        painter.fill(CONTENT_LEFT, y + 11, CONTENT_LEFT + 16, y + 12, palette.accent());
        painter.fill(CONTENT_LEFT + 18, y + 11, CONTENT_RIGHT, y + 12, palette.line());
    }

    /**
     * How many texture pixels a scene has for each page pixel it is shown over: enough for it to stay sharp on a
     * handheld book held close and on large screens.
     */
    public static final int SCENE_DETAIL = 4;

    /**
     * A scene for a page to show (an entity, a structure), drawn to a texture of {@code width} by {@code height}
     * pixels (its size on the page times {@link #SCENE_DETAIL}). It belongs to this layout: it is closed when the book is laid out again.
     */
    public SceneViewport viewport(int width, int height) {
        SceneViewport viewport = SceneViewport.create(Folios.rl("guide/scene_" + VIEWPORTS.incrementAndGet()), width, height);
        viewports.add(viewport);
        return viewport;
    }

    /** Closes the scenes made for this layout. */
    public void close() {
        for (SceneViewport viewport : viewports) viewport.close();
        viewports.clear();
    }
}
