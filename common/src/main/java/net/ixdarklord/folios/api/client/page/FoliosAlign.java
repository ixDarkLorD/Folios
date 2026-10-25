package net.ixdarklord.folios.api.client.page;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Where something sits across a page: an element's text and what it shows, as {@code "alignment"} and
 * {@code "title_alignment"} in JSON ({@code "left"}, {@code "center"}, {@code "right"} or {@code "justify"}). Justified
 * text fills each line but a paragraph's last; anything that isn't text is centred for it.
 */
public enum FoliosAlign {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY;

    /** As JSON writes it; null for anything else. */
    public static @Nullable FoliosAlign named(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "left" -> LEFT;
            case "center", "centre" -> CENTER;
            case "right" -> RIGHT;
            case "justify" -> JUSTIFY;
            default -> null;
        };
    }

    /** How far in from the left something {@code width} wide sits in {@code room}. */
    public int offset(int room, int width) {
        return switch (this) {
            case LEFT -> 0;
            case CENTER, JUSTIFY -> (room - width) / 2;
            case RIGHT -> room - width;
        };
    }
}
