package net.ixdarklord.folios.internal.client.viewer.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.ixdarklord.folios.internal.client.viewer.Viewers;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class FoliosJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Folios.rl("jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        Viewers.add(new JeiBridge(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        Viewers.remove(JeiBridge.NAME);
    }
}
