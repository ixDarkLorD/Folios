package net.ixdarklord.folios.internal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.Util;

// The books' own clock: milliseconds as Util.getMillis() counts them, standing still while the game is paused, so what
// moves in a book (its scenes turning, pages turning and revealing, a held book opening) stops with the game and goes on
// from where it was. Read every client tick as well as when drawn, so a pause is counted out even while nothing reads it.
public final class FoliosClock {
    private static long last = Util.getMillis();
    private static long stopped;

    private FoliosClock() {}

    /** Now, in milliseconds, less the time the game has spent paused. */
    public static synchronized long millis() {
        long now = Util.getMillis();
        if (paused()) stopped += now - last;
        last = now;
        return now - stopped;
    }

    /** Whether the game is paused: a singleplayer world behind its pause menu. */
    public static boolean paused() {
        return Minecraft.getInstance().isPaused();
    }
}
