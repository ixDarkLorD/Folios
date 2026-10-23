package net.ixdarklord.folios.api.client.page;

/**
 * The colours a page is drawn in (ARGB). Pages never pick their own: the same page is ink on paper in a handheld
 * book and light text on a dark panel on a screen, so they draw with these.
 *
 * @param paper   the page itself
 * @param text    body text
 * @param muted   captions, hints and other quiet text
 * @param heading titles and headings
 * @param accent  the book's colour: marks, rules, the bar of a quote
 * @param link    links in text
 * @param panel   boxes set on the page (a recipe's grid, an image's frame)
 * @param line    hairlines: borders and dividers
 */
public record FoliosPalette(int paper, int text, int muted, int heading, int accent, int link, int panel, int line) {
    /** {@code color} with its alpha set (0 to 255). */
    public static int alpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
    }

    /** One colour a share {@code t} (0 to 1) of the way to another, alpha included. */
    public static int mix(int from, int to, float t) {
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
