package net.ixdarklord.foliostest;

import net.ixdarklord.folios.api.client.FoliosGuides;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;

// The demo books' client side: their secret entry (folios/*/entries/secrets/the_end.json) opens with this flag.
public final class FoliosTestClient implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        FoliosGuides.setFlag("foliostest:secrets", () -> true);
    }
}
