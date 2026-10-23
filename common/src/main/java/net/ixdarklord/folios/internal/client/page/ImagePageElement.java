package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.GuideText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

// Pictures across the page at their own proportions (at most `height` tall), one at a time with arrows to step
// through several; a caption under them.
public record ImagePageElement(List<ResourceLocation> images, Optional<String> title, String text, boolean border, int height) implements GuidePage {
    public static final MapCodec<ImagePageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.withAlternative(ResourceLocation.CODEC.listOf(1, 32), ResourceLocation.CODEC.xmap(List::of, List::getFirst)).fieldOf("images").forGetter(ImagePageElement::images),
            Codec.STRING.optionalFieldOf("title").forGetter(ImagePageElement::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(ImagePageElement::text),
            Codec.BOOL.optionalFieldOf("border", true).forGetter(ImagePageElement::border),
            Codec.intRange(16, 140).optionalFieldOf("height", 90).forGetter(ImagePageElement::height)
    ).apply(instance, ImagePageElement::new));

    private static final int ARROWS = 12;

    @Override
    public List<Laid> lay(GuideLayout layout) {
        int[] shown = {0};
        int inner = layout.contentWidth() - (border ? 4 : 0);
        // The tallest picture sets the room taken, so the text under them stays put as they change.
        int room = 0;
        for (ResourceLocation image : images) room = Math.max(room, fit(image, inner)[1]);
        int pictures = room + (border ? 4 : 0);
        int frame = pictures + (images.size() > 1 ? ARROWS : 0);
        return layout.flow(title.orElse(null), frame, text, (painter, y) -> {
            GuidePalette palette = painter.palette();
            int count = images.size();
            ResourceLocation image = images.get(shown[0] % count);
            int[] size = fit(image, inner);
            int x = painter.pageWidth() / 2 - size[0] / 2;
            int top = y + (pictures - size[1]) / 2;
            if (border) Shapes.panel(painter, x - 2, top - 2, x + size[0] + 2, top + size[1] + 2);
            painter.texture(image, x, top, size[0], size[1], 0, 0, 1, 1, 0xFFFFFFFF);
            if (count > 1) {
                int arrowsY = y + pictures + 3;
                int middle = painter.pageWidth() / 2;
                painter.centeredText(Component.literal((shown[0] % count + 1) + " / " + count), middle, arrowsY, palette.muted());
                boolean back = painter.action(middle - 36, arrowsY - 2, 14, 11, null, () -> shown[0] = (shown[0] + count - 1) % count);
                boolean next = painter.action(middle + 22, arrowsY - 2, 14, 11, null, () -> shown[0] = (shown[0] + 1) % count);
                painter.text(Component.literal("<"), middle - 31, arrowsY, back ? palette.accent() : palette.muted());
                painter.text(Component.literal(">"), middle + 27, arrowsY, next ? palette.accent() : palette.muted());
            }
        });
    }

    // A picture's size fitted to a width and the height limit, keeping its proportions.
    private int[] fit(ResourceLocation image, int width) {
        int[] size = ConfigStyle.textureSize(image);
        float scale = Math.min(width / (float) size[0], height / (float) size[1]);
        return new int[]{Math.max(1, Math.round(size[0] * scale)), Math.max(1, Math.round(size[1] * scale))};
    }

    @Override
    public String searchText() {
        return GuideText.resolve(title.orElse("")) + " " + GuideText.resolve(text);
    }
}
