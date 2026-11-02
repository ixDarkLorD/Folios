package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.folios.internal.client.screen.BookCache;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.mixin.client.GuiGraphicsInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ScreenAtlas {
    private static final int SIZE = 2048;
    private static final int MAX_SIDE = 512;
    private static final int PAD = 1;
    private static final Identifier ID = Folios.rl("atlas/screen");
    private static final Map<Identifier, Optional<Region>> FILES = new HashMap<>();
    private static final Map<String, Region> SLOTS = new HashMap<>();
    private static final Set<Identifier> ANIMATED = new HashSet<>();
    private static @Nullable DynamicTexture texture;
    private static @Nullable Region white;
    private static int shelfX, shelfY, shelfHeight;
    private static boolean dirty;
    private static int revision;

    private ScreenAtlas() {}

    record Region(int x, int y, int width, int height) {}

    public static boolean blit(GuiGraphicsExtractor graphics, Identifier file, int x0, int y0, int x1, int y1, float u0, float v0, float u1, float v1, int color) {
        if (Math.min(u0, u1) < 0 || Math.max(u0, u1) > 1 || Math.min(v0, v1) < 0 || Math.max(v0, v1) > 1) return false;
        Region region = file(file);
        if (region == null) {
            if (ANIMATED.contains(file)) BookCache.markDynamic();
            return false;
        }
        draw(graphics, region, x0, y0, x1, y1, u0, v0, u1, v1, color);
        return true;
    }

    public static void fill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (white == null) {
            white = allocate(1, 1);
            if (white == null) {
                graphics.fill(x0, y0, x1, y1, color);
                return;
            }
            texture().getPixels().fillRect(white.x - PAD, white.y - PAD, 1 + 2 * PAD, 1 + 2 * PAD, 0xFFFFFFFF);
            dirty = true;
            revision++;
        }
        draw(graphics, white, Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), 0, 0, 1, 1, color);
    }

    static void draw(GuiGraphicsExtractor graphics, Region region, int x0, int y0, int x1, int y1, float u0, float v0, float u1, float v1, int color) {
        DynamicTexture atlas = texture();
        if (dirty) {
            atlas.upload();
            dirty = false;
        }
        float size = SIZE;
        ((GuiGraphicsInvoker) graphics).folios$blit(RenderPipelines.GUI_TEXTURED, atlas.getTextureView(), atlas.getSampler(), x0, y0, x1, y1,
                (region.x + u0 * region.width) / size, (region.x + u1 * region.width) / size,
                (region.y + v0 * region.height) / size, (region.y + v1 * region.height) / size, color);
    }

    static @Nullable Region place(String key, NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Region slot = SLOTS.get(key);
        if (slot == null || slot.width < width || slot.height < height) {
            slot = allocate(width + (width >> 2) + 4, height + (height >> 2) + 4);
            if (slot == null) return null;
            SLOTS.put(key, slot);
        }
        Region region = new Region(slot.x, slot.y, width, height);
        copy(image, region);
        return region;
    }

    public static int revision() {
        return revision;
    }

    public static void clear() {
        FILES.clear();
        ANIMATED.clear();
        revision++;
        SLOTS.clear();
        white = null;
        shelfX = shelfY = shelfHeight = 0;
        if (texture != null) {
            texture.getPixels().fillRect(0, 0, SIZE, SIZE, 0);
            dirty = true;
        }
    }

    private static @Nullable Region file(Identifier id) {
        Optional<Region> known = FILES.get(id);
        if (known != null) return known.orElse(null);
        Region region = null;
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        Optional<Resource> resource = resources.getResource(id);
        boolean animated = resource.isPresent() && animated(resources, id);
        if (animated) ANIMATED.add(id);
        if (resource.isPresent() && !animated) {
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                if (image.getWidth() <= MAX_SIDE && image.getHeight() <= MAX_SIDE) {
                    region = allocate(image.getWidth(), image.getHeight());
                    if (region != null) copy(image, region);
                }
            } catch (IOException ignored) {
            }
        }
        FILES.put(id, Optional.ofNullable(region));
        return region;
    }

    private static boolean animated(ResourceManager resources, Identifier id) {
        Optional<Resource> meta = resources.getResource(id.withSuffix(".mcmeta"));
        if (meta.isEmpty()) return false;
        try (InputStream in = meta.get().open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"animation\"");
        } catch (IOException e) {
            return true;
        }
    }

    private static @Nullable Region allocate(int width, int height) {
        int outerWidth = width + 2 * PAD;
        int outerHeight = height + 2 * PAD;
        if (outerWidth > SIZE || outerHeight > SIZE) return null;
        if (shelfX + outerWidth > SIZE) {
            shelfY += shelfHeight;
            shelfX = 0;
            shelfHeight = 0;
        }
        if (shelfY + outerHeight > SIZE) return null;
        Region region = new Region(shelfX + PAD, shelfY + PAD, width, height);
        shelfX += outerWidth;
        shelfHeight = Math.max(shelfHeight, outerHeight);
        return region;
    }

    private static void copy(NativeImage image, Region region) {
        NativeImage pixels = texture().getPixels();
        for (int y = -PAD; y < region.height + PAD; y++) {
            int sy = Math.clamp(y, 0, region.height - 1);
            for (int x = -PAD; x < region.width + PAD; x++) {
                int sx = Math.clamp(x, 0, region.width - 1);
                pixels.setPixel(region.x + x, region.y + y, image.getPixel(sx, sy));
            }
        }
        dirty = true;
        revision++;
    }

    private static DynamicTexture texture() {
        if (texture == null) {
            texture = new DynamicTexture(() -> "folios screen atlas", SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        return texture;
    }
}
