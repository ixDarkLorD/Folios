package net.ixdarklord.folios.internal.core.client;

import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.folios.internal.client.GuideClient;

// Folios's client entry point.
public final class FoliosClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        GuideClient.init();
    }
}
