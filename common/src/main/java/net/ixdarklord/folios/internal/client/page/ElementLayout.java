package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.folios.api.client.page.FoliosAlign;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.core.Folios;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How an element is set on its page, beside what it shows: what happens when it doesn't fit ({@code "overflow"}),
 * where its parts sit across the page ({@code "alignment"}, {@code "title_alignment"}) and down a box taller than they
 * are ({@code "anchor"}). Written on an element, on a page (for its elements) or in book.json (for the whole book): the
 * nearest that says wins, and what none says keeps how pages have always been set. Each field is null where the JSON
 * doesn't say.
 *
 * @param minHeight the least room worth setting a box in, below which the element starts on the next page
 */
public record ElementLayout(@Nullable Overflow overflow, @Nullable Height height, @Nullable Integer minHeight,
                            @Nullable FoliosAlign alignment, @Nullable FoliosAlign titleAlignment, @Nullable Anchor anchor) {
    public static final ElementLayout NONE = new ElementLayout(null, null, null, null, null, null);
    /** The least room a box is set in when nothing says: about three lines of text. */
    public static final int MIN_HEIGHT = 3 * FoliosText.LINE_HEIGHT;

    /** What an element does when it doesn't fit in what is left of its page. */
    public enum Overflow {
        /** Runs on: its text onto the next pages, its other parts whole onto the next. */
        FLOW,
        /** Moves whole to the next page rather than split. */
        KEEP,
        /** Stays in a box, its text paged in place. */
        FLIP,
        /** Keeps whole when it fits on a page of its own, flips when not even that is enough. */
        AUTO
    }

    /** Where an element's parts sit down a box taller than they are. */
    public enum Anchor { TOP, CENTER, BOTTOM }

    /**
     * How tall an element's box is: {@link Kind#FILL} the rest of the page, {@link Kind#FIT} only as tall as what it
     * holds (up to the rest of the page), or {@link Kind#FIXED} that many pixels.
     */
    public record Height(Kind kind, int pixels) {
        public enum Kind { FILL, FIT, FIXED }
    }

    /** What an object of JSON (an element, a page or a book) says, warning of what it says that can't be read. */
    public static ElementLayout read(@Nullable JsonObject json, String where) {
        if (json == null) return NONE;
        Overflow overflow = null;
        Height height = null;
        Integer minHeight = null;
        if (json.has("overflow")) {
            JsonElement value = json.get("overflow");
            JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
            String mode = object != null ? (object.has("mode") ? object.get("mode").getAsString() : null) : value.getAsString();
            if (mode != null) {
                overflow = switch (mode.toLowerCase(Locale.ROOT)) {
                    case "flow" -> Overflow.FLOW;
                    case "keep" -> Overflow.KEEP;
                    case "flip" -> Overflow.FLIP;
                    case "auto" -> Overflow.AUTO;
                    default -> {
                        Folios.LOGGER.warn("{} has \"overflow\": \"{}\", which is one of flow, keep, flip or auto", where, mode);
                        yield null;
                    }
                };
            }
            if (object != null && object.has("height")) height = height(object.get("height"), where);
            if (object != null && object.has("min_height")) minHeight = Math.max(0, object.get("min_height").getAsInt());
        }
        return new ElementLayout(overflow, height, minHeight, align(json, "alignment", where), align(json, "title_alignment", where), anchor(json, where));
    }

    private static @Nullable Height height(JsonElement json, String where) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) return new Height(Height.Kind.FIXED, Math.max(1, json.getAsInt()));
        String named = json.getAsString().toLowerCase(Locale.ROOT);
        if (named.equals("fill")) return new Height(Height.Kind.FILL, 0);
        if (named.equals("fit")) return new Height(Height.Kind.FIT, 0);
        Folios.LOGGER.warn("{} has an overflow \"height\" of \"{}\", which is a number of pixels, \"fill\" or \"fit\"", where, named);
        return null;
    }

    private static @Nullable FoliosAlign align(JsonObject json, String key, String where) {
        if (!json.has(key)) return null;
        FoliosAlign align = FoliosAlign.named(json.get(key).getAsString());
        if (align == null) Folios.LOGGER.warn("{} has \"{}\": \"{}\", which is one of left, center, right or justify", where, key, json.get(key).getAsString());
        return align;
    }

    // "anchor" also named a page for links before 3.0; that is reported where pages are read, and isn't a place here.
    private static @Nullable Anchor anchor(JsonObject json, String where) {
        if (!json.has("anchor") || !json.get("anchor").isJsonPrimitive()) return null;
        return switch (json.get("anchor").getAsString().toLowerCase(Locale.ROOT)) {
            case "top" -> Anchor.TOP;
            case "center", "centre" -> Anchor.CENTER;
            case "bottom" -> Anchor.BOTTOM;
            default -> null;
        };
    }

    /** This, with what it leaves unsaid taken from {@code outer} (the page's for an element, the book's for a page). */
    public ElementLayout or(ElementLayout outer) {
        return new ElementLayout(overflow != null ? overflow : outer.overflow, height != null ? height : outer.height,
                minHeight != null ? minHeight : outer.minHeight, alignment != null ? alignment : outer.alignment,
                titleAlignment != null ? titleAlignment : outer.titleAlignment, anchor != null ? anchor : outer.anchor);
    }

    /** The overflow to use: {@link Overflow#FLOW} when none is said. */
    public Overflow overflowOrFlow() {
        return overflow != null ? overflow : Overflow.FLOW;
    }

    /** How the title is aligned: its own, else the body's (justified text keeps a left title). */
    public @Nullable FoliosAlign titleAlign() {
        if (titleAlignment != null) return titleAlignment;
        return alignment == FoliosAlign.JUSTIFY ? null : alignment;
    }
}
