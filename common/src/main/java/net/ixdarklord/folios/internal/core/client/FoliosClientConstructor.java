package net.ixdarklord.folios.internal.core.client;

import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.folios.internal.client.FoliosClient;

public final class FoliosClientConstructor implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        FoliosClient.init();
    }
}
