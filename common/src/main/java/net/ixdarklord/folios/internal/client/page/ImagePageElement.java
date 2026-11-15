package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.folios.api.client.page.FoliosAlign;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.paint.GifStrips;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ImagePageElement(List<ResourceLocation> images, Optional<String> title, String text, boolean border, int height, Optional<Integer> frames,
                               int frameTime) implements FoliosPageElement {
    public static final MapCodec<ImagePageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.withAlternative(ResourceLocation.CODEC.listOf(1, 32), ResourceLocation.CODEC.xmap(List::of, List::getFirst)).fieldOf("image").forGetter(ImagePageElement::images),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(ImagePageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(ImagePageElement::text),
            Codec.BOOL.optionalFieldOf("border", true).forGetter(ImagePageElement::border),
            Codec.intRange(16, 140).optionalFieldOf("height", 90).forGetter(ImagePageElement::height),
            Codec.intRange(1, 256).optionalFieldOf("frames").forGetter(ImagePageElement::frames),
            Codec.intRange(1, 200).optionalFieldOf("frame_time", 2).forGetter(ImagePageElement::frameTime)
    ).apply(instance, ImagePageElement::new));

    private static final int ARROWS = 12;
    private static final Map<ResourceLocation, int[]> ANIMATIONS = new HashMap<>();

    private record Look(ResourceLocation texture, int width, int height, int frames, int ticks, GifStrips.@Nullable Strip strip) {
        int frame(FoliosPainter painter) {
            if (strip != null) return strip.frames() > 1 ? strip.frameAt(painter.time(0.05F)) : 0;
            if (frames <= 1) return 0;
            float step = ticks / 20F;
            return (int) (painter.time(step) / step) % frames;
        }
    }

    public static void clear() {
        ANIMATIONS.clear();
    }

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        int[] shown = {0};
        int inner = layout.contentWidth() - (border ? 4 : 0);
        int room = 0;
        for (ResourceLocation image : images) room = Math.max(room, fit(this.look(image), inner)[1]);
        int pictures = room + (border ? 4 : 0);
        int frame = pictures + (images.size() > 1 ? ARROWS : 0);
        FoliosAlign align = layout.alignment();
        return layout.flow(title.orElse(null), frame, text, (painter, y) -> {
            FoliosPalette palette = painter.palette();
            int count = images.size();
            Look look = this.look(images.get(shown[0] % count));
            int[] size = fit(look, inner);
            int x = FoliosLayout.place(painter, align, size[0]);
            int top = y + (pictures - size[1]) / 2;
            if (border) Shapes.panel(painter, x - 2, top - 2, x + size[0] + 2, top + size[1] + 2);
            int strip = Math.max(1, look.frames());
            int at = look.frame(painter);
            painter.texture(look.texture(), x, top, size[0], size[1], 0, at / (float) strip, 1, (at + 1) / (float) strip, 0xFFFFFFFF);
            if (count > 1) {
                int arrowsY = y + pictures + 3;
                int middle = x + size[0] / 2;
                painter.centeredText(Component.literal((shown[0] % count + 1) + " / " + count), middle, arrowsY, palette.muted());
                boolean back = painter.action(middle - 36, arrowsY - 2, 14, 11, null, () -> shown[0] = (shown[0] + count - 1) % count);
                boolean next = painter.action(middle + 22, arrowsY - 2, 14, 11, null, () -> shown[0] = (shown[0] + 1) % count);
                painter.text(Component.literal("<"), middle - 31, arrowsY, back ? palette.accent() : palette.muted());
                painter.text(Component.literal(">"), middle + 27, arrowsY, next ? palette.accent() : palette.muted());
            }
        });
    }

    private Look look(ResourceLocation image) {
        if (GifStrips.isGif(image)) {
            GifStrips.Strip strip = GifStrips.get(image);
            if (strip != null) return new Look(strip.texture(), strip.width(), strip.height(), strip.frames(), 1, strip);
        }
        int[] size = ConfigStyle.textureSize(image);
        int[] animation = frames.isPresent() ? new int[]{frames.get(), frameTime} : animation(image, size);
        int count = Math.max(1, Math.min(animation[0], size[1]));
        return new Look(image, size[0], Math.max(1, size[1] / count), count, frames.isPresent() ? frameTime : animation[1], null);
    }

    private static int[] animation(ResourceLocation image, int[] size) {
        return ANIMATIONS.computeIfAbsent(image, id -> {
            Optional<Resource> meta = Minecraft.getInstance().getResourceManager().getResource(id.withSuffix(".mcmeta"));
            if (meta.isEmpty()) return new int[]{1, 2};
            try (Reader reader = meta.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (!(json instanceof JsonObject root) || !(root.get("animation") instanceof JsonObject animation)) return new int[]{1, 2};
                int frameHeight = animation.has("height") ? animation.get("height").getAsInt() : size[0];
                int ticks = animation.has("frametime") ? Math.max(1, animation.get("frametime").getAsInt()) : 1;
                return new int[]{Math.max(1, size[1] / Math.max(1, frameHeight)), ticks};
            } catch (Exception e) {
                return new int[]{1, 2};
            }
        });
    }

    private int[] fit(Look look, int width) {
        float scale = Math.min(width / (float) look.width(), height / (float) look.height());
        return new int[]{Math.max(1, Math.round(look.width() * scale)), Math.max(1, Math.round(look.height() * scale))};
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
