package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

// A page: a title, and "elements", what it shows one under another: text, a recipe, a multiblock, a structure, a build,
// an entity, a picture, a spotlight, links, a quest... Each is written {"type": "recipe", "recipe": ...}, as an element
// of its type is (a plain string is text), with its own title and text if it has them; "text" on the page itself is
// text before them. An element that doesn't fit in what is left of the page starts the next, and text runs on over as
// many pages as it needs, unless its "overflow" says otherwise (ElementLayout). A page written as one element
// ({"type": "recipe", ...}) is read as a page of that element alone ("single"), its title the page's.
public record TextPage(Optional<String> title, String text, List<Item> elements, ElementLayout settings, boolean single, @Nullable Observed observed)
        implements FoliosPageElement {
    /** An element of a page, how it is set there, and the source its text observes (null for none). */
    public record Item(FoliosPageElement element, ElementLayout settings, @Nullable Observed observed) {}

    private static final Codec<Item> ELEMENT = ExtraCodecs.JSON.comapFlatMap(TextPage::element, item -> new JsonObject());

    public static final MapCodec<TextPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FoliosText.CODEC.optionalFieldOf("title").forGetter(TextPage::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(TextPage::text),
            net.ixdarklord.folios.internal.compat.Compat.listOf(ELEMENT, 0, 64).optionalFieldOf("elements", List.of()).forGetter(TextPage::elements)
    ).apply(instance, (title, text, elements) -> new TextPage(title, text, elements, ElementLayout.NONE, false, null)));

    /** The room left between elements. */
    private static final int GAP = 6;
    /** The room under a flipping box for its pager: a gap and its pill. */
    private static final int PAGER = 4 + 10;

    /** A page of one element, set as its JSON says (it lays itself out as its type does when it can't be set so). */
    public static TextPage single(FoliosPageElement element, ElementLayout settings, @Nullable Observed observed) {
        return new TextPage(Optional.empty(), "", List.of(new Item(element, settings, observed)), ElementLayout.NONE, true, null);
    }

    /** This page, set as its JSON says, its text observing a source (null for none). */
    public TextPage with(ElementLayout settings, @Nullable Observed observed) {
        return new TextPage(title, text, elements, settings, single, observed);
    }

    // Its own text, and after it what it observes, as the source is now.
    private static String with(String text, @Nullable Observed observed) {
        if (observed == null) return text;
        String rows = observed.text();
        if (rows.isEmpty()) return text;
        return text.isEmpty() ? rows : text + "\n\n" + rows;
    }

    // An element: of any type that lays itself out as a title, a part of its own and text do (not a spread, nor a
    // page of elements itself).
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
            return DataResult.success(new Item(page, ElementLayout.read(object, "A " + type + " element"), Observed.read(object)));
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
            Item item = elements.get(0);
            ElementLayout own = item.settings().or(page);
            layout.alignment(own.alignment());
            List<FoliosLayout.Flow> flows = layout.capture(item.element());
            // One that doesn't lay itself out as a title, a part and text is set its own way, as it always was.
            if (flows.isEmpty()) {
                layout.alignment(null);
                return item.element().lay(layout);
            }
            // Its own title is the page's, the page's icon before it.
            FoliosLayout.Flow first = flows.get(0);
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
            // A page of text: laid out by flow(), which is also how it is taken in as another page's element. Only a
            // page set down its middle or foot, with its title moved, or kept in a box (an overflow other than flow, or
            // a height), is set here instead.
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
            // The page's icon heads its own title, not its elements'.
            layout.titleIcon(FoliosIcon.NONE);
            for (Item item : elements) {
                ElementLayout own = item.settings().or(page);
                layout.alignment(own.alignment());
                List<FoliosLayout.Flow> flows = layout.capture(item.element());
                if (flows.isEmpty()) Folios.LOGGER.warn("A page's element ({}) lays itself out in a way an element can't be", item.element().getClass().getSimpleName());
                groups.add(group(layout, own, flows, item.observed()));
            }
            layout.alignment(null);
            layout.titleIcon(icon);
        }
        return set(heading, icon, headingAlign, groups, page.anchor(), layout.contentBottom());
    }

    // What an element asked flow() for, as pieces to set: its title, the part it draws itself, its text, and after it
    // what it observes.
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
            // Joined to its text when its last piece is text, so the two run on and flip as one.
            if (!pieces.isEmpty() && pieces.get(pieces.size() - 1) instanceof Words && !flows.isEmpty() && flows.get(flows.size() - 1).text() != null) {
                pieces.set(pieces.size() - 1, Words.of(layout, flows.get(flows.size() - 1).text() + "\n\n" + rows));
            } else {
                pieces.add(Words.of(layout, rows));
            }
        }
        return new Group(own, pieces);
    }

    // One thing to set on a page: a title or what an element shows, which stays whole, or text, which can be split.
    private sealed interface Piece permits Fixed, Words {
        int height();
    }

    // A part set whole; a heading (an element's title) keeps to what comes after it.
    private record Fixed(int height, Part part, boolean heading) implements Piece {
        Fixed(int height, Part part) {
            this(height, part, false);
        }
    }

    private record Words(FoliosText text, java.util.function.IntFunction<FoliosText> relay) implements Piece {
        // Its text, and how to set the same text out again at another width.
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

    // An element's pieces, set together as its layout says.
    private record Group(ElementLayout settings, List<Piece> pieces) {
        int height() {
            int height = 0;
            for (Piece piece : pieces) height += piece.height();
            return height + Math.max(0, pieces.size() - 1) * GAP;
        }
    }

    private record Placed(int y, int height, Part part) {}

    // Where setting has got to: the pages so far, and how far down the last one is.
    private static final class Setter {
        final List<List<Placed>> pages = new ArrayList<>();
        List<Placed> page = new ArrayList<>();
        final int bottom;
        int y = FoliosLayout.CONTENT_TOP;
        // Whether nothing is on this page yet (but its title): the next piece then goes at its top, with no gap.
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

    // The groups set one under another down the page, onto as many pages as they take.
    private static List<Laid> set(Optional<String> title, FoliosIcon icon, @Nullable FoliosAlign titleAlign, List<Group> groups,
                                  ElementLayout.@Nullable Anchor anchor, int bottom) {
        Setter setter = new Setter(bottom);
        if (title.isPresent() && !title.get().isEmpty()) {
            String heading = title.get();
            setter.page.add(new Placed(setter.y, FoliosLayout.TITLE_HEIGHT, (painter, py) -> FoliosLayout.title(painter, FoliosText.inline(heading), py, icon, titleAlign)));
            setter.y += FoliosLayout.TITLE_HEIGHT;
        }
        for (Group group : groups) place(setter, group);
        List<Laid> laid = new ArrayList<>();
        for (List<Placed> placed : setter.pages) {
            if (placed.isEmpty() && !laid.isEmpty()) continue;
            // A page set down it as one: its content moved to the middle or the foot of the page.
            int shift = 0;
            if (anchor != null && anchor != ElementLayout.Anchor.TOP && !placed.isEmpty()) {
                int used = 0;
                for (Placed one : placed) used = Math.max(used, one.y() + one.height());
                shift = Math.max(0, bottom - used) / (anchor == ElementLayout.Anchor.CENTER ? 2 : 1);
            }
            int down = shift;
            laid.add(painter -> {
                for (Placed one : placed) one.part().draw(painter, one.y() + down);
            });
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
            // It fits, in a box of its own height: set at the box's top, middle or foot.
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

    // How far down a box something sits that leaves {@code spare} room in it.
    private static int offset(ElementLayout.@Nullable Anchor anchor, int spare) {
        if (anchor == null || spare <= 0) return 0;
        return switch (anchor) {
            case TOP -> 0;
            case CENTER -> spare / 2;
            case BOTTOM -> spare;
        };
    }

    // Pieces one under another, text running on over pages, the rest whole: how pages have always been set.
    private static void flow(Setter setter, List<Piece> pieces) {
        int full = setter.bottom - FoliosLayout.CONTENT_TOP;
        for (int position = 0; position < pieces.size(); position++) {
            Piece piece = pieces.get(position);
            int at = setter.next();
            // A title isn't left at a page's foot without what it heads: the next part whole, or two lines of text.
            int keep = 0;
            if (piece instanceof Fixed title && title.heading() && position + 1 < pieces.size()) {
                Piece next = pieces.get(position + 1);
                keep = GAP + (next instanceof Fixed part ? part.height() : Math.min(((Words) next).text().height(), 2 * FoliosText.LINE_HEIGHT));
            }
            if (piece instanceof Fixed fixed) {
                // Whole, so on the next page if it doesn't fit here (on an empty page, as it is).
                if (!setter.fresh && at + fixed.height() + keep > setter.bottom) {
                    setter.newPage();
                    at = setter.y;
                }
                setter.put(at, fixed.height(), fixed.part());
            } else if (piece instanceof Words words) {
                List<FoliosText> split = words.text().split(Math.max(0, setter.bottom - at), full);
                for (int index = 0; index < split.size(); index++) {
                    FoliosText text = split.get(index);
                    if (index > 0) {
                        setter.newPage();
                        at = setter.y;
                    }
                    // An empty first piece: none of it fitted here, so it starts on the next page.
                    if (text.isEmpty()) continue;
                    setter.put(at, text.height(), (painter, py) -> text.draw(painter, FoliosLayout.CONTENT_LEFT, py));
                }
            }
        }
    }

    // An element kept in a box: its title and part as they are, its text paged in place in a frame under them with
    // clasps stepping back and on (and the mouse wheel over it). The box runs to the foot of the page (the next element
    // then starts the next), or is as tall as its "height" says.
    private static void flip(Setter setter, Group group) {
        List<Piece> pieces = group.pieces();
        int words = -1;
        for (int index = 0; index < pieces.size(); index++) {
            if (pieces.get(index) instanceof Words) {
                if (words >= 0) {
                    // More text than one run: nothing to page in one box, so it runs on as usual.
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
        // Where the pager's marker is drawn as it slides to the part shown, and when it last moved.
        float[] slide = {0, -1};
        int count = chunks.size();
        ElementLayout.Anchor anchor = settings.anchor();
        // The pager stays under the tallest part, so it doesn't move up on a shorter one (the last, say).
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
                    notches -> shown[0] = net.minecraft.util.Mth.clamp(shown[0] + (notches > 0 ? -1 : 1), 0, count - 1));
            pager(painter, shown, slide, count, (FoliosLayout.CONTENT_LEFT + right) / 2, y + framed + 4);
        });
        // A box running to the foot of the page leaves nothing under it there.
        setter.y = settings.height() != null && settings.height().sized() ? bottom : setter.bottom;
    }

    // An element kept in a box: its title and part as they are, its text in a frame under them laid out beside a
    // scrollbar and scrolled a line at a time, by the mouse wheel over it or the bar dragged. Only whole lines are shown, so nothing
    // needs cutting off at the box's edges. The box runs to the foot of the page, or is as tall as its "height" says.
    private static void scroll(Setter setter, Group group) {
        List<Piece> pieces = group.pieces();
        int words = -1;
        for (int index = 0; index < pieces.size(); index++) {
            if (!(pieces.get(index) instanceof Words)) continue;
            if (words >= 0) {
                // More text than one run: nothing to scroll in one box, so it runs on as usual.
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
        // Where a window can start: each line's top, up to the first that shows the text's end.
        List<Integer> tops = text.lineTops();
        int last = 0;
        while (last < tops.size() - 1 && text.height() - tops.get(last) > room) last++;
        int end = last;
        // Each window's whole lines, and the frame as tall as the tallest of them: whole lines leave less than the
        // box's room, and the frame doesn't keep the rest empty under them.
        FoliosText[] windows = new FoliosText[end + 1];
        int fit = 0;
        for (int index = 0; index <= end; index++) {
            windows[index] = text.window(tops.get(index), room);
            fit = Math.max(fit, windows[index].height());
        }
        int inside = fit;
        int framed = inside + 2 * FRAME_EDGE;
        int[] first = {0};
        // How far the bar has been dragged since the window last moved, in pixels of the text.
        float[] dragged = {0};
        setter.put(top, framed, (painter, boxY) -> {
            int right = painter.contentRight();
            Shapes.panel(painter, FoliosLayout.CONTENT_LEFT, boxY, right, boxY + framed);
            int y = boxY + FRAME_EDGE;
            int barX = right - FRAME_SIDE - Shapes.SCROLLBAR;
            int shown = Math.max(0, Math.min(first[0], end));
            windows[shown].draw(painter, FoliosLayout.CONTENT_LEFT + FRAME_SIDE, y);
            // At an edge with more of the text beyond it, the frame's face rises over the text, fading it into the edge.
            boolean above = shown > 0;
            boolean below = shown < end;
            if (above || below) {
                int face = painter.palette().panel();
                int left = FoliosLayout.CONTENT_LEFT + 2;
                int across = barX - SCROLL_GAP / 2;
                over(painter, () -> {
                    for (int row = 0; row < SCROLL_FADE; row++) {
                        // Solid at the edge, smoothly clear by SCROLL_FADE pixels in.
                        float t = 1 - (row + 0.5F) / SCROLL_FADE;
                        int color = net.ixdarklord.folios.api.client.page.FoliosPalette.alpha(face, Math.round(255 * t * t * (3 - 2 * t)));
                        if (above) painter.fill(left, y + row, across, y + row + 1, color);
                        if (below) painter.fill(left, y + inside - 1 - row, across, y + inside - row, color);
                    }
                });
            }
            painter.scroll(FoliosLayout.CONTENT_LEFT, boxY, right - FoliosLayout.CONTENT_LEFT, framed,
                    notches -> first[0] = Math.max(0, Math.min(end, first[0] - (int) Math.signum(notches))));
            boolean held = painter.drag(barX - 2, y, Shapes.SCROLLBAR + 4, inside, (dx, dy) -> {
                dragged[0] += dy * text.height() / (float) inside;
                // On to the window whose top is nearest where the bar has been dragged.
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
            int thumb = Math.max(8, inside * inside / text.height());
            int thumbY = y + (inside - thumb) * shown / end;
            Shapes.scrollbar(painter, barX, y, inside, thumbY, thumb, held || painter.hovered(barX - 2, y, Shapes.SCROLLBAR + 4, inside));
        });
        // A box of a set height ends at its frame; one running to the foot of the page leaves nothing under it there.
        setter.y = settings.height() != null && settings.height().sized() ? top + framed : setter.bottom;
    }

    /** The room between scrolled text and its scrollbar. */
    private static final int SCROLL_GAP = 3;
    /** How far in from a scroll box's edge its text fades into the frame, in pixels. */
    private static final int SCROLL_FADE = 14;

    // Draws over what is already drawn, text and items too, where the painter can (a page's own painters can).
    private static void over(net.ixdarklord.folios.api.client.page.FoliosPainter painter, Runnable draw) {
        if (painter instanceof net.ixdarklord.folios.internal.client.paint.AbstractPainter drawing) drawing.above(draw);
        else draw.run();
    }
    /** The room inside a box's frame: at its sides, and above and below its text. */
    private static final int FRAME_SIDE = 4;
    private static final int FRAME_EDGE = 3;

    // Text that fits its box whole, in the box's frame as tall as it needs, and then what follows it.
    private static void framed(Setter setter, int top, FoliosText text, List<Piece> rest) {
        setter.put(top, text.height() + 2 * FRAME_EDGE, (painter, y) -> {
            Shapes.panel(painter, FoliosLayout.CONTENT_LEFT, y, painter.contentRight(), y + text.height() + 2 * FRAME_EDGE);
            text.draw(painter, FoliosLayout.CONTENT_LEFT + FRAME_SIDE, y + FRAME_EDGE);
        });
        flow(setter, rest);
    }

    /** The most squares a pager shows at once; more parts than this scroll under them. */
    private static final int PAGER_SQUARES = 7;

    // The pager of a flipping box, right under its text: a small pill in the colours of the book's clasps (their face,
    // and their rim as a faint outline), with a thin arrow at each end that steps back or on (tinted and growing a
    // little under the pointer, as a clasp does, its tooltip naming the part it goes to), and a square for each part:
    // the one shown larger and of the clasp's lit rim, sliding smoothly to the part as it changes. No more than
    // PAGER_SQUARES at once: with more parts, the squares scroll to keep the one shown among them, those at an edge
    // with more beyond made smaller.
    private static void pager(net.ixdarklord.folios.api.client.page.FoliosPainter painter, int[] shown, float[] slide, int count, int middle, int y) {
        net.ixdarklord.folios.api.client.page.FoliosPalette palette = painter.palette();
        int face = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.panel(), palette.paper(), 0.4F);
        int rim = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.line(), palette.accent(), 0.2F);
        int lit = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.line(), palette.accent(), 0.6F);
        int ink = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(palette.muted(), palette.text(), 0.35F);
        // The marker eases towards the part shown.
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
        // The pager sprite: the pill's face, and its outline with the corners left open so it reads round.
        Sprites.draw(painter, Sprites.PAGER_FACE, left, y - 1, width, 11, face);
        Sprites.draw(painter, Sprites.PAGER_OUTLINE, left, y - 1, width, 11, outline);
        boolean first = shown[0] == 0;
        boolean last = shown[0] == count - 1;
        Component before = Component.translatableWithFallback("folios.overflow.page", "Page: %s/%s", shown[0], count);
        Component after = Component.translatableWithFallback("folios.overflow.page", "Page: %s/%s", shown[0] + 2, count);
        boolean back = !first && painter.action(left, y - 1, 10, 11, before, () -> shown[0]--);
        boolean on = !last && painter.action(right - 10, y - 1, 10, 11, after, () -> shown[0]++);
        // Under the pointer, an arrow's end of the pill takes the clasp's hover tint.
        int hover = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(face, palette.accent(), 0.14F);
        // The face's own end, its middle stretched over the arrow's end of the pill.
        if (back) Sprites.draw(painter, Sprites.PAGER_FACE, left, y - 1, 11, 11, hover);
        if (on) Sprites.draw(painter, Sprites.PAGER_FACE, right - 11, y - 1, 11, 11, hover);
        // The squares, one every 5 pixels from the first one in view, which follows the marker once there are more
        // parts than fit.
        float start = count > visible ? Math.max(0, Math.min(count - visible, slide[0] - (visible - 1) / 2F)) : 0;
        int x0 = middle - inner / 2 + 1;
        for (int index = 0; index < count; index++) {
            float slot = index - start;
            if (slot < -0.5F || slot > visible - 0.5F) continue;
            int center = x0 + Math.round(slot * 5) + 1;
            // At an edge with parts beyond it, a smaller square.
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

    // A pager's arrow, centred on x, drawn larger while the pointer is over it.
    private static void arrow(net.ixdarklord.folios.api.client.page.FoliosPainter painter, String mark, int x, int y, int color, boolean over) {
        // Scaled about its own middle, which sits on the pill's: a glyph is 7 pixels tall over a 9-pixel pill.
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
            words.append(' ').append(item.element().searchText());
            if (item.observed() != null) words.append(' ').append(FoliosText.resolve(item.observed().text()));
        }
        return words.toString();
    }

    @Override
    public boolean spread() {
        return single && elements.get(0).element().spread();
    }

    // Its elements' items and objectives are the page's: pressing a spotlit item anywhere comes here, a quest counts.
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
