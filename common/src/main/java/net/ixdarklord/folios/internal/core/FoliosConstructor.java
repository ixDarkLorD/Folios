package net.ixdarklord.folios.internal.core;

import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.folios.internal.FoliosNetwork;

public final class FoliosConstructor implements ModConstructor {
    @Override
    public void onConstructMod() {
        FoliosRegistry.register();
        FoliosNetwork.init();
    }
}
