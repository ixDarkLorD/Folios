package net.ixdarklord.folios.internal.client.paint;

// Items drawn on a screen tinted and faded: the game draws them from a picture of each, made once, and this gives that
// picture's draw a colour. The colour is taken up as the item is handed to the screen, so only what is drawn inside
// draw(...) is tinted.
public final class TintedGuiItems {
    private static int current = -1;

    private TintedGuiItems() {}

    /** Draws what {@code draw} hands the screen tinted by {@code argb}. */
    public static void draw(int argb, Runnable draw) {
        int before = current;
        current = argb;
        try {
            draw.run();
        } finally {
            current = before;
        }
    }

    /** The colour an item handed to the screen now takes. */
    public static int current() {
        return current;
    }

    /** A colour as the item pictures' draw wants it: its red, green and blue times its alpha. */
    public static int premultiplied(int argb) {
        if (argb == -1) return -1;
        int alpha = argb >>> 24;
        int red = (argb >> 16 & 0xFF) * alpha / 255;
        int green = (argb >> 8 & 0xFF) * alpha / 255;
        int blue = (argb & 0xFF) * alpha / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /** An item handed to the screen, with the colour it was handed with. */
    public interface Tinted {
        int folios$tint();
    }
}
