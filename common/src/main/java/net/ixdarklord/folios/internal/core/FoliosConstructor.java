package net.ixdarklord.folios.internal.core;

import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.folios.internal.FoliosNetwork;

// Folios's common entry point.
public final class FoliosConstructor implements ModConstructor {
    @Override
    public void onConstructMod() {
        FoliosRegistry.register();
        FoliosNetwork.init();
    }
}
