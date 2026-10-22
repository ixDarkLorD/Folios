package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.GuideText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideStack;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

// An item shown large on a card, named under it (unless the page has a title), then text. The book links the item here.
public record SpotlightPage(GuideStack item, Optional<String> title, String text, boolean link) implements GuidePage {
    public static final MapCodec<SpotlightPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            GuideStack.CODEC.fieldOf("item").forGetter(SpotlightPage::item),
            Codec.STRING.optionalFieldOf("title").forGetter(SpotlightPage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(SpotlightPage::text),
            Codec.BOOL.optionalFieldOf("link", true).forGetter(SpotlightPage::link)
    ).apply(instance, SpotlightPage::new));

    private static final int CARD = 46;

    @Override
    public List<Laid> lay(GuideLayout layout) {
        boolean named = title.isEmpty();
        return layout.flow(title.orElse(null), CARD + (named ? 12 : 0), text, (painter, y) -> {
            int middle = GuideLayout.PAGE_WIDTH / 2;
            int left = middle - CARD / 2;
            Shapes.panel(painter, left, y, left + CARD, y + CARD);
            // A soft wash of the book's colour behind the item.
            GuidePalette palette = painter.palette();
            painter.fill(left + 5, y + 5, left + CARD - 5, y + CARD - 5, GuidePalette.alpha(palette.accent(), 0x22));
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
        return GuideText.resolve(title.orElse("")) + " " + GuideText.resolve(text);
    }

    @Override
    public List<GuideStack> items() {
        return link ? List.of(item) : List.of();
    }
}
