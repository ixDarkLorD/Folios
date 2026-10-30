package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record ImagePageElement(List<ResourceLocation> images, Optional<String> title, String text, boolean border, int height) implements FoliosPageElement {
    public static final MapCodec<ImagePageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            net.ixdarklord.folios.internal.compat.Compat.withAlternative(net.ixdarklord.folios.internal.compat.Compat.listOf(ResourceLocation.CODEC, 1, 32), ResourceLocation.CODEC.xmap(List::of, list -> list.get(0))).fieldOf("image").forGetter(ImagePageElement::images),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(ImagePageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(ImagePageElement::text),
            Codec.BOOL.optionalFieldOf("border", true).forGetter(ImagePageElement::border),
            Codec.intRange(16, 140).optionalFieldOf("height", 90).forGetter(ImagePageElement::height)
    ).apply(instance, ImagePageElement::new));

    private static final int ARROWS = 12;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        int[] shown = {0};
        int inner = layout.contentWidth() - (border ? 4 : 0);
        int room = 0;
        for (ResourceLocation image : images) room = Math.max(room, fit(image, inner)[1]);
        int pictures = room + (border ? 4 : 0);
        int frame = pictures + (images.size() > 1 ? ARROWS : 0);
        net.ixdarklord.folios.api.client.page.FoliosAlign align = layout.alignment();
        return layout.flow(title.orElse(null), frame, text, (painter, y) -> {
            FoliosPalette palette = painter.palette();
            int count = images.size();
            ResourceLocation image = images.get(shown[0] % count);
            int[] size = fit(image, inner);
            int x = FoliosLayout.place(painter, align, size[0]);
            int top = y + (pictures - size[1]) / 2;
            if (border) Shapes.panel(painter, x - 2, top - 2, x + size[0] + 2, top + size[1] + 2);
            painter.texture(image, x, top, size[0], size[1], 0, 0, 1, 1, 0xFFFFFFFF);
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

    private int[] fit(ResourceLocation image, int width) {
        int[] size = ConfigStyle.textureSize(image);
        float scale = Math.min(width / (float) size[0], height / (float) size[1]);
        return new int[]{Math.max(1, Math.round(size[0] * scale)), Math.max(1, Math.round(size[1] * scale))};
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
