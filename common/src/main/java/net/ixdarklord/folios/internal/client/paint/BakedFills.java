package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BakedFills {
    private static final int MAX_SIDE = 2048;
    private static final long SETTLE_MILLIS = 250;
    private static final Map<String, Site> SITES = new HashMap<>();

    private BakedFills() {}

    static final class Site {
        int[] fills = new int[0];
        long changedAt;
        ResourceLocation id;
        int x0, y0, width, height;
        boolean baked;
    }

    static @Nullable Site site(String name, int[] fills, int count) {
        Site site = SITES.computeIfAbsent(name, key -> new Site());
        long now = System.currentTimeMillis();
        if (!Arrays.equals(site.fills, 0, site.fills.length, fills, 0, count)) {
            site.fills = Arrays.copyOf(fills, count);
            site.changedAt = now;
            release(site);
        }
        if (!site.baked) {
            if (now - site.changedAt < SETTLE_MILLIS || !bake(site, name)) return null;
        }
        return site;
    }

    public static void clear() {
        SITES.values().forEach(BakedFills::release);
        SITES.clear();
    }

    private static void release(Site site) {
        if (site.baked) Minecraft.getInstance().getTextureManager().release(site.id);
        site.baked = false;
    }

    private static boolean bake(Site site, String name) {
        int[] fills = site.fills;
        if (fills.length == 0) return false;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int at = 0; at < fills.length; at += 5) {
            x0 = Math.min(x0, Math.min(fills[at], fills[at + 2]));
            y0 = Math.min(y0, Math.min(fills[at + 1], fills[at + 3]));
            x1 = Math.max(x1, Math.max(fills[at], fills[at + 2]));
            y1 = Math.max(y1, Math.max(fills[at + 1], fills[at + 3]));
        }
        int width = x1 - x0, height = y1 - y0;
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) return false;
        float[] red = new float[width * height], green = new float[width * height], blue = new float[width * height], alpha = new float[width * height];
        for (int at = 0; at < fills.length; at += 5) {
            int fx0 = Math.min(fills[at], fills[at + 2]) - x0, fx1 = Math.max(fills[at], fills[at + 2]) - x0;
            int fy0 = Math.min(fills[at + 1], fills[at + 3]) - y0, fy1 = Math.max(fills[at + 1], fills[at + 3]) - y0;
            int argb = fills[at + 4];
            float a = (argb >>> 24) / 255.0F;
            if (a <= 0) continue;
            float r = (argb >> 16 & 0xFF) / 255.0F * a, g = (argb >> 8 & 0xFF) / 255.0F * a, b = (argb & 0xFF) / 255.0F * a;
            for (int y = fy0; y < fy1; y++) {
                int row = y * width;
                for (int x = fx0; x < fx1; x++) {
                    int i = row + x;
                    float keep = 1 - a;
                    red[i] = r + red[i] * keep;
                    green[i] = g + green[i] * keep;
                    blue[i] = b + blue[i] * keep;
                    alpha[i] = a + alpha[i] * keep;
                }
            }
        }
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                float a = alpha[i];
                int argb = 0;
                if (a > 0) {
                    int ia = Math.round(a * 255);
                    int ir = Math.min(255, Math.round(red[i] / a * 255));
                    int ig = Math.min(255, Math.round(green[i] / a * 255));
                    int ib = Math.min(255, Math.round(blue[i] / a * 255));
                    argb = ia << 24 | ir << 16 | ig << 8 | ib;
                }
                image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
            }
        }
        site.id = Folios.rl("baked/" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
        Minecraft.getInstance().getTextureManager().register(site.id, new DynamicTexture(image));
        site.x0 = x0;
        site.y0 = y0;
        site.width = width;
        site.height = height;
        site.baked = true;
        return true;
    }
}
