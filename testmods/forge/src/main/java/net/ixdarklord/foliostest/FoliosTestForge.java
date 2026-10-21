package net.ixdarklord.foliostest;

import net.ixdarklord.coolcatcore.api.core.forge.ForgeModEntrypoint;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("foliostest")
public final class FoliosTestForge extends ForgeModEntrypoint {
    public FoliosTestForge(FMLJavaModLoadingContext context) {
        super(context);
        this.client(() -> FoliosTestClient::new);
    }
}
