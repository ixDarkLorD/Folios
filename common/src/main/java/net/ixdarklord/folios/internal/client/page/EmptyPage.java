package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.MapCodec;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;

import java.util.List;

// A blank page, for starting what follows on a fresh spread.
public record EmptyPage() implements FoliosPageElement {
    public static final MapCodec<EmptyPage> CODEC = MapCodec.unit(EmptyPage::new);

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        return List.of(painter -> {});
    }
}
