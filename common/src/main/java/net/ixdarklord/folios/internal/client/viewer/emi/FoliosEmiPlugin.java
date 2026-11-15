package net.ixdarklord.folios.internal.client.viewer.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.ixdarklord.folios.internal.client.viewer.Viewers;

@EmiEntrypoint
public final class FoliosEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        Viewers.add(new EmiBridge());
    }
}
