package net.ixdarklord.folios.internal.core.neoforge;

import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.FoliosConstructor;
import net.ixdarklord.folios.internal.core.client.FoliosClientConstructor;
import net.ixdarklord.coolcatcore.api.core.neoforge.NeoForgeModEntrypoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Folios.MOD_ID)
public final class FoliosNeoForge extends NeoForgeModEntrypoint {
    public FoliosNeoForge(ModContainer container) {
        super(container);
        this.common(FoliosConstructor::new);
        this.client(() -> FoliosClientConstructor::new);
    }
}
