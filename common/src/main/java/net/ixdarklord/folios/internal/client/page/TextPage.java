package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.FoliosLock;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosAlign;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPageElements;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record TextPage(Optional<String> title, String text, List<Item> elements, ElementLayout settings, boolean single, @Nullable Observed observed)
        implements FoliosPageElement {
    public record Item(FoliosPageElement element, ElementLayout settings, @Nullable Observed observed, @Nullable String marker, FoliosLock lock) {
        public Item(FoliosPageElement element, ElementLayout settings, @Nullable Observed observed) {
            this(element, settings, observed, null, FoliosLock.NONE);
        }
    }

    private static final Codec<Item> ELEMENT = ExtraCodecs.JSON.comapFlatMap(TextPage::element, item -> new JsonObject());

    public static final MapCodec<TextPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FoliosText.CODEC.optionalFieldOf("title").forGetter(TextPage::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(TextPage::text),
            ELEMENT.listOf(0, 64).optionalFieldOf("elements", List.of()).forGetter(TextPage::elements)
    ).apply(instance, (title, text, elements) -> new TextPage(title, text, elements, ElementLayout.NONE, false, null)));

    private static final int GAP = 6;
    private static final int PAGER = 4 + 10;

    public static TextPage single(FoliosPageElement element, ElementLayout settings, @Nullable Observed observed) {
        return new TextPage(Optional.empty(), "", List.of(new Item(element, settings, observed)), ElementLayout.NONE, true, null);
    }

    public TextPage with(ElementLayout settings, @Nullable Observed observed) {
        return new TextPage(title, text, elements, settings, single, observed);
    }

    private static String with(String text, @Nullable Observed observed) {
        if (observed == null) return text;
        String rows = observed.text();
        if (rows.isEmpty()) return text;
        return text.isEmpty() ? rows : text + "\n\n" + rows;
    }

    private static DataResult<Item> element(JsonElement json) {
        JsonObject object;
        if (json.isJsonPrimitive()) {
            object = new JsonObject();
            object.addProperty("text", json.getAsString());
        } else if (json.isJsonObject()) {
            object = json.getAsJsonObject();
        } else {
            return DataResult.error(() -> "an element is an object with a type, or a string of text");
        }
        var type = FoliosPageElements.id(object.has("type") ? object.get("type").getAsString() : "text");
        MapCodec<? extends FoliosPageElement> codec = FoliosPageElements.get(type);
        if (codec == null) return DataResult.error(() -> "no element type " + type);
        return codec.codec().parse(JsonOps.INSTANCE, object).flatMap(page -> {
            if (page.spread()) return DataResult.error(() -> "a spread is a page of its own, not an element");
            if (page instanceof TextPage text && !text.elements.isEmpty()) return DataResult.error(() -> "an element can't have elements of its own");
            String marker = object.has("marker") ? object.get("marker").getAsString() : null;
            return DataResult.success(new Item(page, ElementLayout.read(object, "A " + type + " element"), Observed.read(object), marker,
                    FoliosLock.read(object, FoliosLibrary.reading())));
        });
    }

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        ElementLayout page = settings.or(FoliosLibrary.layoutOf(layout.book().id()));
        FoliosIcon icon = layout.titleIcon();
        List<Group> groups = new ArrayList<>();
        Optional<String> heading = title;
        @Nullable FoliosAlign headingAlign = page.titleAlign();
        if (single) {
            Item item = elements.getFirst();
            ElementLayout own = item.settings().or(page);
            layout.alignment(own.alignment());
            List<FoliosLayout.Flow> flows = layout.capture(item.element());
            if (flows.isEmpty()) {
                layout.alignment(null);
                return item.element().lay(layout);
            }
            FoliosLayout.Flow first = flows.getFirst();
            if (first.title() != null && !first.title().isEmpty()) {
                heading = Optional.of(first.title());
                flows = new ArrayList<>(flows);
                flows.set(0, new FoliosLayout.Flow(null, first.reserved(), first.text(), first.part()));
            }
            headingAlign = own.titleAlign();
            groups.add(group(layout, own, flows, item.observed()));
            layout.alignment(null);
            page = own;
        } else if (elements.isEmpty()) {
            String full = with(text, observed);
            layout.alignment(page.alignment());
            List<Laid> flowed = layout.flow(title.orElse(null), 0, full, null);
            layout.alignment(null);
            boolean boxed = page.overflow() != null && page.overflow() != ElementLayout.Overflow.FLOW || page.height() != null;
            boolean placed = page.anchor() != null && page.anchor() != ElementLayout.Anchor.TOP || page.titleAlign() != null || boxed;
            if (flowed.isEmpty() || !placed) return flowed;
            layout.alignment(page.alignment());
            if (!full.isEmpty()) groups.add(new Group(page, List.of(Words.of(layout, full))));
            layout.alignment(null);
        } else {
            String words = with(text, observed);
            if (!words.isEmpty()) {
                layout.alignment(page.alignment());
                groups.add(new Group(page, List.of(Words.of(layout, words))));
            }
            layout.titleIcon(FoliosIcon.NONE);
            for (int index = 0; index < elements.size(); index++) {
                Item item = elements.get(index);
                if (!item.lock().isVisible()) continue;
                ElementLayout own = item.settings().or(page);
                layout.alignment(own.alignment());
                Group group;
                if (item.lock().isOpen()) {
                    List<FoliosLayout.Flow> flows = layout.capture(item.element());
                    if (flows.isEmpty()) Folios.LOGGER.warn("A page's element ({}) lays itself out in a way an element can't be", item.element().getClass().getSimpleName());
                    group = group(layout, own, flows, item.observed());
                } else {
                    group = locked(layout, own, item.element(), item.lock(), index, layout.contentBottom());
                }
                List<String> marks = new ArrayList<>();
                marks.add("@" + index);
                if (item.marker() != null) marks.add(item.marker());
                groups.add(group.marked(marks));
            }
            layout.alignment(null);
            layout.titleIcon(icon);
        }
        return set(layout, heading, icon, headingAlign, groups, page.anchor(), layout.contentBottom());
    }

    private static Group locked(FoliosLayout layout, ElementLayout own, FoliosPageElement element, FoliosLock lock, int seed, int bottom) {
        List<Piece> pieces = new ArrayList<>();
        List<Piece> real = lock.peek() > 0 ? group(layout, own, layout.capture(element), null).pieces() : List.of();
        Part found = null;
        for (Piece piece : real) {
            if (piece instanceof Fixed fixed && fixed.heading()) {
                found = fixed.part();
                break;
            }
        }
        Part title = found;
        Part heading = (painter, y) -> {
            if (title != null) title.draw(new net.ixdarklord.folios.internal.client.paint.ObscuredPainter(painter, lock.peek()), y);
        };
        pieces.add(new Fixed(FoliosLayout.TITLE_HEIGHT, heading, true));
        int fill = bottom - FoliosLayout.CONTENT_TOP - FoliosLayout.TITLE_HEIGHT - GAP;
        Part under = real.isEmpty() ? null : (painter, y) -> {
            int at = y;
            for (Piece piece : real) {
                if (piece instanceof Fixed fixed) {
                    if (fixed.heading()) continue;
                    fixed.part().draw(painter, at);
                } else if (piece instanceof Words words) {
                    words.text().draw(painter, FoliosLayout.CONTENT_LEFT, at);
                }
                at += piece.height() + GAP;
            }
        };
        pieces.add(new Fixed(fill, (painter, y) -> Locked.over(painter, y, fill, under, lock, seed)));
        return new Group(own, pieces);
    }

    private static Group group(FoliosLayout layout, ElementLayout own, List<FoliosLayout.Flow> flows, @Nullable Observed observed) {
        List<Piece> pieces = new ArrayList<>();
        for (FoliosLayout.Flow flow : flows) {
            if (flow.title() != null && !flow.title().isEmpty()) {
                String heading = flow.title();
                FoliosAlign align = own.titleAlign();
                pieces.add(new Fixed(FoliosLayout.TITLE_HEIGHT, (painter, y) -> FoliosLayout.title(painter, FoliosText.inline(heading), y, FoliosIcon.NONE, align), true));
            }
            if (flow.part() != null && flow.reserved() > 0) pieces.add(new Fixed(flow.reserved(), flow.part()));
            if (flow.text() != null && !flow.text().isEmpty()) pieces.add(Words.of(layout, flow.text()));
        }
        String rows = observed != null ? observed.text() : "";
        if (!rows.isEmpty()) {
            if (!pieces.isEmpty() && pieces.getLast() instanceof Words && !flows.isEmpty() && flows.getLast().text() != null) {
                pieces.set(pieces.size() - 1, Words.of(layout, flows.getLast().text() + "\n\n" + rows));
            } else {
                pieces.add(Words.of(layout, rows));
            }
        }
        return new Group(own, pieces);
    }

    private sealed interface Piece permits Fixed, Words {
        int height();
    }

    private record Fixed(int height, Part part, boolean heading) implements Piece {
        Fixed(int height, Part part) {
            this(height, part, false);
        }
    }

    private record Words(FoliosText text, java.util.function.IntFunction<FoliosText> relay) implements Piece {
        static Words of(FoliosLayout layout, String raw) {
            net.minecraft.client.gui.Font font = layout.font();
            FoliosAlign align = layout.alignment();
            return new Words(layout.text(raw), width -> FoliosText.of(font, raw, width, align));
        }

        @Override
        public int height() {
            return text.height();
        }
    }

    private record Group(ElementLayout settings, List<Piece> pieces, List<String> marks) {
        Group(ElementLayout settings, List<Piece> pieces) {
            this(settings, pieces, List.of());
        }

        Group marked(List<String> marks) {
            return new Group(settings, pieces, List.copyOf(marks));
        }

        int height() {
            int height = 0;
            for (Piece piece : pieces) height += piece.height();
            return height + Math.max(0, pieces.size() - 1) * GAP;
        }
    }

    private record Placed(int y, int height, Part part) {}

    private static final class Setter {
        final List<List<Placed>> pages = new ArrayList<>();
        List<Placed> page = new ArrayList<>();
        final int bottom;
        int y = FoliosLayout.CONTENT_TOP;
        boolean fresh = true;

        Setter(int bottom) {
            this.bottom = bottom;
            pages.add(page);
        }

        int next() {
            return fresh ? y : y + GAP;
        }

        void newPage() {
            page = new ArrayList<>();
            pages.add(page);
            y = FoliosLayout.CONTENT_TOP;
            fresh = true;
        }

        void put(int at, int height, Part part) {
            page.add(new Placed(at, height, part));
            y = at + height;
            fresh = false;
        }
    }

    private static List<Laid> set(FoliosLayout layout, Optional<String> title, FoliosIcon icon, @Nullable FoliosAlign titleAlign, List<Group> groups,
                                  ElementLayout.@Nullable Anchor anchor, int bottom) {
        Setter setter = new Setter(bottom);
        if (title.isPresent() && !title.get().isEmpty()) {
            String heading = title.get();
            setter.page.add(new Placed(setter.y, FoliosLayout.TITLE_HEIGHT, (painter, py) -> FoliosLayout.title(painter, FoliosText.inline(heading), py, icon, titleAlign)));
            setter.y += FoliosLayout.TITLE_HEIGHT;
        }
        record Span(int page, int top, int bottom) {}
        java.util.Map<String, Span> spans = new java.util.LinkedHashMap<>();
        for (Group group : groups) {
            int page = setter.pages.size() - 1;
            int before = setter.page.size();
            int at = setter.next();
            place(setter, group);
            boolean here = setter.pages.get(page).size() > before;
            int start = here ? page : Math.min(page + 1, setter.pages.size() - 1);
            int top = here ? at : FoliosLayout.CONTENT_TOP;
            List<Placed> first = setter.pages.get(start);
            int end = top;
            for (int index = here ? before : 0; index < first.size(); index++) end = Math.max(end, first.get(index).y() + first.get(index).height());
            for (String mark : group.marks()) spans.putIfAbsent(mark, new Span(start, top, Math.min(end, bottom)));
        }
        List<Laid> laid = new ArrayList<>();
        int[] laidOf = new int[setter.pages.size()];
        int[] shifts = new int[setter.pages.size()];
        for (int at = 0; at < setter.pages.size(); at++) {
            List<Placed> placed = setter.pages.get(at);
            laidOf[at] = laid.size();
            if (placed.isEmpty() && !laid.isEmpty()) continue;
            int shift = 0;
            if (anchor != null && anchor != ElementLayout.Anchor.TOP && !placed.isEmpty()) {
                int used = 0;
                for (Placed one : placed) used = Math.max(used, one.y() + one.height());
                shift = Math.max(0, bottom - used) / (anchor == ElementLayout.Anchor.CENTER ? 2 : 1);
            }
            shifts[at] = shift;
            int down = shift;
            laid.add(painter -> {
                for (Placed one : placed) one.part().draw(painter, one.y() + down);
            });
        }
        for (var mark : spans.entrySet()) {
            Span span = mark.getValue();
            int down = shifts[span.page()];
            layout.mark(mark.getKey(), new FoliosLayout.Mark(Math.min(laidOf[span.page()], laid.size() - 1), span.top() + down, span.bottom() + down));
        }
        return laid;
    }

    private static void place(Setter setter, Group group) {
        ElementLayout settings = group.settings();
        ElementLayout.Height height = settings.height();
        ElementLayout.Overflow mode = settings.overflowOrFlow();
        int at = setter.next();
        int natural = group.height();
        int full = setter.bottom - FoliosLayout.CONTENT_TOP;
        if (mode == ElementLayout.Overflow.AUTO) mode = natural <= full ? ElementLayout.Overflow.KEEP : ElementLayout.Overflow.FLIP;
        boolean reserved = height != null && height.kind() != ElementLayout.Height.Kind.FIT;
        if (natural <= setter.bottom - at) {
            if (!reserved) {
                flow(setter, group.pieces());
                return;
            }
            int box = height.sized() ? height.pixels(full) : setter.bottom - at;
            if (box > setter.bottom - at && !setter.fresh) {
                setter.newPage();
                at = setter.y;
                if (height.kind() == ElementLayout.Height.Kind.FILL) box = setter.bottom - at;
            }
            box = Math.min(box, setter.bottom - at);
            setter.y = at + offset(settings.anchor(), box - natural);
            setter.fresh = true;
            flow(setter, group.pieces());
            setter.y = at + box;
            setter.fresh = false;
            return;
        }
        switch (mode) {
            case KEEP -> {
                if (!setter.fresh) setter.newPage();
                flow(setter, group.pieces());
            }
            case FLIP -> flip(setter, group);
            case SCROLL -> scroll(setter, group);
            default -> flow(setter, group.pieces());
        }
    }

    private static int offset(ElementLayout.@Nullable Anchor anchor, int spare) {
        if (anchor == null || spare <= 0) return 0;
        return switch (anchor) {
            case TOP -> 0;
            case CENTER -> spare / 2;
            case BOTTOM -> spare;
        };
    }

    private static void flow(Setter setter, List<Piece> pieces) {
        int full = setter.bottom - FoliosLayout.CONTENT_TOP;
        for (int position = 0; position < pieces.size(); position++) {
            Piece piece = pieces.get(position);
            int at = setter.next();
            int keep = 0;
            if (piece instanceof Fixed title && title.heading() && position + 1 < pieces.size()) {
                Piece next = pieces.get(position + 1);
                keep = GAP + (next instanceof Fixed part ? part.height() : Math.min(((Words) next).text().height(), 2 * FoliosText.LINE_HEIGHT));
            }
            switch (piece) {
                case Fixed fixed -> {
                    if (!setter.fresh && at + fixed.height() + keep > setter.bottom) {
                        setter.newPage();
                        at = setter.y;
                    }
                    setter.put(at, fixed.height(), fixed.part());
                }
                case Words words -> {
                    List<FoliosText> split = words.text().split(Math.max(0, setter.bottom - at), full);
                    for (int index = 0; index < split.size(); index++) {
                        FoliosText text = split.get(index);
                        if (index > 0) {
                            setter.newPage();
                            at = setter.y;
                        }
                        if (text.isEmpty()) continue;
                        setter.put(at, text.height(), (painter, py) -> text.draw(painter, FoliosLayout.CONTENT_LEFT, py));
                    }
                }
            }
        }
    }

    private static void flip(Setter setter, Group group) {
        List<Piece> pieces = group.pieces();
        int words = -1;
        for (int index = 0; index < pieces.size(); index++) {
            if (pieces.get(index) instanceof Words) {
                if (words >= 0) {
                    flow(setter, pieces);
                    return;
                }
                words = index;
            }
        }
        if (words < 0) {
            flow(setter, pieces);
            return;
        }
        ElementLayout settings = group.settings();
        int fixed = 0;
        for (int index = 0; index < words; index++) fixed += pieces.get(index).height() + GAP;
        int least = settings.leastHeight(setter.bottom - FoliosLayout.CONTENT_TOP);
        int at = setter.next();
        if (!setter.fresh && boxBottom(setter, settings, at) - (at + fixed) - PAGER < least) {
            setter.newPage();
            at = setter.y;
        }
        int bottom = boxBottom(setter, settings, at);
        setter.y = at;
        setter.fresh = at == FoliosLayout.CONTENT_TOP || setter.fresh;
        flow(setter, pieces.subList(0, words));
        int top = setter.next();
        int room = bottom - top - PAGER - 2 * FRAME_EDGE;
        Words piece = (Words) pieces.get(words);
        FoliosText text = piece.relay().apply(piece.text().width() - 2 * FRAME_SIDE);
        if (room < FoliosText.LINE_HEIGHT) {
            flow(setter, pieces.subList(words, pieces.size()));
            return;
        }
        List<FoliosText> chunks = new ArrayList<>();
        for (FoliosText chunk : text.split(room, room)) {
            if (!chunk.isEmpty()) chunks.add(chunk);
        }
        if (chunks.size() <= 1) {
            framed(setter, top, text, pieces.subList(words + 1, pieces.size()));
            return;
        }
        int[] shown = {0};
        float[] slide = {0, -1};
        int count = chunks.size();
        ElementLayout.Anchor anchor = settings.anchor();
        int tallest = 0;
        for (FoliosText chunk : chunks) tallest = Math.max(tallest, chunk.height());
        int tall = tallest;
        int framed = tall + 2 * FRAME_EDGE;
        setter.put(top, room + 2 * FRAME_EDGE + PAGER, (painter, y) -> {
            int right = painter.contentRight();
            Shapes.panel(painter, FoliosLayout.CONTENT_LEFT, y, right, y + framed);
            FoliosText chunk = chunks.get(shown[0]);
            chunk.draw(painter, FoliosLayout.CONTENT_LEFT + FRAME_SIDE, y + FRAME_EDGE + offset(anchor, tall - chunk.height()));
            painter.scroll(FoliosLayout.CONTENT_LEFT, y, right - FoliosLayout.CONTENT_LEFT, framed,
                    notches -> shown[0] = Math.clamp(shown[0] + (notches > 0 ? -1 : 1), 0, count - 1));
            pager(painter, shown, slide, count, (FoliosLayout.CONTENT_LEFT + right) / 2, y + framed + 4);
        });
        setter.y = settings.height() != null && settings.height().sized() ? bottom : setter.bottom;
    }

    private static void scroll(Setter setter, Group group) {
        List<Piece> pieces = group.pieces();
        int words = -1;
        for (int index = 0; index < pieces.size(); index++) {
            if (!(pieces.get(index) instanceof Words)) continue;
            if (words >= 0) {
                flow(setter, pieces);
                return;
            }
            words = index;
        }
        if (words < 0) {
            flow(setter, pieces);
            return;
        }
        ElementLayout settings = group.settings();
        int fixed = 0;
        for (int index = 0; index < words; index++) fixed += pieces.get(index).height() + GAP;
        int least = settings.leastHeight(setter.bottom - FoliosLayout.CONTENT_TOP);
        int at = setter.next();
        if (!setter.fresh && boxBottom(setter, settings, at) - (at + fixed) < least) {
            setter.newPage();
            at = setter.y;
        }
        int bottom = boxBottom(setter, settings, at);
        setter.y = at;
        setter.fresh = at == FoliosLayout.CONTENT_TOP || setter.fresh;
        flow(setter, pieces.subList(0, words));
        int top = setter.next();
        int room = bottom - top - 2 * FRAME_EDGE;
        Words piece = (Words) pieces.get(words);
        if (room < FoliosText.LINE_HEIGHT) {
            flow(setter, pieces.subList(words, pieces.size()));
            return;
        }
        FoliosText whole = piece.relay().apply(piece.text().width() - 2 * FRAME_SIDE);
        if (whole.height() <= room) {
            framed(setter, top, whole, pieces.subList(words + 1, pieces.size()));
            return;
        }
        FoliosText text = piece.relay().apply(piece.text().width() - 2 * FRAME_SIDE - Shapes.SCROLLBAR - SCROLL_GAP);
        List<Integer> tops = text.lineTops();
        int last = 0;
        while (last < tops.size() - 1 && text.height() - tops.get(last) > room) last++;
        int end = last;
        FoliosText[] windows = new FoliosText[end + 1];
        int fit = 0;
        for (int index = 0; index <= end; index++) {
            windows[index] = text.window(tops.get(index), room);
            fit = Math.max(fit, windows[index].height());
        }
        int inside = fit;
        int framed = inside + 2 * FRAME_EDGE;
        int[] first = {0};
        float[] dragged = {0};
        setter.put(top, framed, (painter, boxY) -> {
            int right = painter.contentRight();
            Shapes.panel(painter, FoliosLayout.CONTENT_LEFT, boxY, right, boxY + framed);
            int y = boxY + FRAME_EDGE;
            int barX = right - FRAME_SIDE - Shapes.SCROLLBAR;
            int shown = Math.max(0, Math.min(first[0], end));
            windows[shown].draw(painter, FoliosLayout.CONTENT_LEFT + FRAME_SIDE, y);
            boolean above = shown > 0;
            boolean below = shown < end;
            if (above || below) {
                int face = painter.palette().panel();
                int left = FoliosLayout.CONTENT_LEFT + 2;
                int across = barX - SCROLL_GAP / 2;
                over(painter, () -> {
                    for (int row = 0; row < SCROLL_FADE; row++) {
                        float t = 1 - (row + 0.5F) / SCROLL_FADE;
                        int color = net.ixdarklord.folios.api.client.page.FoliosPalette.alpha(face, Math.round(255 * t * t * (3 - 2 * t)));
                        if (above) painter.fill(left, y + row, across, y + row + 1, color);
                        if (below) painter.fill(left, y + inside - 1 - row, across, y + inside - row, color);
                    }
                });
            }
            painter.scroll(FoliosLayout.CONTENT_LEFT, boxY, right - FoliosLayout.CONTENT_LEFT, framed,
                    notches -> first[0] = Math.max(0, Math.min(end, first[0] - (int) Math.signum(notches))));
            int thumb = Math.max(8, inside * inside / text.height());
            int travel = Math.max(1, inside - thumb);
            boolean held = painter.drag(barX - 2, y, Shapes.SCROLLBAR + 4, inside, (dx, dy) -> {
                dragged[0] += dy * tops.get(end) / (float) travel;
                int target = tops.get(first[0]) + Math.round(dragged[0]);
                int nearest = first[0];
                for (int index = 0; index <= end; index++) {
                    if (Math.abs(tops.get(index) - target) < Math.abs(tops.get(nearest) - target)) nearest = index;
                }
                if (nearest != first[0]) {
                    dragged[0] -= tops.get(nearest) - tops.get(first[0]);
                    first[0] = nearest;
                }
            });
            if (!held) dragged[0] = 0;
            int thumbY = y + travel * shown / end;
            Shapes.scrollbar(painter, barX, y, inside, thumbY, thumb, held);
        });
        setter.y = settings.height() != null && settings.height().sized() ? top + framed : setter.bottom;
    }

    private static final int SCROLL_GAP = 3;
    private static final int SCROLL_FADE = 14;

    private static void over(net.ixdarklord.folios.api.client.page.FoliosPainter painter, Runnable draw) {
        if (painter instanceof net.ixdarklord.folios.internal.client.paint.AbstractPainter drawing) drawing.above(draw);
        else draw.run();
    }
    private static final int FRAME_SIDE = 4;
    private static final int FRAME_EDGE = 3;

    private static void framed(Setter setter, int top, FoliosText text, List<Piece> rest) {
        setter.put(top, text.height() + 2 * FRAME_EDGE, (painter, y) -> {
            Shapes.panel(painter, FoliosLayout.CONTENT_LEFT, y, painter.contentRight(), y + text.height() + 2 * FRAME_EDGE);
            text.draw(painter, FoliosLayout.CONTENT_LEFT + FRAME_SIDE, y + FRAME_EDGE);
        });
        flow(setter, rest);
    }

    private static final int PAGER_SQUARES = 7;

    private static void pager(net.ixdarklord.folios.api.client.page.FoliosPainter painter, int[] shown, float[] slide, int count, int middle, int y) {
        net.ixdarklord.folios.api.client.page.FoliosPalette palette = painter.palette();
        int face = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.panel(), palette.paper(), 0.4F);
        int rim = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.line(), palette.accent(), 0.2F);
        int lit = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.line(), palette.accent(), 0.6F);
        int ink = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.muted(), palette.text(), 0.35F);
        float now = painter.time();
        if (slide[1] < 0) slide[0] = shown[0];
        else slide[0] += (shown[0] - slide[0]) * Math.min(1, (now - slide[1]) * 14);
        if (Math.abs(shown[0] - slide[0]) < 0.01F) slide[0] = shown[0];
        slide[1] = now;
        int visible = Math.min(count, PAGER_SQUARES);
        int inner = visible * 5 - 2 + 2;
        int width = inner + 24;
        int left = middle - width / 2;
        int right = left + width;
        int outline = net.ixdarklord.folios.api.client.page.FoliosPalette.alpha(rim, 0x90);
        Sprites.draw(painter, Sprites.PAGER_FACE, left, y - 1, width, 11, face);
        Sprites.draw(painter, Sprites.PAGER_OUTLINE, left, y - 1, width, 11, outline);
        boolean first = shown[0] == 0;
        boolean last = shown[0] == count - 1;
        Component before = Component.translatableWithFallback("folios.overflow.page", "Page: %s/%s", shown[0], count);
        Component after = Component.translatableWithFallback("folios.overflow.page", "Page: %s/%s", shown[0] + 2, count);
        boolean back = !first && painter.action(left, y - 1, 10, 11, before, () -> shown[0]--);
        boolean on = !last && painter.action(right - 10, y - 1, 10, 11, after, () -> shown[0]++);
        int hover = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(face, palette.accent(), 0.14F);
        if (back) Sprites.draw(painter, Sprites.PAGER_FACE, left, y - 1, 11, 11, hover);
        if (on) Sprites.draw(painter, Sprites.PAGER_FACE, right - 11, y - 1, 11, 11, hover);
        float start = count > visible ? Math.max(0, Math.min(count - visible, slide[0] - (visible - 1) / 2F)) : 0;
        int x0 = middle - inner / 2 + 1;
        for (int index = 0; index < count; index++) {
            float slot = index - start;
            if (slot < -0.5F || slot > visible - 0.5F) continue;
            int center = x0 + Math.round(slot * 5) + 1;
            boolean edge = count > visible && (slot < 0.5F && index > 0 || slot > visible - 1.5F && index < count - 1);
            if (edge) Sprites.draw(painter, Sprites.PAGER_DOT, center, y + 4, rim);
            else Sprites.draw(painter, Sprites.PAGER_SQUARE, center - 1, y + 3, rim);
        }
        int marker = x0 + Math.round((slide[0] - start) * 5) + 1;
        Sprites.draw(painter, Sprites.PAGER_MARKER, marker - 2, y + 2, lit);
        int faded = net.ixdarklord.folios.api.client.page.FoliosPalette.alpha(rim, 0x80);
        arrow(painter, "‹", left + 5, y, first ? faded : back ? palette.accent() : ink, back);
        arrow(painter, "›", right - 4, y, last ? faded : on ? palette.accent() : ink, on);
    }

    private static void arrow(net.ixdarklord.folios.api.client.page.FoliosPainter painter, String mark, int x, int y, int color, boolean over) {
        float grow = over ? 1.2F : 1;
        float half = painter.font().width(mark) / 2F;
        painter.push();
        painter.translate(x, y + 4.5F);
        painter.scale(grow);
        painter.translate(-half, -3.5F);
        painter.text(Component.literal(mark), 0, 0, color);
        painter.pop();
    }

    private static int boxBottom(Setter setter, ElementLayout settings, int at) {
        ElementLayout.Height height = settings.height();
        if (height != null && height.sized()) return Math.min(setter.bottom, at + height.pixels(setter.bottom - FoliosLayout.CONTENT_TOP));
        return setter.bottom;
    }

    @Override
    public String searchText() {
        StringBuilder words = new StringBuilder(FoliosText.resolve(title.orElse(""))).append(' ').append(FoliosText.resolve(with(text, observed)));
        for (Item item : elements) {
            if (!item.lock().isOpen()) continue;
            words.append(' ').append(item.element().searchText());
            if (item.observed() != null) words.append(' ').append(FoliosText.resolve(item.observed().text()));
        }
        return words.toString();
    }

    @Override
    public boolean spread() {
        return single && elements.getFirst().element().spread();
    }

    @Override
    public List<FoliosStack> items() {
        List<FoliosStack> items = new ArrayList<>();
        for (Item item : elements) items.addAll(item.element().items());
        return items;
    }

    @Override
    public List<FoliosObjective> objectives() {
        List<FoliosObjective> objectives = new ArrayList<>();
        for (Item item : elements) objectives.addAll(item.element().objectives());
        return objectives;
    }
}
