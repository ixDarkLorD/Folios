package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;

import java.util.List;
import java.util.Optional;

// Text, with a title if it has one; it runs on over as many pages as it needs.
public record TextPage(Optional<String> title, String text) implements GuidePage {
    public static final MapCodec<TextPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("title").forGetter(TextPage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(TextPage::text)
    ).apply(instance, TextPage::new));

    @Override
    public List<Laid> lay(GuideLayout layout) {
        return layout.flow(title.orElse(null), 0, text, null);
    }

    @Override
    public String searchText() {
        return title.orElse("") + " " + text;
    }
}
