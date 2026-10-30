package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.internal.client.paint.PixelIcon;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.minecraft.resources.ResourceLocation;

public final class Shapes {
    private Shapes() {}

    public static void panel(FoliosPainter painter, int x0, int y0, int x1, int y1) {
        FoliosPalette palette = painter.palette();
        Sprites.draw(painter, Sprites.PANEL_BORDER, x0, y0, x1 - x0, y1 - y0, palette.line());
        Sprites.draw(painter, Sprites.PANEL_FACE, x0, y0, x1 - x0, y1 - y0, palette.panel());
    }

    public static final int SCROLLBAR = 4;

    public static void scrollbar(FoliosPainter painter, int x, int y, int height, int thumbY, int thumb, boolean over) {
        FoliosPalette palette = painter.palette();
        Sprites.draw(painter, Sprites.SCROLL_TRACK, x, y, SCROLLBAR, height, FoliosPalette.mix(palette.panel(), palette.line(), 0.55F));
        int lit = FoliosPalette.mix(palette.line(), palette.accent(), over ? 0.85F : 0.6F);
        Sprites.draw(painter, Sprites.SCROLL_THUMB, x, thumbY, SCROLLBAR, thumb, lit);
        if (thumb >= 9) Sprites.draw(painter, Sprites.SCROLL_GRIP, x, thumbY + (thumb - 3) / 2, SCROLLBAR, 3, lit);
    }

    public static void slot(FoliosPainter painter, int x, int y) {
        slot(painter, x, y, 18);
    }

    public static void slot(FoliosPainter painter, int x, int y, int size) {
        FoliosPalette palette = painter.palette();
        Sprites.draw(painter, Sprites.SLOT_BORDER, x, y, size, size, FoliosPalette.mix(palette.panel(), palette.line(), 0.55F));
        Sprites.draw(painter, Sprites.SLOT_WELL, x, y, size, size, FoliosPalette.mix(palette.panel(), palette.paper(), 0.35F));
    }

    public static void pageFrame(FoliosPainter painter, ResourceLocation texture, int width, int height) {
        frameSpan(painter, texture, 0, width, 0, FRAME_W, height, frameTint(painter));
    }

    public static final int SCREEN_SPINE = 6;
    public static final int FRAME_INSIDE = 7;
    private static final int FRAME_SIDE = 12;
    private static final int FRAME_W = FoliosLayout.PAGE_WIDTH;
    private static final int FRAME_H = FoliosLayout.PAGE_HEIGHT;
    private static final int FRAME_PLAIN = 40;
    private static final int[][] FRAME_COLUMNS = {{0, 20, 0}, {20, 58, 1}, {58, 78, 0}, {78, 116, 1}, {116, FRAME_W, 0}};
    private static final int[][] FRAME_ROWS = {{0, 20, 0}, {20, 160, 1}, {160, FRAME_H, 0}};

    public static void pageFrame(FoliosPainter painter, ResourceLocation texture, int width, int height, FoliosPageElement.@org.jetbrains.annotations.Nullable Laid page, boolean right) {
        if (!(page instanceof FoliosPageElement.SpreadHalf)) {
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

    private static void frameSpan(FoliosPainter painter, ResourceLocation texture, int x, int width, int from, int to, int height, int tint) {
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

    public static void acrossFold(FoliosPainter painter, net.minecraft.util.FormattedCharSequence line, int y, int color, boolean right) {
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

    public static final int FORE_EDGE = 3;

    private static int leaf(FoliosPalette palette, int leaf) {
        int paper = palette.paper() | 0xFF000000;
        float depth = leaf / (float) (FORE_EDGE - 1);
        float shade = (leaf % 2 == 0 ? 0.14F : 0.42F) + 0.22F * depth;
        return FoliosPalette.mix(paper, palette.line(), Math.min(1, shade));
    }

    public static void foreEdge(FoliosPainter painter, int x, int y0, int y1, boolean right) {
        foreEdge(painter, x, y0, y1, right, FORE_EDGE);
    }

    public static void foreEdge(FoliosPainter painter, int x, int y0, int y1, boolean right, int leaves) {
        FoliosPalette palette = painter.palette();
        int paper = palette.paper() | 0xFF000000;
        for (int leaf = 0; leaf < leaves; leaf++) {
            int at = right ? x + leaf : x - leaf - 1;
            int color = leaf(palette, leaf);
            int aged = FoliosPalette.mix(color, AGED, 0.2F);
            int y = y0;
            while (y < y1) {
                int h = hash(leaf * 7919 + (right ? 1 : 0), y - y0);
                int end = Math.min(y1, y + 3 + (h & 7));
                boolean nick = leaf > 0 && ((h >>> 3) & 7) < leaf;
                int tone = ((h >>> 9) % 5) < 2 ? aged : color;
                if (!nick) painter.fill(at, y, at + 1, end, ((h >>> 6) & 3) == 0 ? FoliosPalette.mix(tone, paper, 0.3F) : tone);
                y = end;
            }
        }
    }

    public static void deckle(FoliosPainter painter, int x0, int x1, int y, boolean down, int behind) {
        deckle(painter, x0, x1, y, down, behind, 1);
    }

    public static void deckle(FoliosPainter painter, int x0, int x1, int y, boolean down, int behind, float wear) {
        FoliosPalette palette = painter.palette();
        int worn = FoliosPalette.mix(palette.paper() | 0xFF000000, palette.line(), 0.28F);
        int inward = down ? -1 : 1;
        int x = x0;
        while (x < x1) {
            int h = hash(down ? 31 : 17, x - x0);
            int end = Math.min(x1, x + 2 + (h & 7));
            painter.fill(x, y, end, y + 1, worn);
            if (((h >>> 9) & 3) == 0) painter.fill(x, y + inward, Math.min(end, x + 2), y + inward + 1, worn);
            x = end;
            if (x >= x1 || ((h >>> 4) & (wear > 1 ? 1 : 3)) != 0) continue;
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

    private static final int AGED = 0xFF9A7A42;

    public static void wornEdges(FoliosPainter painter, int x0, int y0, int x1, int y1, boolean outerRight, int behind) {
        wornEdges(painter, x0, y0, x1, y1, outerRight, behind, 1);
    }

    public static void wornEdges(FoliosPainter painter, int x0, int y0, int x1, int y1, boolean outerRight, int behind, float wear) {
        int seed = outerRight ? 71 : 37;
        int strength = Math.round(40 * wear);
        int extra = Math.round(wear) - 1;
        for (int x = x0; x < x1; ) {
            int h = hash(seed, x - x0);
            int end = Math.min(x1, x + 3 + (h & 7));
            stain(painter, x, end, y0, 1, 1 + ((h >>> 4) & 1) + extra, strength);
            stain(painter, x, end, y1 - 1, -1, 1 + ((h >>> 7) & 1) + extra, strength);
            x = end;
        }
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
            if (((h >>> 9) & (wear > 1 ? 3 : 31)) == 0) {
                int bite = Math.min(end - y, wear > 1 ? 2 + ((h >>> 13) & 3) + ((h >>> 15) & 1) : 1 + ((h >>> 13) & 1));
                int depth = wear > 1 ? Math.min(Math.round(wear), 1 + ((h >>> 17) & 3)) : 1;
                int gap = wear > 1 ? behind : FoliosPalette.mix(painter.palette().paper() | 0xFF000000, behind, 0.18F);
                for (int step = 0; step < depth && bite - 2 * step > 0; step++) {
                    int column = outer + inward * step;
                    painter.fill(column, y + step, column + 1, y + bite - step, gap);
                }
                if (wear > 1) {
                    int rim = outer + inward * depth;
                    painter.fill(rim, y, rim + 1, y + bite, alpha(AGED, Math.round(70 * Math.min(wear, 1.6F))));
                }
            }
            y = end;
        }
        for (int spot = 0; spot < 2 + extra * 2; spot++) {
            int h = hash(seed * 7, spot);
            int sx = x0 + 10 + (h & 0x7FFF) % Math.max(1, x1 - x0 - 26);
            int sy = y0 + 14 + ((h >>> 15) & 0x7FFF) % Math.max(1, y1 - y0 - 34);
            int size = 3 + ((h >>> 11) & 3);
            painter.fill(sx, sy, sx + size, sy + size - 1, alpha(AGED, Math.round(12 * wear)));
            painter.fill(sx + 1, sy - 1, sx + size - 1, sy + size, alpha(AGED, Math.round(9 * wear)));
        }
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

    private static void stain(FoliosPainter painter, int x0, int x1, int y, int inward, int deep, int strength) {
        for (int step = 0; step < deep; step++) {
            int row = y + inward * step;
            painter.fill(x0, row, x1, row + 1, alpha(AGED, strength - step * strength * 3 / 8));
        }
    }

    private static int alpha(int color, int alpha) {
        return FoliosPalette.alpha(color, Math.max(0, alpha));
    }

    private static int hash(int a, int b) {
        int h = a * 0x27D4EB2D ^ b * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & 0x7FFFFFFF;
    }

    public static int tabDepth(int order, float out) {
        return tabDepth(order, out, FORE_EDGE);
    }

    public static int tabDepth(int order, float out, int leaves) {
        float depth = Math.min(order * 0.5F, leaves - 1);
        return Math.round(depth * (1 - Math.max(0, Math.min(1, out))));
    }

    public static int ribbonRoot(int edge, int depth) {
        return edge + 1 + depth;
    }

    public static void tuck(FoliosPainter painter, int edge, int y, int height, int depth, int ribbon) {
        FoliosPalette palette = painter.palette();
        int root = ribbonRoot(edge, depth);
        for (int leaf = 0; edge + leaf < root; leaf++) painter.fill(edge + leaf, y, edge + leaf + 1, y + height, leaf(palette, leaf));
        painter.fill(root, y, root + 1, y + height, FoliosPalette.mix(ribbon, 0xFF000000, 0.34F));
        painter.fill(root + 1, y, root + 2, y + height, FoliosPalette.mix(ribbon, 0xFF000000, 0.2F));
        painter.fill(root + 2, y, root + 3, y + height, FoliosPalette.mix(ribbon, 0xFF000000, 0.08F));
        painter.fill(root, y, root + 4, y + 1, FoliosPalette.mix(ribbon, 0xFF000000, 0.25F));
        painter.fill(root, y + height - 1, root + 4, y + height, FoliosPalette.mix(ribbon, 0xFF000000, 0.25F));
    }

    private static int frameTint(FoliosPainter painter) {
        FoliosPalette palette = painter.palette();
        return FoliosPalette.mix(palette.line(), palette.accent(), 0.35F) | 0xFF000000;
    }

    public enum Icon {
        NEXT(PixelIcon.of("button_next", 3, 5)),
        PREVIOUS(PixelIcon.of("button_previous", 3, 5)),
        BACK(PixelIcon.of("button_back", 5, 5)),
        CLOSE(PixelIcon.of("button_close", 5, 5)),
        RESET(PixelIcon.of("button_reset", 7, 7)),
        EYE(PixelIcon.of("button_eye", 7, 5)),
        BLOCKS(PixelIcon.of("button_blocks", 7, 5));

        private final PixelIcon image;

        Icon(PixelIcon image) {
            this.image = image;
        }

        public PixelIcon image() {
            return image;
        }
    }

    public static final int BUTTON = 11;

    public static void button(FoliosPainter painter, int x, int y, Icon icon, boolean hovered) {
        FoliosPalette palette = painter.palette();
        int rim = FoliosPalette.mix(palette.line(), palette.accent(), hovered ? 0.6F : 0.2F);
        int face = FoliosPalette.mix(FoliosPalette.mix(palette.panel(), palette.paper(), 0.4F), palette.accent(), hovered ? 0.14F : 0.0F);
        if (hovered) {
            octagon(painter, x + 1, y + 1, BUTTON, FoliosPalette.mix(palette.paper(), 0xFF000000, 0.22F), 0);
            y -= 1;
        }
        octagon(painter, x, y, BUTTON, rim, face);
        int ink = hovered ? palette.accent() : FoliosPalette.mix(palette.muted(), palette.text(), 0.35F);
        icon.image.draw(painter, x + (BUTTON - icon.image.width()) / 2, y + (BUTTON - icon.image.height()) / 2, ink);
    }

    public static void numberButton(FoliosPainter painter, int x, int y, int width, String number, boolean hovered, boolean picked) {
        FoliosPalette palette = painter.palette();
        int rim = FoliosPalette.mix(palette.line(), palette.accent(), picked || hovered ? 0.6F : 0.2F);
        int face = picked ? FoliosPalette.mix(palette.panel(), palette.accent(), 0.4F)
                : FoliosPalette.mix(FoliosPalette.mix(palette.panel(), palette.paper(), 0.4F), palette.accent(), hovered ? 0.14F : 0.0F);
        if (hovered && !picked) {
            octagon(painter, x + 1, y + 1, width, FoliosPalette.mix(palette.paper(), 0xFF000000, 0.22F), 0);
            y -= 1;
        }
        octagon(painter, x, y, width, rim, face);
        int ink = picked ? palette.heading() : hovered ? palette.accent() : FoliosPalette.mix(palette.muted(), palette.text(), 0.35F);
        net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.literal(number);
        painter.text(text, x + (width - painter.font().width(text)) / 2 + 1, y + 2, ink);
    }

    private static void octagon(FoliosPainter painter, int x, int y, int width, int rim, int face) {
        Sprites.draw(painter, Sprites.CLASP_BORDER, x, y, width, BUTTON, rim);
        if (face != 0) Sprites.draw(painter, Sprites.CLASP_FACE, x, y, width, BUTTON, face);
    }

    public static void arrow(FoliosPainter painter, int x, int y, int color) {
        Sprites.draw(painter, Sprites.ARROW, x, y, color);
    }
}
