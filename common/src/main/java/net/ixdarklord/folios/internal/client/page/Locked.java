package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosLock;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.paint.ObscuredPainter;
import net.ixdarklord.folios.internal.client.paint.PixelIcon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Locked {
    private Locked() {}

    private static final PixelIcon LOCK = PixelIcon.of("lock", 9, 12);
    private static final int PAD = 6;
    private static final int SEAL = 11;
    private static final int SEAL_ROOM = 2 * SEAL + 1;
    private static final int CAPTION = 8;
    private static final float SMALL = 0.75F;
    private static final int ROW = 9;
    private static final int SMALL_LINE = 7;
    private static final int ROW_GAP = 3;
    private static final int BAR = 7;
    private static final int HEADING = 9;
    private static final int LIST_TOP = 2;
    private static final int FRAME_IN = 3;
    private static final java.util.Map<FoliosLock, float[]> EASED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final int FADE = 5;
    private static final java.util.Map<FoliosLock, int[]> SCROLLED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<FoliosLock, float[]> PULLED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static FoliosPageElement.Laid page(FoliosLock lock, int seed, FoliosPageElement.@Nullable Laid under) {
        return painter -> {
            if (under != null) under.draw(new ObscuredPainter(painter, lock.peek()));
            above(painter, () -> backdrop(painter));
            above(painter, () -> draw(painter, lock, seed));
        };
    }

    public static void over(FoliosPainter painter, int y, int height, FoliosPageElement.@Nullable Part under, FoliosLock lock, int seed) {
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        if (under != null) painter.clip(left, y, right - left, height, () -> under.draw(new ObscuredPainter(painter, lock.peek()), y));
        above(painter, () -> backdrop(painter));
        above(painter, () -> draw(painter, lock, seed));
    }

    private static int foot(FoliosPainter painter, FoliosLock lock) {
        FoliosBook book = lock.book() != null ? FoliosLibrary.book(lock.book()) : null;
        if (book == null) return painter.contentBottom();
        FoliosBook.Layout layout = book.layout();
        return painter.pageHeight() - Math.max(layout.buttonY(), layout.numberY()) - 3;
    }

    private static void backdrop(FoliosPainter painter) {
        int color = light(painter.palette().paper()) ? 0x96FFFFFF : 0x78000000;
        int x0 = Shapes.FRAME_INSIDE;
        int y0 = Shapes.FRAME_INSIDE;
        int x1 = painter.pageWidth() - Shapes.FRAME_INSIDE;
        int y1 = painter.pageHeight() - Shapes.FRAME_INSIDE;
        for (int ring = 0; ring < BACKDROP_FADE; ring++) {
            float t = (ring + 1) / (float) (BACKDROP_FADE + 1);
            int shade = FoliosPalette.alpha(color, Math.round((color >>> 24) * t * t * (3 - 2 * t)));
            int ax = x0 + ring, ay = y0 + ring, bx = x1 - ring, by = y1 - ring;
            painter.fill(ax + 1, ay, bx - 1, ay + 1, shade);
            painter.fill(ax + 1, by - 1, bx - 1, by, shade);
            painter.fill(ax, ay + 1, ax + 1, by - 1, shade);
            painter.fill(bx - 1, ay + 1, bx, by - 1, shade);
        }
        painter.fill(x0 + BACKDROP_FADE, y0 + BACKDROP_FADE, x1 - BACKDROP_FADE, y1 - BACKDROP_FADE, color);
    }

    private static final int BACKDROP_FADE = 5;

    private static boolean light(int argb) {
        return (argb >> 16 & 0xFF) * 299 + (argb >> 8 & 0xFF) * 587 + (argb & 0xFF) * 114 > 128_000;
    }

    private static void draw(FoliosPainter painter, FoliosLock lock, int seed) {
        int left = Shapes.FRAME_INSIDE;
        int right = painter.pageWidth() - Shapes.FRAME_INSIDE;
        int y = Shapes.FRAME_INSIDE;
        int bottom = foot(painter, lock);
        FoliosPalette palette = painter.palette();
        Font font = painter.font();
        int accent = palette.accent() | 0xFF000000;
        rim(painter, left, y, right, bottom + 2, accent);

        int cx = (left + right) / 2;
        int drop = 2;
        int cy = y + PAD + SEAL + drop;
        float glow = 0.5F + 0.5F * (float) Math.sin(painter.time() * 2.2F);
        int rule = FoliosPalette.alpha(palette.line(), 0xB0);
        faded(painter, left + PAD + 8, cx - SEAL - 4, cy, rule, true);
        faded(painter, cx + SEAL + 4, right - PAD - 8, cy, rule, false);
        ornament(painter, left + PAD + 3, cy, accent);
        ornament(painter, right - PAD - 4, cy, accent);
        seal(painter, cx, cy, accent, palette.paper() | 0xFF000000, glow);
        int at = y + PAD + SEAL_ROOM + drop;

        String caption = Component.translatableWithFallback("folios.locked", "Not yet discovered").getString();
        float captionScale = 0.75F;
        glinting(painter, caption, cx, at + 3, captionScale, palette.muted(), accent, seed, true);
        at += CAPTION + 3;

        List<FoliosObjective> requires = lock.requires();
        FoliosBook book = lock.book() != null ? FoliosLibrary.book(lock.book()) : null;
        if (requires.isEmpty() || book == null || !lock.showUnlock()) return;
        int frameBottom = bottom - PAD;
        int closed = at + 4;
        int need = HEADING + LIST_TOP + rows(lock, book, right - left - 2 * PAD - 2 * FRAME_IN, false).total() + 2;
        closed = Math.max(closed, frameBottom - need);
        int frameTop = closed;
        above(painter, () -> frame(painter, lock, book, left, right, frameTop, frameBottom, accent, seed));
    }

    private static void frame(FoliosPainter painter, FoliosLock lock, FoliosBook book, int left, int right, int top, int bottom, int accent, int seed) {
        FoliosPalette palette = painter.palette();
        Font font = painter.font();
        int x0 = left + PAD;
        int x1 = right - PAD;
        int ruleY = top + 4;
        FormattedCharSequence heading = Component.translatableWithFallback("folios.locked.needs", "How to unlock").getVisualOrderText();
        int headingWidth = Math.round(font.width(heading) * SMALL);
        int cx = (x0 + x1) / 2;
        int nx0 = cx - headingWidth / 2 - 4;
        int nx1 = cx + headingWidth / 2 + 4;
        int nTop = top - 2;
        int line = FoliosPalette.alpha(palette.line(), 0xC0);
        int panel = palette.panel() | 0xFF000000;
        inside(painter, nx0 + 1, nTop + 1, nx1 + 1, ruleY + 3, 0x50000000);
        inside(painter, x0 + 1, ruleY + 1, x1 + 1, bottom + 1, 0x50000000);
        inside(painter, nx0, nTop, nx1, ruleY + 3, panel);
        inside(painter, x0, ruleY, x1, bottom, panel);
        glinting(painter, Component.translatableWithFallback("folios.locked.needs", "How to unlock").getString(), cx, top + 1, SMALL, palette.heading(), accent, seed + 1, false);
        painter.fill(x0 + 2, ruleY, nx0, ruleY + 1, line);
        painter.fill(nx1, ruleY, x1 - 2, ruleY + 1, line);
        painter.fill(nx0 + 2, nTop, nx1 - 2, nTop + 1, line);
        painter.fill(nx0 + 1, nTop + 1, nx0 + 2, nTop + 2, line);
        painter.fill(nx1 - 2, nTop + 1, nx1 - 1, nTop + 2, line);
        painter.fill(nx0, nTop + 2, nx0 + 1, ruleY + 1, line);
        painter.fill(nx1 - 1, nTop + 2, nx1, ruleY + 1, line);
        painter.fill(x0 + 1, ruleY + 1, x0 + 2, ruleY + 2, line);
        painter.fill(x1 - 2, ruleY + 1, x1 - 1, ruleY + 2, line);
        painter.fill(x0, ruleY + 2, x0 + 1, bottom - 2, line);
        painter.fill(x1 - 1, ruleY + 2, x1, bottom - 2, line);
        painter.fill(x0 + 1, bottom - 2, x0 + 2, bottom - 1, line);
        painter.fill(x1 - 2, bottom - 2, x1 - 1, bottom - 1, line);
        painter.fill(x0 + 2, bottom - 1, x1 - 2, bottom, line);
        list(painter, lock, book, x0 + FRAME_IN, x1 - FRAME_IN, top + HEADING, bottom - 2, accent);
    }

    private static final net.minecraft.network.chat.Style ITALIC = net.minecraft.network.chat.Style.EMPTY.withItalic(true);
    private static final net.minecraft.network.chat.Style RUNES = ITALIC.withFont(new net.minecraft.resources.ResourceLocation("alt"));
    private static final net.minecraft.network.chat.Style UPRIGHT = net.minecraft.network.chat.Style.EMPTY;
    private static final net.minecraft.network.chat.Style UPRIGHT_RUNES = RUNES.withItalic(false);
    private static final float GLINT_CYCLE = 6;
    private static final float GLINT_SWEEP = 1.4F;
    private static final float GLINT_REACH = 1.8F;

    private static void glinting(FoliosPainter painter, String text, int cx, int y, float scale, int color, int accent, int seed, boolean italic) {
        net.minecraft.network.chat.Style face = italic ? ITALIC : UPRIGHT;
        net.minecraft.network.chat.Style runes = italic ? RUNES : UPRIGHT_RUNES;
        Font font = painter.font();
        int[] widths = new int[text.length()];
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            widths[i] = font.width(FormattedCharSequence.forward(String.valueOf(text.charAt(i)), face));
            total += widths[i];
        }
        float time = painter.time();
        int cycle = (int) Math.floor(time / GLINT_CYCLE);
        float start = (mix(seed, cycle) >>> 8) / (float) (1 << 24) * (GLINT_CYCLE - GLINT_SWEEP);
        float into = time - cycle * GLINT_CYCLE - start;
        boolean sweeping = into >= 0 && into <= GLINT_SWEEP;
        float at = sweeping ? into / GLINT_SWEEP * (text.length() + 2 * GLINT_REACH) - GLINT_REACH : Float.NaN;
        int flicker = (int) Math.floor(time * 14);
        int glint = FoliosPalette.mix(accent | 0xFF000000, 0xFFFFFFFF, 0.25F);
        painter.push();
        painter.translate(cx - total * scale / 2, y);
        painter.scale(scale);
        int x = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float near = sweeping && c != ' ' ? 1 - Math.abs(i - at) / GLINT_REACH : 0;
            if (near > 0) {
                char rune = (char) ('a' + Math.floorMod(mix(seed + i, flicker), 26));
                FormattedCharSequence shown = FormattedCharSequence.forward(String.valueOf(rune), runes);
                int ink = FoliosPalette.mix(color | 0xFF000000, glint, near);
                painter.text(shown, x + (widths[i] - font.width(shown)) / 2, 0, FoliosPalette.alpha(ink, color >>> 24));
            } else {
                painter.text(FormattedCharSequence.forward(String.valueOf(c), face), x, 0, color);
            }
            x += widths[i];
        }
        painter.pop();
    }

    private static int mix(int a, int b) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h;
    }

    private static void faded(FoliosPainter painter, int x0, int x1, int y, int argb, boolean fromLeft) {
        int fade = Math.min(12, (x1 - x0) / 2);
        int alpha = argb >>> 24;
        for (int x = x0; x < x1; x++) {
            int from = fromLeft ? x - x0 : x1 - 1 - x;
            float t = from >= fade ? 1 : (from + 0.5F) / fade;
            painter.fill(x, y, x + 1, y + 1, FoliosPalette.alpha(argb, Math.round(alpha * t * t)));
        }
    }

    private static void inside(FoliosPainter painter, int x0, int y0, int x1, int y1, int color) {
        painter.fill(x0 + 2, y0, x1 - 2, y1, color);
        painter.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, color);
        painter.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, color);
        painter.fill(x0, y0 + 2, x0 + 1, y1 - 2, color);
        painter.fill(x1 - 1, y0 + 2, x1, y1 - 2, color);
    }


    private static void list(FoliosPainter painter, FoliosLock lock, FoliosBook book, int x0, int x1, int top, int bottom, int accent) {
        FoliosPalette palette = painter.palette();
        List<FoliosObjective> requires = lock.requires();
        int window = bottom - top;
        int content = window - LIST_TOP;
        Rows wide = rows(lock, book, x1 - x0, false);
        Rows rows = wide.total() <= content ? wide : rows(lock, book, x1 - x0, true);
        int[] first = SCROLLED.computeIfAbsent(lock, key -> new int[1]);
        int last = rows.scrolls() ? rows.lastFirst(content) : 0;
        first[0] = Math.max(0, Math.min(first[0], last));
        int markX = x0 + 3;
        int textX = x0 + 11;
        int barRight = x1 - 1 - (rows.scrolls() ? Shapes.SCROLLBAR + 2 : 0);
        if (rows.scrolls()) {
            painter.scroll(x0, top, x1 - x0, window, notches -> first[0] = Math.max(0, Math.min(last, first[0] - (int) Math.signum(notches))));
            int trackTop = top + 1;
            int track = window - 2;
            int thumb = Math.max(6, track * content / rows.total());
            int thumbY = trackTop + Math.round((track - thumb) * Math.min(1, rows.above(first[0]) / (float) Math.max(1, rows.total() - content)));
            int barX = x1 - Shapes.SCROLLBAR;
            float[] pulled = PULLED.computeIfAbsent(lock, key -> new float[1]);
            int travel = Math.max(1, track - thumb);
            boolean held = painter.drag(barX - 2, trackTop, Shapes.SCROLLBAR + 4, track, (dx, dy) -> {
                pulled[0] += dy * (rows.total() - content) / (float) travel;
                int to = rows.above(first[0]) + Math.round(pulled[0]);
                int nearest = first[0];
                for (int index = 0; index <= last; index++) {
                    if (Math.abs(rows.above(index) - to) < Math.abs(rows.above(nearest) - to)) nearest = index;
                }
                if (nearest != first[0]) {
                    pulled[0] -= rows.above(nearest) - rows.above(first[0]);
                    first[0] = nearest;
                }
            });
            if (!held) pulled[0] = 0;
            Shapes.scrollbar(painter, barX, trackTop, track, thumbY, thumb, held);
        }
        float[] eased = EASED.computeIfAbsent(lock, key -> new float[] {-1, 0});
        int target = rows.above(first[0]);
        float now = painter.time();
        if (eased[0] < 0 || now < eased[1]) eased[0] = target;
        else eased[0] += (target - eased[0]) * Math.min(1, (now - eased[1]) * 14);
        if (Math.abs(target - eased[0]) < 0.05F) eased[0] = target;
        eased[1] = now;
        float offset = eased[0];
        int furthest = rows.above(last);
        painter.clip(x0, top, x1 - x0, window, () -> {
            painter.push();
            painter.translate(0, -offset);
            int rowY = top + LIST_TOP;
            for (int index = 0; index < requires.size(); index++) {
                int height = rows.heights().get(index);
                if (rowY - top + height > offset && rowY - top < offset + window) row(painter, requires.get(index), book, rowY, markX, textX, barRight, accent);
                rowY += height;
            }
            painter.pop();
            int face = palette.panel();
            above(painter, () -> {
                for (int row = 0; row < FADE; row++) {
                    float t = 1 - (row + 0.5F) / FADE;
                    int color = FoliosPalette.alpha(face, Math.round(255 * t * t * (3 - 2 * t)));
                    if (offset > 0.5F) painter.fill(x0, top + row, barRight, top + row + 1, color);
                    if (offset < furthest - 0.5F) painter.fill(x0, bottom - 1 - row, barRight, bottom - row, color);
                }
            });
        });
        if (painter.hovered(x0, top, barRight - x0, window)) {
            List<Component> all = new ArrayList<>();
            all.add(Component.translatableWithFallback("folios.locked.needs", "How to unlock"));
            for (FoliosObjective condition : requires) {
                boolean done = condition.isDone(book);
                all.add(Component.literal(done ? "✔ " : "◇ ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        .append(condition.label(book).copy().withStyle(done ? ChatFormatting.GRAY : ChatFormatting.WHITE)));
            }
            painter.tooltip(all);
        }
    }

    private static void row(FoliosPainter painter, FoliosObjective condition, FoliosBook book, int y, int markX, int textX, int barRight, int accent) {
        FoliosPalette palette = painter.palette();
        Font font = painter.font();
        float progress = condition.progress(book);
        boolean done = progress >= 1;
        if (done) check(painter, markX, y + 2);
        else hollow(painter, markX, y + 2, palette.paper() | 0xFF000000);
        for (FormattedCharSequence line : font.split(condition.label(book), Math.max(20, Math.round((barRight - textX) / SMALL)))) {
            small(painter, line, textX, y, done ? palette.muted() : palette.text());
            y += SMALL_LINE;
        }
        y += ROW_GAP;
        if (counts(condition)) {
            QuestPageElement.stepped(painter, textX, y - 1, barRight - textX, progress, Math.min(10, condition.goal()), done ? 0xFF4CAF50 : accent);
        }
    }

    private static void above(FoliosPainter painter, Runnable draw) {
        if (painter instanceof net.ixdarklord.folios.internal.client.paint.AbstractPainter drawing) drawing.above(draw);
        else draw.run();
    }

    private record Rows(List<Integer> heights, int total, boolean scrolls) {
        int above(int index) {
            int above = 0;
            for (int at = 0; at < index && at < heights.size(); at++) above += heights.get(at);
            return above;
        }

        int lastFirst(int window) {
            int below = 0;
            for (int index = heights.size() - 1; index >= 0; index--) {
                below += heights.get(index);
                if (below > window) return Math.min(heights.size() - 1, index + 1);
            }
            return 0;
        }
    }

    private static Rows rows(FoliosLock lock, FoliosBook book, int width, boolean narrow) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int room = width - 12 - (narrow ? Shapes.SCROLLBAR + 2 : 0);
        List<Integer> heights = new ArrayList<>();
        int total = 0;
        for (FoliosObjective condition : lock.requires()) {
            int lineCount = font.split(condition.label(book), Math.max(20, Math.round(room / SMALL))).size();
            int height = lineCount * SMALL_LINE + ROW_GAP + (counts(condition) ? BAR : 0);
            heights.add(height);
            total += height;
        }
        return new Rows(List.copyOf(heights), total, narrow);
    }

    private static boolean counts(FoliosObjective condition) {
        return !condition.isAllOrNothing();
    }

    private static void rim(FoliosPainter painter, int x0, int y0, int x1, int y1, int accent) {
        int in = 2;
        int stitch = FoliosPalette.alpha(accent, 0x50);
        for (int x = x0 + in + 4; x < x1 - in - 4; x += 4) {
            painter.fill(x, y0 + in, x + 2, y0 + in + 1, stitch);
            painter.fill(x, y1 - in - 1, x + 2, y1 - in, stitch);
        }
        for (int y = y0 + in + 4; y < y1 - in - 4; y += 4) {
            painter.fill(x0 + in, y, x0 + in + 1, y + 2, stitch);
            painter.fill(x1 - in - 1, y, x1 - in, y + 2, stitch);
        }
        int corner = FoliosPalette.alpha(accent, 0xB0);
        diamond(painter, x0 + in + 1, y0 + in + 1, corner);
        diamond(painter, x1 - in - 2, y0 + in + 1, corner);
        diamond(painter, x0 + in + 1, y1 - in - 2, corner);
        diamond(painter, x1 - in - 2, y1 - in - 2, corner);
    }

    private static void seal(FoliosPainter painter, int cx, int cy, int accent, int paper, float glow) {
        disc(painter, cx, cy, SEAL + 2, FoliosPalette.alpha(accent, Math.round(0x10 + 0x18 * glow)));
        int wax = FoliosPalette.mix(accent, 0xFF000000, 0.3F);
        int edge = FoliosPalette.mix(accent, 0xFF000000, 0.55F);
        int sheen = FoliosPalette.mix(accent, 0xFFFFFFFF, 0.2F);
        int face = FoliosPalette.mix(accent, 0xFF000000, 0.42F);
        int sunk = FoliosPalette.mix(accent, 0xFF000000, 0.62F);
        int raised = FoliosPalette.mix(accent, 0xFFFFFFFF, 0.15F);
        for (int dy = -SEAL - 1; dy <= SEAL + 1; dy++) {
            for (int dx = -SEAL - 1; dx <= SEAL + 1; dx++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                double reach = SEAL - 0.3 + 0.7 * Math.sin(angle * 5 + 0.6) + 0.35 * Math.sin(angle * 3 - 1.1);
                if (d > reach + 0.5) continue;
                int color;
                if (d > reach - 0.5) color = dx + dy < -2 ? sheen : edge;
                else if (d > SEAL - 3.5 && d <= SEAL - 2.5) color = dx + dy < 0 ? sunk : raised;
                else if (d <= SEAL - 3.5) color = face;
                else color = wax;
                painter.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
        LOCK.draw(painter, cx - 4, cy - 5, FoliosPalette.alpha(0xFF000000, 0x90));
        LOCK.draw(painter, cx - 4, cy - 6, FoliosPalette.mix(paper, 0xFFFFFFFF, 0.5F));
    }

    private static void disc(FoliosPainter painter, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, (r + 0.5) * (r + 0.5) - dy * dy)) - 0.5);
            painter.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
        }
    }

    private static final String[] ORNAMENT = {
            "..a..",
            ".abd.",
            "abcdd",
            ".add.",
            "..a..",
    };

    private static void ornament(FoliosPainter painter, int x, int y, int accent) {
        pixels(painter, x, y, ORNAMENT,
                FoliosPalette.mix(accent, 0xFF000000, 0.3F),
                FoliosPalette.mix(accent, 0xFFFFFFFF, 0.45F),
                FoliosPalette.mix(accent, 0xFFFFFFFF, 0.8F),
                accent);
    }

    private static void ring(FoliosPainter painter, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt((r + 0.5) * (r + 0.5) - dy * dy));
            int inner = Math.abs(dy) >= r ? -1 : (int) Math.floor(Math.sqrt((r - 0.5) * (r - 0.5) - dy * dy));
            if (inner < 0) {
                painter.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, argb);
            } else {
                painter.fill(cx - half, cy + dy, cx - inner, cy + dy + 1, argb);
                painter.fill(cx + inner + 1, cy + dy, cx + half + 1, cy + dy + 1, argb);
            }
        }
    }

    private static void diamond(FoliosPainter painter, int x, int y, int argb) {
        painter.fill(x, y - 1, x + 1, y + 2, argb);
        painter.fill(x - 1, y, x + 2, y + 1, argb);
    }

    private static void pixels(FoliosPainter painter, int x, int y, String[] rows, int... colors) {
        int top = y - rows.length / 2;
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int left = x - line.length() / 2;
            for (int column = 0; column < line.length(); column++) {
                char c = line.charAt(column);
                if (c == '.') continue;
                painter.fill(left + column, top + row, left + column + 1, top + row + 1, colors[c - 'a']);
            }
        }
    }

    private static final String[] EMPTY = {
            "..aaa..",
            ".acdda.",
            "acdddba",
            "acdddba",
            "acdddba",
            ".abbba.",
            "..aaa..",
    };

    private static void hollow(FoliosPainter painter, int x, int y, int paper) {
        int grey = FoliosPalette.mix(0xFF9E9E9E, paper, 0.25F);
        pixels(painter, x, y, EMPTY,
                FoliosPalette.mix(grey, 0xFF000000, 0.35F),
                FoliosPalette.mix(grey, 0xFFFFFFFF, 0.35F),
                FoliosPalette.mix(grey, 0xFF000000, 0.18F),
                grey);
    }

    private static final String[] BADGE = {
            "..aaaaa..",
            ".abbbbba.",
            "abbbbbeca",
            "abbbbeeca",
            "abebeedca",
            "abeeedcca",
            "accedccca",
            ".acdccca.",
            "..aaaaa..",
    };

    private static void check(FoliosPainter painter, int x, int y) {
        int green = 0xFF4CAF50;
        pixels(painter, x, y, BADGE,
                FoliosPalette.mix(green, 0xFF000000, 0.45F),
                FoliosPalette.mix(green, 0xFFFFFFFF, 0.25F),
                FoliosPalette.mix(green, 0xFF000000, 0.12F),
                FoliosPalette.mix(green, 0xFF000000, 0.35F),
                0xFFFFFFFF);
    }

    private static void small(FoliosPainter painter, FormattedCharSequence text, int x, int y, int color) {
        painter.push();
        painter.translate(x, y);
        painter.scale(SMALL);
        painter.text(text, 0, 0, color);
        painter.pop();
    }
}
