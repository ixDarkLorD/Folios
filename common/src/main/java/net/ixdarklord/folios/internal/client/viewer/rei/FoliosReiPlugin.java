package net.ixdarklord.folios.internal.client.viewer.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import net.ixdarklord.folios.internal.client.viewer.Viewers;

public class FoliosReiPlugin implements REIClientPlugin {
    public FoliosReiPlugin() {
        Viewers.add(new ReiBridge());
    }
}
