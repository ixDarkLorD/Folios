package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.internal.client.paint.PixelIcon;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.minecraft.resources.ResourceLocation;

// The boxes built-in pages set things in: soft-cornered panels with a hairline edge, and item slots.
public final class Shapes {
    private Shapes() {}

    /** A panel: its corner pixels left out, so it reads as slightly rounded. */
    public static void panel(GuidePainter painter, int x0, int y0, int x1, int y1) {
        GuidePalette palette = painter.palette();
        painter.fill(x0 + 1, y0, x1 - 1, y1, palette.line());
        painter.fill(x0, y0 + 1, x1, y1 - 1, palette.line());
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, palette.panel());
    }

    /** An 18-pixel slot for an item drawn at (x + 1, y + 1). */
    public static void slot(GuidePainter painter, int x, int y) {
        GuidePalette palette = painter.palette();
        painter.fill(x, y, x + 18, y + 18, GuidePalette.mix(palette.panel(), palette.line(), 0.55F));
        painter.fill(x + 1, y + 1, x + 17, y + 17, GuidePalette.mix(palette.panel(), palette.paper(), 0.35F));
    }

    /**
     * The page's frame, drawn under what is on it: the book's frame texture over the whole page, tinted between the
     * page's lines and the book's colour, so it sits as quietly on dark paper as on parchment. On a page larger than
     * the default its corners and the ornaments at the middle of its head and foot keep their size; only the plain runs
     * of line between them are drawn longer.
     */
    public static void pageFrame(GuidePainter painter, ResourceLocation texture, int width, int height) {
        frameSpan(painter, texture, 0, width, 0, FRAME_W, height, frameTint(painter));
    }

    /** The spine's width between a screen book's pages, which a spread's halves draw on into to meet. */
    public static final int SCREEN_SPINE = 6;
    /** How much of the frame texture's side is left out at a spread's fold, where its plain middle takes over. */
    private static final int FRAME_SIDE = 12;
    /** The frame texture's size, in the default page's pixels, and the plain column of it that only has its head and foot lines. */
    private static final int FRAME_W = GuideLayout.PAGE_WIDTH;
    private static final int FRAME_H = GuideLayout.PAGE_HEIGHT;
    private static final int FRAME_PLAIN = 40;
    // Where the frame texture is cut across and down: {from, to, 1 if it stretches}. The corners (with their ornaments)
    // and the ornament at the middle of the head and foot keep their size; the runs of plain line between stretch.
    private static final int[][] FRAME_COLUMNS = {{0, 20, 0}, {20, 58, 1}, {58, 78, 0}, {78, 116, 1}, {116, FRAME_W, 0}};
    private static final int[][] FRAME_ROWS = {{0, 20, 0}, {20, 160, 1}, {160, FRAME_H, 0}};

    /**
     * The page's frame, or for half of a spread the frame joined with the other half's: its side at the fold left
     * out, its top and bottom running on (a plain middle column of the texture, stretched) to meet the other page's.
     */
    public static void pageFrame(GuidePainter painter, ResourceLocation texture, int width, int height, GuidePage.@org.jetbrains.annotations.Nullable Laid page, boolean right) {
        if (!(page instanceof GuidePage.SpreadHalf)) {
            pageFrame(painter, texture, width, height);
            return;
        }
        int tint = frameTint(painter);
        int over = painter.flat() ? SCREEN_SPINE / 2 : 0;
        if (!right) {
            frameSpan(painter, texture, 0, width - FRAME_SIDE, 0, FRAME_W - FRAME_SIDE, height, tint);
            frameSpan(painter, texture, width - FRAME_SIDE, FRAME_SIDE + over, FRAME_PLAIN, FRAME_PLAIN + 1, height, tint);
        } else {
            frameSpan(painter, texture, -over, FRAME_SIDE + over, FRAME_PLAIN, FRAME_PLAIN + 1, height, tint);
            frameSpan(painter, texture, FRAME_SIDE, width - FRAME_SIDE, FRAME_SIDE, FRAME_W, height, tint);
        }
    }

    // The columns `from` to `to` of the frame texture drawn from x across `width`, down `height`: each column piece and
    // row piece that keeps its size kept, the stretching ones sharing what is left.
    private static void frameSpan(GuidePainter painter, ResourceLocation texture, int x, int width, int from, int to, int height, int tint) {
        float[][] columns = slices(FRAME_COLUMNS, from, to, width);
        float[][] rows = slices(FRAME_ROWS, 0, FRAME_H, height);
        for (float[] column : columns) {
            for (float[] row : rows) {
                int x0 = x + Math.round(column[0]);
                int x1 = x + Math.round(column[1]);
                int y0 = Math.round(row[0]);
                int y1 = Math.round(row[1]);
                if (x1 <= x0 || y1 <= y0) continue;
                painter.texture(texture, x0, y0, x1 - x0, y1 - y0, column[2] / FRAME_W, row[2] / FRAME_H, column[3] / FRAME_W, row[3] / FRAME_H, tint);
            }
        }
    }

    // The pieces of the texture from `from` to `to` (cut where `cuts` cuts it), each {drawn from, drawn to, texture from,
    // texture to}, laid over `length`: the fixed ones their own length, the stretching ones sharing the rest by theirs.
    private static float[][] slices(int[][] cuts, int from, int to, int length) {
        java.util.List<float[]> pieces = new java.util.ArrayList<>();
        int fixed = 0;
        int stretched = 0;
        for (int[] cut : cuts) {
            int a = Math.max(from, cut[0]);
            int b = Math.min(to, cut[1]);
            if (b <= a) continue;
            if (cut[2] == 0) fixed += b - a;
            else stretched += b - a;
        }
        // The stretching pieces' share: all of what is left, or (with none to stretch) the fixed ones scaled to fit.
        float stretch = stretched > 0 ? Math.max(0, length - fixed) / (float) stretched : 0;
        float squeeze = stretched > 0 || fixed == 0 ? 1 : length / (float) fixed;
        float at = 0;
        for (int[] cut : cuts) {
            int a = Math.max(from, cut[0]);
            int b = Math.min(to, cut[1]);
            if (b <= a) continue;
            float drawn = (b - a) * (cut[2] == 0 ? squeeze : stretch);
            pieces.add(new float[]{at, at + drawn, a, b});
            at += drawn;
        }
        return pieces.toArray(new float[0][]);
    }

    /**
     * A line of text centred on a spread's fold, as each page of it prints it: only the letters whose middle is on its
     * side, placed so the halves meet as one line while the book lies open (on a screen book, across the spine between
     * the pages). Nothing is drawn past the page, so a leaf turning carries its own letters and leaves none behind.
     */
    public static void acrossFold(GuidePainter painter, net.minecraft.util.FormattedCharSequence line, int y, int color, boolean right) {
        net.minecraft.client.gui.Font font = painter.font();
        int width = painter.pageWidth();
        int gap = painter.flat() ? SCREEN_SPINE : 0;
        int offset = right ? width + gap / 2 : -gap / 2;
        int[] x = {width - font.width(line) / 2};
        line.accept((index, style, codePoint) -> {
            net.minecraft.util.FormattedCharSequence letter = net.minecraft.util.FormattedCharSequence.codepoint(codePoint, style);
            int letterWidth = font.width(letter);
            if ((x[0] + letterWidth / 2.0F >= width) == right) painter.text(letter, x[0] - offset, y, color);
            x[0] += letterWidth;
            return true;
        });
    }

    /** How many leaves of a book's block show past its pages, a pixel each. */
    public static final int FORE_EDGE = 3;

    // The colour of a leaf of the fore-edge, the first nearest the page: leaves and the thin shadows between them by
    // turns, darkening a little deeper into the block.
    private static int leaf(GuidePalette palette, int leaf) {
        int paper = palette.paper() | 0xFF000000;
        float depth = leaf / (float) (FORE_EDGE - 1);
        float shade = (leaf % 2 == 0 ? 0.14F : 0.42F) + 0.22F * depth;
        return GuidePalette.mix(paper, palette.line(), Math.min(1, shade));
    }

    /**
     * The fore-edge of a book's leaves: the block of pages showing past the page, a pixel a leaf, from {@code x} (the
     * page's edge) outwards, rightwards or leftwards; from {@code y0} to {@code y1}.
     */
    public static void foreEdge(GuidePainter painter, int x, int y0, int y1, boolean right) {
        foreEdge(painter, x, y0, y1, right, FORE_EDGE);
    }

    /** The same, {@code leaves} wide. */
    public static void foreEdge(GuidePainter painter, int x, int y0, int y1, boolean right, int leaves) {
        GuidePalette palette = painter.palette();
        int paper = palette.paper() | 0xFF000000;
        for (int leaf = 0; leaf < leaves; leaf++) {
            int at = right ? x + leaf : x - leaf - 1;
            int color = leaf(palette, leaf);
            int aged = GuidePalette.mix(color, AGED, 0.2F);
            // Uneven, as a block of real leaves is: in short runs, now a leaf a hair short (a nick in the edge, deeper
            // in the block more often), now one standing proud and catching the light.
            int y = y0;
            while (y < y1) {
                int h = hash(leaf * 7919 + (right ? 1 : 0), y - y0);
                int end = Math.min(y1, y + 3 + (h & 7));
                boolean nick = leaf > 0 && ((h >>> 3) & 7) < leaf;
                int tone = ((h >>> 9) % 5) < 2 ? aged : color;
                if (!nick) painter.fill(at, y, at + 1, end, ((h >>> 6) & 3) == 0 ? GuidePalette.mix(tone, paper, 0.3F) : tone);
                y = end;
            }
        }
    }

    /**
     * A page's rough-cut edge along its head ({@code down} false: the edge is row {@code y}, the page below it) or its
     * foot ({@code down} true: the page above): small irregular bites out of the paper, showing {@code behind}, and a
     * faint worn line where the edge is whole.
     */
    public static void deckle(GuidePainter painter, int x0, int x1, int y, boolean down, int behind) {
        deckle(painter, x0, x1, y, down, behind, 1);
    }

    /** As {@link #deckle(GuidePainter, int, int, int, boolean, int)}, cut rougher for a {@code wear} over 1. */
    public static void deckle(GuidePainter painter, int x0, int x1, int y, boolean down, int behind, float wear) {
        GuidePalette palette = painter.palette();
        int worn = GuidePalette.mix(palette.paper() | 0xFF000000, palette.line(), 0.28F);
        int inward = down ? -1 : 1;
        int x = x0;
        while (x < x1) {
            int h = hash(down ? 31 : 17, x - x0);
            int end = Math.min(x1, x + 2 + (h & 7));
            // Whole: the edge worn a shade darker, and a little into the page here and there.
            painter.fill(x, y, end, y + 1, worn);
            if (((h >>> 9) & 3) == 0) painter.fill(x, y + inward, Math.min(end, x + 2), y + inward + 1, worn);
            x = end;
            // A bite in about one run of four; one of two on a well-worn page.
            if (x >= x1 || ((h >>> 4) & (wear > 1 ? 1 : 3)) != 0) continue;
            // A bite out of it, one deep, or two at its middle; on a well-worn page wider, deeper and ragged, a
            // pixel narrower each row in.
            int bite = Math.min(x1 - x, 1 + ((h >>> 6) & 3) + (wear > 1 ? (h >>> 12) & 1 : 0));
            painter.fill(x, y, x + bite, y + 1, behind);
            int deep = wear > 1 ? Math.min(Math.round(wear), 1 + ((h >>> 14) & 3)) : bite >= 3 ? 2 : 1;
            for (int row = 1; row < deep; row++) {
                int in = row + ((h >>> (16 + row)) & 1);
                if (bite - 2 * in + 1 <= 0) break;
                painter.fill(x + in, y + inward * row, x + bite - in + 1, y + inward * row + 1, behind);
            }
            x += bite;
        }
    }

    /** The warm brown old paper yellows towards. */
    private static final int AGED = 0xFF9A7A42;

    /**
     * A page worn with age, from x0 to x1 and y0 to y1: yellowing creeping in from its head, its foot and its outer side
     * (not its fold) unevenly, deeper here and shallower there; a few faint stains; and the odd small nick or tear in
     * its outer edge. {@code outerRight} for a right-hand page, whose outer side is at x1. Drawn over the paper, under
     * what is on the page; the same every frame.
     */
    public static void wornEdges(GuidePainter painter, int x0, int y0, int x1, int y1, boolean outerRight, int behind) {
        wornEdges(painter, x0, y0, x1, y1, outerRight, behind, 1);
    }

    /**
     * As {@link #wornEdges(GuidePainter, int, int, int, int, boolean, int)}, as worn as {@code wear} says: 1 lightly,
     * more for a well-thumbed book, its yellowing deeper and stronger, its nicks more and bigger, more stains, and its
     * outer corners rubbed dark.
     */
    public static void wornEdges(GuidePainter painter, int x0, int y0, int x1, int y1, boolean outerRight, int behind, float wear) {
        int seed = outerRight ? 71 : 37;
        int strength = Math.round(40 * wear);
        int extra = Math.round(wear) - 1;
        // Along the head and the foot: runs of a few pixels, each yellowed to its own depth, fading inwards.
        for (int x = x0; x < x1; ) {
            int h = hash(seed, x - x0);
            int end = Math.min(x1, x + 3 + (h & 7));
            stain(painter, x, end, y0, 1, 1 + ((h >>> 4) & 1) + extra, strength);
            stain(painter, x, end, y1 - 1, -1, 1 + ((h >>> 7) & 1) + extra, strength);
            x = end;
        }
        // Along the outer side, the same, sideways.
        int outer = outerRight ? x1 - 1 : x0;
        int inward = outerRight ? -1 : 1;
        for (int y = y0; y < y1; ) {
            int h = hash(seed * 3, y - y0);
            int end = Math.min(y1, y + 3 + (h & 7));
            int deep = 1 + ((h >>> 4) & 1) + extra;
            for (int step = 0; step < deep; step++) {
                int column = outer + inward * step;
                painter.fill(column, y, column + 1, end, alpha(AGED, strength - step * strength * 3 / 8));
            }
            // Now and then a small nick out of the edge, what is behind the page showing in it; on a well-worn page
            // its outer side is ragged all along, a tear in about one run of four, longer and deeper.
            if (((h >>> 9) & (wear > 1 ? 3 : 31)) == 0) {
                int bite = Math.min(end - y, wear > 1 ? 2 + ((h >>> 13) & 3) + ((h >>> 15) & 1) : 1 + ((h >>> 13) & 1));
                int depth = wear > 1 ? Math.min(Math.round(wear), 1 + ((h >>> 17) & 3)) : 1;
                for (int step = 0; step < depth && bite - 2 * step > 0; step++) {
                    int column = outer + inward * step;
                    painter.fill(column, y + step, column + 1, y + bite - step, behind);
                }
                int rim = outer + inward * depth;
                painter.fill(rim, y, rim + 1, y + bite, alpha(AGED, Math.round(70 * Math.min(wear, 1.6F))));
            }
            y = end;
        }
        // A few faint stains, soft-edged, here and there.
        for (int spot = 0; spot < 2 + extra * 2; spot++) {
            int h = hash(seed * 7, spot);
            int sx = x0 + 10 + (h & 0x7FFF) % Math.max(1, x1 - x0 - 26);
            int sy = y0 + 14 + ((h >>> 15) & 0x7FFF) % Math.max(1, y1 - y0 - 34);
            int size = 3 + ((h >>> 11) & 3);
            painter.fill(sx, sy, sx + size, sy + size - 1, alpha(AGED, Math.round(12 * wear)));
            painter.fill(sx + 1, sy - 1, sx + size - 1, sy + size, alpha(AGED, Math.round(9 * wear)));
        }
        // The outer corners, thumbed: rubbed darker in a little triangle where the page is turned.
        if (wear > 1) {
            int size = 3 + extra * 2;
            for (int corner = 0; corner < 2; corner++) {
                int cy = corner == 0 ? y0 : y1 - 1;
                int down = corner == 0 ? 1 : -1;
                for (int row = 0; row < size; row++) {
                    int across = size - row;
                    int from = outerRight ? x1 - across : x0;
                    painter.fill(from, cy + down * row, from + across, cy + down * row + 1, alpha(AGED, Math.round(strength * 0.6F * across / size)));
                }
            }
        }
    }

    // A strip of yellowing from an edge row inwards, `deep` rows, fading.
    private static void stain(GuidePainter painter, int x0, int x1, int y, int inward, int deep, int strength) {
        for (int step = 0; step < deep; step++) {
            int row = y + inward * step;
            painter.fill(x0, row, x1, row + 1, alpha(AGED, strength - step * strength * 3 / 8));
        }
    }

    private static int alpha(int color, int alpha) {
        return GuidePalette.alpha(color, Math.max(0, alpha));
    }

    // A small, well-mixed number from two: the same every frame for the same place.
    private static int hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & 0x7FFFFFFF;
    }

    /**
     * How deep among the leaves a tab is tucked, in leaves: the top one just under the surface, each below a little
     * deeper; the open one (and, part way, one under the pointer) rises to the surface. {@code order} is its place
     * from the top, {@code out} how far it is out ({@code TabMotion.out}).
     */
    public static int tabDepth(int order, float out) {
        return tabDepth(order, out, FORE_EDGE);
    }

    /** The same, in a fore-edge {@code leaves} wide. */
    public static int tabDepth(int order, float out, int leaves) {
        float depth = Math.min(order * 0.5F, leaves - 1);
        return Math.round(depth * (1 - Math.max(0, Math.min(1, out))));
    }

    /** Where a ribbon tucked {@code depth} leaves deep comes out of the fore-edge at the page's {@code edge}. */
    public static int ribbonRoot(int edge, int depth) {
        return edge + 1 + depth;
    }

    /**
     * Over a ribbon's root, the leaves above it: they lie on it from the page's {@code edge} to where it comes out of
     * the block, so it is seen between the leaves at its depth; and a soft shadow they cast on it as it leaves them.
     * {@code ribbon} is the tab's colour, for its shadow.
     */
    public static void tuck(GuidePainter painter, int edge, int y, int height, int depth, int ribbon) {
        GuidePalette palette = painter.palette();
        int root = ribbonRoot(edge, depth);
        for (int leaf = 0; edge + leaf < root; leaf++) painter.fill(edge + leaf, y, edge + leaf + 1, y + height, leaf(palette, leaf));
        painter.fill(root, y, root + 1, y + height, GuidePalette.mix(ribbon, 0xFF000000, 0.34F));
        painter.fill(root + 1, y, root + 2, y + height, GuidePalette.mix(ribbon, 0xFF000000, 0.2F));
        painter.fill(root + 2, y, root + 3, y + height, GuidePalette.mix(ribbon, 0xFF000000, 0.08F));
        // Where the leaves above press on it, a hairline shade along its top and foot.
        painter.fill(root, y, root + 4, y + 1, GuidePalette.mix(ribbon, 0xFF000000, 0.25F));
        painter.fill(root, y + height - 1, root + 4, y + height, GuidePalette.mix(ribbon, 0xFF000000, 0.25F));
    }

    private static int frameTint(GuidePainter painter) {
        GuidePalette palette = painter.palette();
        return GuidePalette.mix(palette.line(), palette.accent(), 0.35F) | 0xFF000000;
    }

    /** The little controls printed on pages: each a pixel icon on a clasp ('X' in its ink, 'o' a softer shade). */
    public enum Icon {
        NEXT(PixelIcon.of("button_next", 3, 5)),
        PREVIOUS(PixelIcon.of("button_previous", 3, 5)),
        BACK(PixelIcon.of("button_back", 5, 5)),
        CLOSE(PixelIcon.of("button_close", 5, 5)),
        // A ring turning back on itself, its arrowhead at the top right.
        RESET(PixelIcon.of("button_reset", 7, 7)),
        // An open eye: its lids, the iris softly round a dark pupil.
        EYE(PixelIcon.of("button_eye", 7, 5)),
        // Three lines, each with a dot before it: a list.
        BLOCKS(PixelIcon.of("button_blocks", 7, 5));

        private final PixelIcon image;

        Icon(PixelIcon image) {
            this.image = image;
        }
    }

    /** How big a clasp is, square. */
    public static final int BUTTON = 11;

    /**
     * A control: an eight-sided clasp of the page's own paper with a hairline rim and its icon in the middle. Under the
     * pointer it lifts off the page, casting a little shadow, and its icon takes the book's colour.
     */
    public static void button(GuidePainter painter, int x, int y, Icon icon, boolean hovered) {
        GuidePalette palette = painter.palette();
        int rim = GuidePalette.mix(palette.line(), palette.accent(), hovered ? 0.6F : 0.2F);
        int face = GuidePalette.mix(GuidePalette.mix(palette.panel(), palette.paper(), 0.4F), palette.accent(), hovered ? 0.14F : 0.0F);
        if (hovered) {
            octagon(painter, x + 1, y + 1, BUTTON, GuidePalette.mix(palette.paper(), 0xFF000000, 0.22F), 0);
            y -= 1;
        }
        octagon(painter, x, y, BUTTON, rim, face);
        int ink = hovered ? palette.accent() : GuidePalette.mix(palette.muted(), palette.text(), 0.35F);
        // Centred in the clasp by the pixels it actually draws (odd sizes sit exactly in the middle).
        icon.image.draw(painter, x + (BUTTON - icon.image.width()) / 2, y + (BUTTON - icon.image.height()) / 2, ink);
    }

    /**
     * A clasp {@code width} wide with a number on it in place of an icon, for picking one of several things a page
     * shows. The one picked is pressed into the page, its face of the book's colour; another lifts under the pointer.
     */
    public static void numberButton(GuidePainter painter, int x, int y, int width, String number, boolean hovered, boolean picked) {
        GuidePalette palette = painter.palette();
        int rim = GuidePalette.mix(palette.line(), palette.accent(), picked || hovered ? 0.6F : 0.2F);
        int face = picked ? GuidePalette.mix(palette.panel(), palette.accent(), 0.4F)
                : GuidePalette.mix(GuidePalette.mix(palette.panel(), palette.paper(), 0.4F), palette.accent(), hovered ? 0.14F : 0.0F);
        if (hovered && !picked) {
            octagon(painter, x + 1, y + 1, width, GuidePalette.mix(palette.paper(), 0xFF000000, 0.22F), 0);
            y -= 1;
        }
        octagon(painter, x, y, width, rim, face);
        int ink = picked ? palette.heading() : hovered ? palette.accent() : GuidePalette.mix(palette.muted(), palette.text(), 0.35F);
        net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.literal(number);
        painter.text(text, x + (width - painter.font().width(text)) / 2 + 1, y + 2, ink);
    }

    // An eight-sided shape `width` wide and BUTTON tall: its corners cut by two pixels. With a face, the rim is a
    // pixel round it; without one (0), it is all of the rim's colour.
    private static void octagon(GuidePainter painter, int x, int y, int width, int rim, int face) {
        int size = BUTTON;
        painter.fill(x + 2, y, x + width - 2, y + size, rim);
        painter.fill(x, y + 2, x + width, y + size - 2, rim);
        painter.fill(x + 1, y + 1, x + width - 1, y + size - 1, rim);
        if (face == 0) return;
        painter.fill(x + 2, y + 1, x + width - 2, y + size - 1, face);
        painter.fill(x + 1, y + 2, x + width - 1, y + size - 2, face);
    }

    /** A small arrow pointing right, 9 by 7 pixels from (x, y). */
    public static void arrow(GuidePainter painter, int x, int y, int color) {
        painter.fill(x, y + 3, x + 6, y + 4, color);
        for (int step = 0; step < 4; step++) painter.fill(x + 5 + step, y + step, x + 6 + step, y + 7 - step, color);
    }
}
