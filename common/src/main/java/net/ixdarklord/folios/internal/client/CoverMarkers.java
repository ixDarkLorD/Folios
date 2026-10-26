package net.ixdarklord.folios.internal.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where a book model wants its cover lettering, marked in the model itself: an element whose name has {@code #title}
 * in it is where the book's name goes, one with {@code #subtitle} where its subtitle goes, one with {@code #summary}
 * where its summary goes (a book with a summary has its subtitle under its name instead; one without has its subtitle
 * where the summary would go, if nothing marks a subtitle), and one with {@code #icon} is the gem the book's cover icon
 * is set in (CoverEmblem; its middle and size, square, turned 45° as a setting is) (in Blockbench, rename the
 * cube; a plate, or an invisible box, either works). The line is centred on the element's front (+z) face, as wide as
 * most of it, as tall as about half of it, and lies just over it. A model's parents are followed for its elements.
 * Read once per model, until resources reload.
 */
public final class CoverMarkers {
    /** A marked line: where and how large, and the z of the face it lies on. */
    public record Marked(FoliosBook.CoverText.Line line, float z) {}

    /** What a model marks; any may be null. */
    public record Markers(@Nullable Marked title, @Nullable Marked subtitle, @Nullable Marked summary, @Nullable Marked icon) {
        public static final Markers NONE = new Markers(null, null, null, null);
    }

    /** What is lettered where: the book's name, its subtitle, its summary (up to {@code lines} lines), each its own colour. */
    public record Lettering(Component text, FoliosBook.CoverText.Line line, float z, int lines, Kind kind) {}

    /** RULE: a soft rule with a diamond stud at its middle, its line's size its thickness, its width its length. */
    public enum Kind { TITLE, SUBTITLE, SUMMARY, RULE }

    /** How much of the title plate the name takes with the subtitle under it, and where each sits (of its height). */
    private static final float NAME_SHARE = 0.5F;
    private static final float SUBTITLE_SHARE = 0.29F;

    /**
     * A book's cover lettering, where the book says, else where the model marks it, else at the given defaults: its
     * name; its subtitle under the name on the same plate when it has a summary (the summary on the lower plate), or
     * on the lower plate when it hasn't. {@code z} is used where nothing is marked.
     */
    public static java.util.List<Lettering> layout(FoliosBook book, Markers marked, FoliosBook.CoverText.Line titleDefault,
                                                   FoliosBook.CoverText.Line lowerDefault, float z) {
        FoliosBook.CoverText text = book.coverText();
        java.util.List<Lettering> out = new java.util.ArrayList<>();
        FoliosBook.CoverText.Line title = text.titleOr(marked.title() != null ? marked.title().line() : titleDefault);
        float titleZ = marked.title() != null ? marked.title().z() : z;
        Marked lowerMark = marked.summary() != null ? marked.summary() : marked.subtitle();
        float lowerZ = lowerMark != null ? lowerMark.z() : z;
        boolean hasSubtitle = !FoliosText.isBlank(book.subtitle());
        Component subtitle = hasSubtitle ? FoliosText.inline(book.subtitle()) : Component.empty();
        if (!FoliosText.isBlank(text.summaryText())) {
            // The plate's height, from its line (a marked line is UP of it); the name in its upper part, the subtitle under.
            float plate = title.size() / UP;
            boolean own = text.title() != null;
            FoliosBook.CoverText.Line name = !hasSubtitle || own ? title
                    : new FoliosBook.CoverText.Line(title.x(), title.y() + plate * 0.2F, plate * NAME_SHARE * UP * 1.15F, title.width());
            out.add(new Lettering(book.title(), name, titleZ, 1, Kind.TITLE));
            if (hasSubtitle) {
                FoliosBook.CoverText.Line under = text.subtitle() != null ? text.subtitle()
                        : new FoliosBook.CoverText.Line(title.x(), title.y() - plate * 0.28F, plate * SUBTITLE_SHARE, title.width() * 0.92F);
                out.add(new Lettering(subtitle, under, titleZ, 1, Kind.SUBTITLE));
                // Between them, a soft rule with a stud at its middle.
                if (!own && text.subtitle() == null) {
                    out.add(new Lettering(Component.empty(), new FoliosBook.CoverText.Line(title.x(), title.y() - plate * 0.06F, plate * 0.035F, title.width() * 0.7F),
                            titleZ, 1, Kind.RULE));
                }
            }
            FoliosBook.CoverText.Line summary = text.summary() != null ? text.summary() : lowerMark != null ? lowerMark.line() : lowerDefault;
            out.add(new Lettering(FoliosText.inline(text.summaryText()), summary, lowerZ, 2, Kind.SUMMARY));
        } else {
            out.add(new Lettering(book.title(), title, titleZ, 1, Kind.TITLE));
            if (hasSubtitle) {
                FoliosBook.CoverText.Line lower = text.subtitleOr(lowerMark != null ? lowerMark.line() : lowerDefault);
                out.add(new Lettering(subtitle, lower, lowerZ, 1, Kind.SUBTITLE));
            }
        }
        return out;
    }

    /** A lettering's colour: the name in gilt, the subtitle and summary quieter, toward the cover. */
    public static int color(Kind kind, int gilt, int cover) {
        return kind == Kind.TITLE ? gilt : net.ixdarklord.folios.api.client.page.FoliosPalette.mix(gilt, cover, kind == Kind.SUBTITLE ? 0.3F : 0.4F);
    }

    /**
     * A soft rule's pieces, in model units about its middle: {x0, x1, alpha} runs of its line, fading out to its ends,
     * then the stud's half size.
     */
    public static float[][] rule(FoliosBook.CoverText.Line line) {
        int steps = 24;
        float half = line.width() / 2;
        float stud = line.size() * 3.2F;
        float gap = stud * 1.6F;
        java.util.List<float[]> runs = new java.util.ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            for (int step = 0; step < steps; step++) {
                float a = gap + (half - gap) * step / steps;
                float b = gap + (half - gap) * (step + 1) / steps;
                float t = 1 - (step + 0.5F) / steps;
                float alpha = t * t * (3 - 2 * t);
                runs.add(side < 0 ? new float[]{-b, -a, alpha} : new float[]{a, b, alpha});
            }
        }
        runs.add(new float[]{stud});
        return runs.toArray(new float[0][]);
    }

    /** A lettering's rows of text and how large: {@code scale} model units to a font pixel, {@code top} the first row's y. */
    public record Rows(java.util.List<net.minecraft.util.FormattedCharSequence> lines, float scale, float top) {}

    /**
     * Lays a lettering out in model units: a font line as tall as its size (rows sharing it when there are more), wrapped
     * at its width at that size, then shrunk to fit across.
     */
    public static Rows rows(net.minecraft.client.gui.Font font, Lettering lettering) {
        FoliosBook.CoverText.Line line = lettering.line();
        float full = line.size() * 1.6F / 9.0F;
        if (lettering.kind() == Kind.TITLE) {
            Rows two = twoLines(font, lettering, full);
            if (two != null) return two;
        }
        float shared = lettering.lines() > 1 ? full / lettering.lines() * 0.64F : full;
        // A summary of more than one line is set small enough for the whole of it to fit: it is wrapped narrower and
        // narrower (smaller each time) until it fits its line count.
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = wrap(font, lettering, Math.round(line.width() / shared));
        while (lettering.lines() > 1 && shared > full * 0.2F && font.split(lettering.text(), Math.round(line.width() / shared)).size() > lettering.lines()) {
            shared *= 0.9F;
            lines = wrap(font, lettering, Math.round(line.width() / shared));
        }
        float scale = shared;
        for (net.minecraft.util.FormattedCharSequence row : lines) scale = Math.min(scale, line.width() / Math.max(1, font.width(row)));
        return new Rows(lines, scale, -(lines.size() * 9 - 1) / 2.0F);
    }

    /**
     * A name too long for its line, set on two lines as even as can be, each half the line's size (the two as tall as
     * the one), when that sets it larger than one line shrunk to fit; null when one line does as well.
     */
    private static @Nullable Rows twoLines(net.minecraft.client.gui.Font font, Lettering lettering, float full) {
        FoliosBook.CoverText.Line line = lettering.line();
        int wide = font.width(lettering.text());
        float one = Math.min(full, line.width() / Math.max(1, wide));
        float half = full * TITLE_ROWS;
        if (one >= half * TWO_LINES_GAIN) return null;
        // The narrowest width that leaves it two lines: the halves as even as the words allow.
        java.util.List<net.minecraft.util.FormattedCharSequence> rows = null;
        for (int room = (wide + 1) / 2; room <= wide; room += 2) {
            java.util.List<net.minecraft.util.FormattedCharSequence> split = font.split(lettering.text(), room);
            if (split.size() <= 2) {
                rows = split;
                break;
            }
        }
        if (rows == null || rows.size() < 2) return null;
        float scale = half;
        for (net.minecraft.util.FormattedCharSequence row : rows) scale = Math.min(scale, line.width() / Math.max(1, font.width(row)));
        if (scale <= one * TWO_LINES_GAIN) return null;
        return new Rows(rows, scale, -(rows.size() * 9 - 1) / 2.0F);
    }

    /** How large each line of a name on two lines is, of one line's size. */
    private static final float TITLE_ROWS = 0.5F;
    /** How much larger two lines must set a name than one before it is split. */
    private static final float TWO_LINES_GAIN = 1.15F;

    /**
     * A lettering's text as lines no wider than {@code room} at size 1 (font pixels), at most its line count, the last
     * cut short with an ellipsis if more is left.
     */
    public static java.util.List<net.minecraft.util.FormattedCharSequence> wrap(net.minecraft.client.gui.Font font, Lettering lettering, int room) {
        if (lettering.lines() <= 1) return java.util.List.of(lettering.text().getVisualOrderText());
        java.util.List<net.minecraft.util.FormattedCharSequence> split = font.split(lettering.text(), Math.max(20, room));
        if (split.size() <= lettering.lines()) return split;
        java.util.List<net.minecraft.util.FormattedCharSequence> kept = new java.util.ArrayList<>(split.subList(0, lettering.lines() - 1));
        kept.add(net.minecraft.util.FormattedCharSequence.composite(split.get(lettering.lines() - 1), net.minecraft.util.FormattedCharSequence.forward("…", net.minecraft.network.chat.Style.EMPTY)));
        return kept;
    }

    /** How much of a marked face the lettering takes: across, and up (a line's size is 1.6 units at 1). */
    private static final float ACROSS = 0.9F;
    private static final float UP = 0.56F;

    private static final Map<ResourceLocation, Markers> READ = new HashMap<>();

    private CoverMarkers() {}

    public static void clear() {
        READ.clear();
    }

    /** The markers of a block or item model (its id as in "parent": {@code folios:item/guide_book_3d}). */
    public static Markers of(ResourceLocation model) {
        return READ.computeIfAbsent(model, CoverMarkers::read);
    }

    private static Markers read(ResourceLocation model) {
        ResourceLocation at = model;
        for (int depth = 0; depth < 8 && at != null; depth++) {
            JsonObject json = load(at);
            if (json == null) return Markers.NONE;
            if (json.has("elements")) return markers(json.getAsJsonArray("elements"));
            at = json.has("parent") ? ResourceLocation.parse(json.get("parent").getAsString()) : null;
        }
        return Markers.NONE;
    }

    private static @Nullable JsonObject load(ResourceLocation model) {
        ResourceLocation file = model.withPath(path -> "models/" + path + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return null;
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            Folios.LOGGER.warn("Couldn't read the model {} for its cover markers: {}", file, e.getMessage());
            return null;
        }
    }
    /**
     * The plate of a cover a point is on, in the item's units (3 to 13 across, 1 to 15 up): {@link Kind#TITLE} for the
     * name's, {@link Kind#SUMMARY} for the lower one (whatever it holds), or null off both or where nothing is marked.
     */
    public static @Nullable Kind plateAt(Markers marked, float x, float y) {
        if (on(marked.title(), x, y)) return Kind.TITLE;
        return on(marked.summary() != null ? marked.summary() : marked.subtitle(), x, y) ? Kind.SUMMARY : null;
    }

    // Whether a point is on a marked plate's box.
    private static boolean on(@Nullable Marked mark, float x, float y) {
        if (mark == null) return false;
        FoliosBook.CoverText.Line line = mark.line();
        return Math.abs(x - line.x()) <= line.width() / ACROSS / 2 && Math.abs(y - line.y()) <= line.size() / UP / 2;
    }

    /**
     * What a plate of a book's cover says, as {@link #layout} letters it: on the name's plate the name, with the
     * subtitle under it when the lower plate holds the summary; on the lower plate the summary, or the subtitle.
     */
    public static java.util.List<Component> plateText(FoliosBook book, Kind plate) {
        boolean summary = !FoliosText.isBlank(book.coverText().summaryText());
        boolean subtitle = !FoliosText.isBlank(book.subtitle());
        java.util.List<Component> out = new java.util.ArrayList<>();
        if (plate == Kind.TITLE) {
            out.add(book.title());
            if (summary && subtitle) out.add(FoliosText.inline(book.subtitle()));
        } else if (summary) {
            out.add(FoliosText.inline(book.coverText().summaryText()));
        } else if (subtitle) {
            out.add(FoliosText.inline(book.subtitle()));
        }
        return out;
    }

    private static Markers markers(JsonArray elements) {
        Marked title = null;
        Marked subtitle = null;
        Marked summary = null;
        Marked icon = null;
        for (JsonElement element : elements) {
            JsonObject box = element.getAsJsonObject();
            String name = box.has("name") ? box.get("name").getAsString() : "";
            if (name.contains("#summary")) summary = marked(box);
            else if (name.contains("#subtitle")) subtitle = marked(box);
            else if (name.contains("#title")) title = marked(box);
            else if (name.contains("#icon")) icon = marked(box);
        }
        return new Markers(title, subtitle, summary, icon);
    }

    /** How wide a marked gem is, in model units. */
    public static float side(Marked gem) {
        return gem.line().size() / UP;
    }

    private static Marked marked(JsonObject box) {
        JsonArray from = box.getAsJsonArray("from");
        JsonArray to = box.getAsJsonArray("to");
        float x0 = from.get(0).getAsFloat(), y0 = from.get(1).getAsFloat();
        float x1 = to.get(0).getAsFloat(), y1 = to.get(1).getAsFloat();
        float z = Math.max(from.get(2).getAsFloat(), to.get(2).getAsFloat());
        FoliosBook.CoverText.Line line = new FoliosBook.CoverText.Line((x0 + x1) / 2, (y0 + y1) / 2, Math.abs(y1 - y0) * UP, Math.abs(x1 - x0) * ACROSS);
        return new Marked(line, z + 0.01F);
    }
}
