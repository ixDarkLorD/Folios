package net.ixdarklord.folios.internal.core;

import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.folios.internal.GuideNetwork;

// Folios's common entry point.
public final class FoliosConstructor implements ModConstructor {
    @Override
    public void onConstructMod() {
        GuideRegistry.register();
        GuideNetwork.init();
    }
}
