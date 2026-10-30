package net.ixdarklord.folios.internal.core.forge;

import net.ixdarklord.coolcatcore.api.core.forge.ForgeModEntrypoint;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.FoliosConstructor;
import net.ixdarklord.folios.internal.core.client.FoliosClientConstructor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Folios.MOD_ID)
public final class FoliosForge extends ForgeModEntrypoint {
    public FoliosForge(FMLJavaModLoadingContext context) {
        super(context);
        this.common(FoliosConstructor::new);
        this.client(() -> FoliosClientConstructor::new);
    }
}
