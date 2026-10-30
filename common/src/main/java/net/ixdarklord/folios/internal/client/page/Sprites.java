package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Sprites {
    public static final ResourceLocation BAR_BORDER = sprite("bar/border");
    public static final ResourceLocation BAR_GROOVE = sprite("bar/groove");
    public static final ResourceLocation BAR_GILDED_BORDER = sprite("bar/gilded_border");
    public static final ResourceLocation BAR_GILDED_GROOVE = sprite("bar/gilded_groove");
    public static final ResourceLocation BAR_FILL = sprite("bar/fill");
    public static final ResourceLocation BAR_GLINT = sprite("bar/glint");
    public static final ResourceLocation BAR_TICK = sprite("bar/tick");
    public static final ResourceLocation BAR_TICK_LINE = sprite("bar/tick_line");
    public static final ResourceLocation BAR_SLIM_BORDER = sprite("bar/slim_border");
    public static final ResourceLocation BAR_SLIM_GROOVE = sprite("bar/slim_groove");
    public static final ResourceLocation BAR_SLIM_FILL = sprite("bar/slim_fill");
    public static final ResourceLocation CLASP_BORDER = sprite("clasp/border");
    public static final ResourceLocation CLASP_FACE = sprite("clasp/face");
    public static final ResourceLocation PANEL_BORDER = sprite("panel/border");
    public static final ResourceLocation PANEL_FACE = sprite("panel/face");
    public static final ResourceLocation SLOT_BORDER = sprite("slot/border");
    public static final ResourceLocation SLOT_WELL = sprite("slot/well");
    public static final ResourceLocation PAGER_OUTLINE = sprite("pager/outline");
    public static final ResourceLocation PAGER_FACE = sprite("pager/face");
    public static final ResourceLocation PAGER_MARKER = sprite("pager/marker");
    public static final ResourceLocation PAGER_SQUARE = sprite("pager/square");
    public static final ResourceLocation PAGER_DOT = sprite("pager/dot");
    public static final ResourceLocation ARROW = sprite("arrow");
    public static final ResourceLocation SCROLL_TRACK = sprite("scrollbar/track");
    public static final ResourceLocation SCROLL_THUMB = sprite("scrollbar/thumb");
    public static final ResourceLocation SCROLL_GRIP = sprite("scrollbar/grip");
    public static final ResourceLocation LOCKED_SEAL = sprite("locked/seal");
    public static final ResourceLocation LOCKED_SEAL_SHINE = sprite("locked/seal_shine");
    public static final ResourceLocation LOCKED_SEAL_GLOW = sprite("locked/seal_glow");
    public static final ResourceLocation LOCKED_ORNAMENT = sprite("locked/ornament");
    public static final ResourceLocation LOCKED_ORNAMENT_SHINE = sprite("locked/ornament_shine");
    public static final ResourceLocation LOCKED_RULE_LEFT = sprite("locked/rule_left");
    public static final ResourceLocation LOCKED_RULE_RIGHT = sprite("locked/rule_right");
    public static final ResourceLocation LOCKED_RIM = sprite("locked/rim");
    public static final ResourceLocation LOCKED_BACKDROP = sprite("locked/backdrop");
    public static final ResourceLocation LOCKED_FRAME_FILL = sprite("locked/frame_fill");
    public static final ResourceLocation LOCKED_FRAME_LINE = sprite("locked/frame_line");
    public static final ResourceLocation LOCKED_NOTCH_FILL = sprite("locked/notch_fill");
    public static final ResourceLocation LOCKED_NOTCH_LINE = sprite("locked/notch_line");
    public static final ResourceLocation LOCKED_HOLLOW = sprite("locked/hollow");
    public static final ResourceLocation LOCKED_HOLLOW_SHINE = sprite("locked/hollow_shine");
    public static final ResourceLocation LOCKED_TICK = sprite("locked/tick");

    public static final int AS_IS = 0xFFFFFFFF;

    private record Info(int width, int height, String type, int left, int top, int right, int bottom, boolean stretchInner, boolean emptyCenter) {
        static final Info MISSING = new Info(1, 1, "stretch", 0, 0, 0, 0, true, false);
    }

    private static final Map<ResourceLocation, Info> INFO = new ConcurrentHashMap<>();

    private Sprites() {}

    private static ResourceLocation sprite(String name) {
        return Folios.rl("textures/gui/sprites/book/" + name + ".png");
    }

    public static void clear() {
        INFO.clear();
    }

    public static void draw(FoliosPainter painter, ResourceLocation sprite, int x, int y, int width, int height, int argb) {
        if (width <= 0 || height <= 0) return;
        Info info = INFO.computeIfAbsent(sprite, Sprites::read);
        switch (info.type()) {
            case "tile" -> tile(painter, sprite, info, 0, 0, info.width(), info.height(), x, y, width, height, argb);
            case "nine_slice" -> nine(painter, sprite, info, x, y, width, height, argb);
            default -> part(painter, sprite, info, 0, 0, info.width(), info.height(), x, y, width, height, argb);
        }
    }

    public static void draw(FoliosPainter painter, ResourceLocation sprite, int x, int y, int argb) {
        Info info = INFO.computeIfAbsent(sprite, Sprites::read);
        draw(painter, sprite, x, y, info.width(), info.height(), argb);
    }

    private static void nine(FoliosPainter painter, ResourceLocation sprite, Info info, int x, int y, int width, int height, int argb) {
        int left = Math.min(info.left(), width), right = Math.min(info.right(), Math.max(0, width - left));
        int top = Math.min(info.top(), height), bottom = Math.min(info.bottom(), Math.max(0, height - top));
        int midW = info.width() - info.left() - info.right(), midH = info.height() - info.top() - info.bottom();
        int[][] columns = {{0, info.left(), x, left}, {info.left(), midW, x + left, width - left - right},
                {info.width() - info.right(), info.right(), x + width - right, right}};
        int[][] rows = {{0, info.top(), y, top}, {info.top(), midH, y + top, height - top - bottom},
                {info.height() - info.bottom(), info.bottom(), y + height - bottom, bottom}};
        for (int[] column : columns) {
            for (int[] row : rows) {
                if (column[3] <= 0 || row[3] <= 0 || column[1] <= 0 || row[1] <= 0) continue;
                if (column == columns[1] && row == rows[1] && info.emptyCenter()) continue;
                boolean inner = column == columns[1] || row == rows[1];
                if (inner && !info.stretchInner()) tile(painter, sprite, info, column[0], row[0], column[1], row[1], column[2], row[2], column[3], row[3], argb);
                else part(painter, sprite, info, column[0], row[0], column[1], row[1], column[2], row[2], column[3], row[3], argb);
            }
        }
    }

    private static void tile(FoliosPainter painter, ResourceLocation sprite, Info info, int u, int v, int w, int h, int x, int y, int width, int height, int argb) {
        for (int across = 0; across < width; across += w) {
            for (int down = 0; down < height; down += h) {
                int cw = Math.min(w, width - across), ch = Math.min(h, height - down);
                part(painter, sprite, info, u, v, cw, ch, x + across, y + down, cw, ch, argb);
            }
        }
    }

    private static void part(FoliosPainter painter, ResourceLocation sprite, Info info, int u, int v, int w, int h, int x, int y, int width, int height, int argb) {
        if (width <= 0 || height <= 0) return;
        painter.texture(sprite, x, y, width, height, u / (float) info.width(), v / (float) info.height(),
                (u + w) / (float) info.width(), (v + h) / (float) info.height(), argb);
    }

    private static Info read(ResourceLocation sprite) {
        var resources = Minecraft.getInstance().getResourceManager();
        Optional<Resource> png = resources.getResource(sprite);
        if (png.isEmpty()) {
            Folios.LOGGER.warn("The book sprite {} is missing", sprite);
            return Info.MISSING;
        }
        int width, height;
        try (InputStream stream = png.get().open(); DataInputStream data = new DataInputStream(stream)) {
            data.skipNBytes(16);
            width = data.readInt();
            height = data.readInt();
        } catch (IOException e) {
            Folios.LOGGER.warn("Couldn't read the book sprite {}: {}", sprite, e.getMessage());
            return Info.MISSING;
        }
        Optional<Resource> meta = resources.getResource(sprite.withPath(sprite.getPath() + ".mcmeta"));
        if (meta.isEmpty()) return new Info(width, height, "stretch", 0, 0, 0, 0, true, false);
        try (Reader reader = new InputStreamReader(meta.get().open(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject scaling = json.has("gui") ? json.getAsJsonObject("gui").getAsJsonObject("scaling") : null;
            if (scaling == null) return new Info(width, height, "stretch", 0, 0, 0, 0, true, false);
            String type = scaling.get("type").getAsString();
            if (!type.equals("nine_slice")) return new Info(width, height, type, 0, 0, 0, 0, true, false);
            JsonElement border = scaling.get("border");
            int left, top, right, bottom;
            if (border.isJsonPrimitive()) {
                left = top = right = bottom = border.getAsInt();
            } else {
                JsonObject sides = border.getAsJsonObject();
                left = sides.get("left").getAsInt();
                top = sides.get("top").getAsInt();
                right = sides.get("right").getAsInt();
                bottom = sides.get("bottom").getAsInt();
            }
            boolean stretch = scaling.has("stretch_inner") && scaling.get("stretch_inner").getAsBoolean();
            return new Info(width, height, type, left, top, right, bottom, stretch, emptyCenter(png.get(), left, top, width - right, height - bottom));
        } catch (Exception e) {
            Folios.LOGGER.warn("Couldn't read the scaling of the book sprite {}: {}", sprite, e.getMessage());
            return new Info(width, height, "stretch", 0, 0, 0, 0, true, false);
        }
    }

    private static boolean emptyCenter(Resource png, int x0, int y0, int x1, int y1) {
        try (InputStream stream = png.open()) {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(stream);
            if (image == null) return false;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) return false;
                }
            }
            return x1 > x0 && y1 > y0;
        } catch (IOException e) {
            return false;
        }
    }
}
