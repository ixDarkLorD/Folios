package net.ixdarklord.folios.internal.client.paint;

public final class TintedGuiItems {
    private static int current = -1;

    private TintedGuiItems() {}

    public static void draw(int argb, Runnable draw) {
        int before = current;
        current = argb;
        try {
            draw.run();
        } finally {
            current = before;
        }
    }

    public static int current() {
        return current;
    }

    public static int premultiplied(int argb) {
        if (argb == -1) return -1;
        int alpha = argb >>> 24;
        int red = (argb >> 16 & 0xFF) * alpha / 255;
        int green = (argb >> 8 & 0xFF) * alpha / 255;
        int blue = (argb & 0xFF) * alpha / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public interface Tinted {
        int folios$tint();
    }
}
