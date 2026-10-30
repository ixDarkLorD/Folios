package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

public enum TextAlignment {
    LEFT,
    CENTER,
    RIGHT;

    public int calculateLeft(int anchor, int width) {
        return switch (this) {
            case LEFT -> anchor;
            case CENTER -> anchor - width / 2;
            case RIGHT -> anchor - width;
        };
    }

    public int calculateLeft(int anchor, Font font, FormattedCharSequence text) {
        return this == LEFT ? anchor : this.calculateLeft(anchor, font.width(text));
    }
}
