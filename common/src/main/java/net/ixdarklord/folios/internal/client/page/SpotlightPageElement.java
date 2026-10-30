package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record SpotlightPageElement(FoliosStack item, Optional<String> title, String text, boolean link) implements FoliosPageElement {
    public static final MapCodec<SpotlightPageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FoliosStack.CODEC.fieldOf("item").forGetter(SpotlightPageElement::item),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(SpotlightPageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(SpotlightPageElement::text),
            Codec.BOOL.optionalFieldOf("link", true).forGetter(SpotlightPageElement::link)
    ).apply(instance, SpotlightPageElement::new));

    private static final int CARD = 46;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        boolean named = title.isEmpty();
        net.ixdarklord.folios.api.client.page.FoliosAlign align = layout.alignment();
        return layout.flow(title.orElse(null), CARD + (named ? 12 : 0), text, (painter, y) -> {
            int left = FoliosLayout.place(painter, align, CARD);
            int middle = left + CARD / 2;
            FoliosPalette palette = painter.palette();
            painter.texture(layout.book().textures().spotlight(), left, y, CARD, CARD, 0, 0, 1, 1, palette.panel() | 0xFF000000);
            painter.fill(left + 5, y + 5, left + CARD - 5, y + CARD - 5, FoliosPalette.alpha(palette.accent(), 0x22));
            ItemStack stack = item.at(painter.time());
            painter.push();
            painter.translate(middle - 16, y + CARD / 2F - 16);
            painter.scale(2);
            painter.item(stack, 0, 0);
            painter.pop();
            if (named) painter.centeredText(stack.getHoverName().copy().withStyle(ChatFormatting.BOLD), middle, y + CARD + 4, palette.heading());
        });
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }

    @Override
    public List<FoliosStack> items() {
        return link ? List.of(item) : List.of();
    }
}
