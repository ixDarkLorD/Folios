package net.ixdarklord.folios.api.client.page;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where pages are laid out: the page's size and margins, the font, and helpers for the parts most pages have (a
 * title, and text that continues onto further pages).
 * <p>
 * A page is {@link #pageWidth()} by {@link #pageHeight()} pixels: {@value #PAGE_WIDTH} by {@value #PAGE_HEIGHT} in a
 * handheld book and on a screen at the Auto GUI scale, larger on a screen at a smaller GUI scale, so more fits on it.
 * Content sits between the margins, from {@link #CONTENT_LEFT} to {@link #contentRight()} and {@link #CONTENT_TOP} to
 * {@link #contentBottom()}. A page type lays itself out to these (and draws to its {@link FoliosPainter}'s), so it
 * fills a larger page; the constants are the default page's.
 */
public final class FoliosLayout {
    /** The default page's size: a handheld book's, and a screen book's at the Auto GUI scale. */
    public static final int PAGE_WIDTH = 136;
    public static final int PAGE_HEIGHT = 180;
    // Four pixels clear of the page frame's lines and corners (which reach 8 in from the page's edge).
    public static final int MARGIN = 12;
    public static final int CONTENT_LEFT = MARGIN;
    public static final int CONTENT_RIGHT = PAGE_WIDTH - MARGIN;
    public static final int CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_LEFT;
    public static final int CONTENT_TOP = 12;
    /** How far above a page's foot its content stops: under it are the page's number and its turn buttons, 2 pixels clear. */
    public static final int FOOT = 23;
    /** Under this are the page's number and its turn buttons, with 2 pixels between them and what is on the page. */
    public static final int CONTENT_BOTTOM = PAGE_HEIGHT - FOOT;
    /** How much room a title takes: its line, its rule under it, and 4 pixels under that (as a heading in text has). */
    public static final int TITLE_HEIGHT = 16;

    private static final AtomicInteger VIEWPORTS = new AtomicInteger();

    private final FoliosBook book;
    private final Font font;
    private final List<SceneViewport> viewports = new ArrayList<>();
    private @Nullable List<Flow> capturing;
    private @Nullable FoliosAlign alignment;
    private FoliosIcon titleIcon = FoliosIcon.NONE;
    private final int pageWidth;
    private final int pageHeight;

    public FoliosLayout(FoliosBook book, Font font) {
        this(book, font, PAGE_WIDTH, PAGE_HEIGHT);
    }

    /** A layout for pages {@code pageWidth} by {@code pageHeight}, no smaller than the default page. */
    public FoliosLayout(FoliosBook book, Font font, int pageWidth, int pageHeight) {
        this.book = book;
        this.font = font;
        this.pageWidth = Math.max(PAGE_WIDTH, pageWidth);
        this.pageHeight = Math.max(PAGE_HEIGHT, pageHeight);
    }

    /** The page's width and height, in its own pixels. */
    public int pageWidth() {
        return pageWidth;
    }

    public int pageHeight() {
        return pageHeight;
    }

    /**
     * A height written for the default page, for this one: grown as the page is wider, so a window (a scene, a picture)
     * keeps its shape on a larger page rather than turning into a strip.
     */
    public int scaled(int height) {
        return Math.round(height * pageWidth / (float) PAGE_WIDTH);
    }

    /** Where content ends at the right, how wide it is, and where it ends at the foot (above the number and turn buttons). */
    public int contentRight() {
        return pageWidth - MARGIN;
    }

    public int contentWidth() {
        return this.contentRight() - CONTENT_LEFT;
    }

    public int contentBottom() {
        return pageHeight - FOOT;
    }

    public FoliosBook book() {
        return book;
    }

    public Font font() {
        return font;
    }

    /** Text set out to the content's width. */
    /** How what is laid out now is aligned across the page ({@code "alignment"}); null for each part's own way. */
    public @Nullable FoliosAlign alignment() {
        return alignment;
    }

    /** Sets how what is laid out next is aligned, as a page sets it for each of its elements; null for each part's own way. */
    public void alignment(@Nullable FoliosAlign alignment) {
        this.alignment = alignment;
    }

    /**
     * Where something {@code width} wide starts across the page's content, for an alignment: centred for null and
     * {@link FoliosAlign#JUSTIFY}, the way a picture or a card is placed by default.
     */
    public static int place(FoliosPainter painter, @Nullable FoliosAlign align, int width) {
        int room = painter.contentRight() - CONTENT_LEFT;
        return CONTENT_LEFT + (align == null ? FoliosAlign.CENTER : align).offset(room, width);
    }

    public FoliosText text(String raw) {
        return FoliosText.of(font, raw, this.contentWidth(), alignment);
    }

    /**
     * A page with an optional title, a part of its own {@code reserved} pixels tall under it, and text under that;
     * text that doesn't fit runs on over as many more pages as it needs.
     *
     * @param part draws the page's own part, given the height it starts at; null for none
     */
    public List<FoliosPageElement.Laid> flow(@Nullable String title, int reserved, @Nullable String text, FoliosPageElement.@Nullable Part part) {
        if (capturing != null) {
            capturing.add(new Flow(title, reserved, text, part));
            return List.of();
        }
        Component heading = title == null || title.isEmpty() ? null : FoliosText.inline(title);
        int partTop = CONTENT_TOP + (heading != null ? TITLE_HEIGHT : 0);
        int textTop = partTop + reserved + (reserved > 0 ? 6 : 0);
        int bottom = this.contentBottom();
        List<FoliosText> pieces = text == null || text.isEmpty() ? List.of() : this.text(text).split(Math.max(0, bottom - textTop), bottom - CONTENT_TOP);
        // Text with no room left on the first page starts on the next.
        boolean moved = !pieces.isEmpty() && pieces.getFirst().isEmpty();
        List<FoliosPageElement.Laid> pages = new ArrayList<>();
        FoliosIcon icon = titleIcon;
        FoliosText first = pieces.isEmpty() || moved ? null : pieces.getFirst();
        pages.add(painter -> {
            if (heading != null) title(painter, heading, CONTENT_TOP, icon);
            if (part != null) part.draw(painter, partTop);
            if (first != null) first.draw(painter, CONTENT_LEFT, textTop);
        });
        for (int index = 1; index < pieces.size(); index++) {
            FoliosText piece = pieces.get(index);
            pages.add(painter -> piece.draw(painter, CONTENT_LEFT, CONTENT_TOP));
        }
        return pages;
    }

    /**
     * What a page asked {@link #flow} for: its title, its own part and how tall it is, and its text. A page that holds
     * others (a page's {@code "elements"}) sets each of them out from these, one under another.
     */
    public record Flow(@Nullable String title, int reserved, @Nullable String text, FoliosPageElement.@Nullable Part part) {}

    /**
     * Lays a page out only as far as what it asks {@link #flow} for, to set it inside another page; empty for a page
     * that lays itself out some other way.
     */
    public List<Flow> capture(FoliosPageElement page) {
        List<Flow> outer = capturing;
        List<Flow> flows = new ArrayList<>();
        capturing = flows;
        try {
            page.lay(this);
        } finally {
            capturing = outer;
        }
        return flows;
    }

    /**
     * The icon put before the titles of the pages laid out next (as {@link #flow} draws them): their entry's, or a
     * page's own. The book sets it as it lays out each page of an entry; {@link FoliosIcon#NONE} for none.
     */
    public void titleIcon(FoliosIcon icon) {
        this.titleIcon = icon;
    }

    public FoliosIcon titleIcon() {
        return titleIcon;
    }

    /** A page's title: bold, with a line under it. */
    public static void title(FoliosPainter painter, Component title, int y) {
        title(painter, title, y, FoliosIcon.NONE);
    }

    /**
     * As {@link #title(FoliosPainter, Component, int)}, with an icon before it, unless none: three quarters of its size,
     * centred over a mark of the book's colour that heads the line under the title, the title starting where the rest of
     * the line does. Without an icon the line runs unbroken.
     */
    public static void title(FoliosPainter painter, Component title, int y, FoliosIcon icon) {
        title(painter, title, y, icon, null);
    }

    /** As {@link #title(FoliosPainter, Component, int, FoliosIcon)}, placed across the page by an alignment (left for null or justify). */
    public static void title(FoliosPainter painter, Component title, int y, FoliosIcon icon, @Nullable FoliosAlign align) {
        FoliosPalette palette = painter.palette();
        Font font = painter.font();
        Component bold = title.copy().withStyle(net.minecraft.ChatFormatting.BOLD);
        int right = painter.contentRight();
        int indent = icon.isEmpty() ? 0 : 18;
        // On one line: made smaller to fit, down to three quarters, and past that cut short with an ellipsis.
        int room = right - CONTENT_LEFT - indent;
        float fit = Math.max(0.75F, Math.min(1, room / (float) Math.max(1, font.width(bold))));
        int shown = (int) Math.ceil(Math.min(room, font.width(bold) * fit));
        // The icon goes with the title wherever it is placed.
        int across = align == null || align == FoliosAlign.JUSTIFY ? 0 : align.offset(room, shown);
        if (!icon.isEmpty()) {
            painter.push();
            painter.translate(CONTENT_LEFT + 2 + across, y - 2);
            painter.scale(0.75F);
            icon.draw(painter, 0, 0);
            painter.pop();
        }
        net.minecraft.util.FormattedCharSequence line = bold.getVisualOrderText();
        if (font.width(bold) * fit > room) {
            net.minecraft.network.chat.FormattedText cut = font.substrByWidth(bold, (int) (room / fit) - font.width("…"));
            line = net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(cut, net.minecraft.network.chat.FormattedText.of("…")));
        }
        painter.push();
        painter.translate(CONTENT_LEFT + indent + across, y + (1 - fit) * 4);
        painter.scale(fit);
        painter.text(line, 0, 0, palette.heading());
        painter.pop();
        if (icon.isEmpty()) {
            painter.fill(CONTENT_LEFT, y + 11, right, y + 12, palette.line());
            return;
        }
        painter.fill(CONTENT_LEFT, y + 11, CONTENT_LEFT + across, y + 12, palette.line());
        painter.fill(CONTENT_LEFT + across, y + 11, CONTENT_LEFT + across + 16, y + 12, palette.accent());
        painter.fill(CONTENT_LEFT + across + 18, y + 11, right, y + 12, palette.line());
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
