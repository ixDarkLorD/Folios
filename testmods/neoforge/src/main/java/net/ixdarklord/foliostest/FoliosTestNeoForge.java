package net.ixdarklord.foliostest;

import net.ixdarklord.coolcatcore.api.core.neoforge.NeoForgeModEntrypoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod("foliostest")
public final class FoliosTestNeoForge extends NeoForgeModEntrypoint {
    public FoliosTestNeoForge(ModContainer container) {
        super(container);
        this.client(() -> FoliosTestClient::new);
    }
}
