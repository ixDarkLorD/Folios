package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.folios.api.client.page.FoliosAlign;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.core.Folios;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public record ElementLayout(@Nullable Overflow overflow, @Nullable Height height, @Nullable Height minHeight,
                            @Nullable FoliosAlign alignment, @Nullable FoliosAlign titleAlignment, @Nullable Anchor anchor) {
    public static final ElementLayout NONE = new ElementLayout(null, null, null, null, null, null);
    public static final int MIN_HEIGHT = 3 * FoliosText.LINE_HEIGHT;

    public enum Overflow {
        FLOW,
        KEEP,
        FLIP,
        SCROLL,
        AUTO
    }

    public enum Anchor { TOP, CENTER, BOTTOM }

    public record Height(Kind kind, int pixels, float percent) {
        public enum Kind { FILL, FIT, FIXED, PERCENT }

        public Height(Kind kind, int pixels) {
            this(kind, pixels, 0);
        }

        public boolean sized() {
            return kind == Kind.FIXED || kind == Kind.PERCENT;
        }

        public int pixels(int full) {
            return kind == Kind.PERCENT ? Math.max(1, Math.round(full * percent / 100)) : pixels;
        }
    }

    public static ElementLayout read(@Nullable JsonObject json, String where) {
        if (json == null) return NONE;
        Overflow overflow = null;
        Height height = null;
        Height minHeight = null;
        if (json.has("overflow")) {
            JsonElement value = json.get("overflow");
            JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
            String mode = object != null ? (object.has("mode") ? object.get("mode").getAsString() : null) : value.getAsString();
            if (mode != null) {
                overflow = switch (mode.toLowerCase(Locale.ROOT)) {
                    case "flow" -> Overflow.FLOW;
                    case "keep" -> Overflow.KEEP;
                    case "flip" -> Overflow.FLIP;
                    case "scroll" -> Overflow.SCROLL;
                    case "auto" -> Overflow.AUTO;
                    default -> {
                        Folios.LOGGER.warn("{} has \"overflow\": \"{}\", which is one of flow, keep, flip, scroll or auto", where, mode);
                        yield null;
                    }
                };
            }
            if (object != null && object.has("height")) height = height(object.get("height"), where);
            if (object != null && object.has("min_height")) minHeight = size(object.get("min_height"), "min_height", where);
        }
        return new ElementLayout(overflow, height, minHeight, align(json, "alignment", where), align(json, "title_alignment", where), anchor(json, where));
    }

    private static @Nullable Height height(JsonElement json, String where) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String named = json.getAsString().strip().toLowerCase(Locale.ROOT);
            if (named.equals("fill")) return new Height(Height.Kind.FILL, 0);
            if (named.equals("fit")) return new Height(Height.Kind.FIT, 0);
        }
        Height size = size(json, "height", where);
        if (size == null) Folios.LOGGER.warn("{} has an overflow \"height\" of {}, which is a number of pixels, a share of the page (\"60%\"), \"fill\" or \"fit\"", where, json);
        return size;
    }

    private static @Nullable Height size(JsonElement json, String key, String where) {
        if (!json.isJsonPrimitive()) return null;
        if (json.getAsJsonPrimitive().isNumber()) return new Height(Height.Kind.FIXED, Math.max(0, json.getAsInt()));
        String written = json.getAsString().strip();
        if (!written.endsWith("%")) return null;
        try {
            float percent = Float.parseFloat(written.substring(0, written.length() - 1).strip());
            if (percent < 1 || percent > 100) {
                Folios.LOGGER.warn("{} has an overflow \"{}\" of {}, a share of the page from 1% to 100%", where, key, written);
                percent = Math.max(1, Math.min(100, percent));
            }
            return new Height(Height.Kind.PERCENT, 0, percent);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable FoliosAlign align(JsonObject json, String key, String where) {
        if (!json.has(key)) return null;
        FoliosAlign align = FoliosAlign.named(json.get(key).getAsString());
        if (align == null) Folios.LOGGER.warn("{} has \"{}\": \"{}\", which is one of left, center, right or justify", where, key, json.get(key).getAsString());
        return align;
    }

    private static @Nullable Anchor anchor(JsonObject json, String where) {
        if (!json.has("anchor") || !json.get("anchor").isJsonPrimitive()) return null;
        return switch (json.get("anchor").getAsString().toLowerCase(Locale.ROOT)) {
            case "top" -> Anchor.TOP;
            case "center", "centre" -> Anchor.CENTER;
            case "bottom" -> Anchor.BOTTOM;
            default -> null;
        };
    }

    public ElementLayout or(ElementLayout outer) {
        return new ElementLayout(overflow != null ? overflow : outer.overflow, height != null ? height : outer.height,
                minHeight != null ? minHeight : outer.minHeight, alignment != null ? alignment : outer.alignment,
                titleAlignment != null ? titleAlignment : outer.titleAlignment, anchor != null ? anchor : outer.anchor);
    }

    public int leastHeight(int full) {
        return minHeight != null ? minHeight.pixels(full) : MIN_HEIGHT;
    }

    public Overflow overflowOrFlow() {
        return overflow != null ? overflow : Overflow.FLOW;
    }

    public @Nullable FoliosAlign titleAlign() {
        if (titleAlignment != null) return titleAlignment;
        return alignment == FoliosAlign.JUSTIFY ? null : alignment;
    }
}
