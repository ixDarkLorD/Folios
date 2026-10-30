package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.minecraft.client.Minecraft;

public final class PageSize {
    public static final float MOST = 2.5F;

    private PageSize() {}

    public static float factor() {
        return Math.max(1, Math.min(MOST, auto() / (float) pixels()));
    }

    public static int pixels() {
        int auto = auto();
        return Math.min(auto, Math.max(guiScale() + 1, (int) Math.ceil(auto / MOST)));
    }

    public static float drawn() {
        return pixels() / (float) guiScale();
    }

    private static int guiScale() {
        return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
    }

    private static int auto() {
        return Minecraft.getInstance().getWindow().calculateScale(0, Minecraft.getInstance().isEnforceUnicode());
    }

    private static final float HANDHELD_SPAN = 0.2F;
    private static final float HANDHELD_PRINT = 1.5F;

    public static float handheldFactor() {
        float readable = Minecraft.getInstance().getWindow().getWidth() * HANDHELD_SPAN / (FoliosLayout.PAGE_WIDTH * HANDHELD_PRINT);
        return Math.max(1, Math.min(factor(), readable));
    }

    public static int width(float factor) {
        return Math.round(FoliosLayout.PAGE_WIDTH * Math.max(1, factor));
    }

    public static int height(float factor) {
        return Math.round(FoliosLayout.PAGE_HEIGHT * Math.max(1, factor));
    }
}
