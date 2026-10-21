package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.MapCodec;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;

import java.util.List;

// A blank page, for starting what follows on a fresh spread.
public record EmptyPage() implements GuidePage {
    public static final MapCodec<EmptyPage> CODEC = MapCodec.unit(EmptyPage::new);

    @Override
    public List<Laid> lay(GuideLayout layout) {
        return List.of(painter -> {});
    }
}
