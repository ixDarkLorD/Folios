package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.internal.client.screen.BookCache;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;

public final class FoliosClock {
    private static long last = Util.getMillis();
    private static long stopped;

    private FoliosClock() {}

    public static synchronized long millis() {
        BookCache.timed();
        long now = Util.getMillis();
        if (paused()) stopped += now - last;
        last = now;
        return now - stopped;
    }

    public static boolean paused() {
        return Minecraft.getInstance().isPaused();
    }
}
