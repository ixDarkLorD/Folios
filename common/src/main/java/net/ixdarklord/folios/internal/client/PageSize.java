package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.minecraft.client.Minecraft;

// How large a book's pages are for the player's GUI scale. At the Auto scale (or a larger one) they are the default
// page; at a smaller one the book keeps its size on the screen, its print a little larger than the GUI's (pixels()),
// and its pages grow by the rest, so the room it gains holds more of what is on them. Never more than MOST times the
// default, so a tiny scale on a huge window doesn't make a page of a whole entry's worth of small print.
public final class PageSize {
    /** The most a page grows, across and down. */
    public static final float MOST = 2.5F;

    private PageSize() {}

    /** How many times the default page the pages are now: 1 at the Auto GUI scale or above. */
    public static float factor() {
        return Math.max(1, Math.min(MOST, auto() / (float) pixels()));
    }

    /**
     * How many of the window's pixels a book pixel takes at a GUI scale smaller than Auto: one more than the GUI's
     * own, so what is on the pages is drawn larger than the rest of the GUI (still a whole number, so the pixel font
     * stays sharp), and the pages grow by the rest of what Auto would draw. No more than Auto's, and enough that the
     * pages needn't grow past MOST.
     */
    public static int pixels() {
        int auto = auto();
        return Math.min(auto, Math.max(guiScale() + 1, (int) Math.ceil(auto / MOST)));
    }

    /** How many GUI pixels a book pixel takes, as {@link #pixels()}. */
    public static float drawn() {
        return pixels() / (float) guiScale();
    }

    private static int guiScale() {
        return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private static int auto() {
        return Minecraft.getInstance().getWindow().calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
    }

    /** How much of the window's width a handheld book's page spans, roughly, held as it is. */
    private static final float HANDHELD_SPAN = 0.2F;
    /** The fewest of the window's pixels a handheld book's page pixel takes, so its print stays easy to read. */
    private static final float HANDHELD_PRINT = 1.5F;

    /**
     * As {@link #factor()} for a handheld book, whose pages stay the same size in the world and print smaller as they
     * grow: no more than keeps its print at HANDHELD_PRINT of the window's pixels a pixel of the page, so it doesn't
     * get too small to read (and a small window keeps the default page).
     */
    public static float handheldFactor() {
        float readable = Minecraft.getInstance().getWindow().getWidth() * HANDHELD_SPAN / (GuideLayout.PAGE_WIDTH * HANDHELD_PRINT);
        return Math.max(1, Math.min(factor(), readable));
    }

    /** A page {@code factor} times the default, in whole pixels. */
    public static int width(float factor) {
        return Math.round(GuideLayout.PAGE_WIDTH * Math.max(1, factor));
    }

    public static int height(float factor) {
        return Math.round(GuideLayout.PAGE_HEIGHT * Math.max(1, factor));
    }
}
