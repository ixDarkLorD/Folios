package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ShaderManagerMixin {
    @Inject(method = "reloadShaders", at = @At("TAIL"))
    private void folios$reloadBooks(ResourceProvider resourceProvider, CallbackInfo ci) {
        FoliosLibrary.reload(Minecraft.getInstance().getResourceManager());
    }
}
