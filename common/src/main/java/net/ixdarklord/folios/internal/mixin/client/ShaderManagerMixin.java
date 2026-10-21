package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.GuideLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Guide books are read again with every resource reload, at the same point as CoolCatLib's own files: once the new
// shaders are in place (the provider only holds shaders; the books come from the already swapped resource manager).
@Mixin(GameRenderer.class)
public abstract class ShaderManagerMixin {
    @Inject(method = "reloadShaders", at = @At("TAIL"))
    private void folios$reloadBooks(ResourceProvider resourceProvider, CallbackInfo ci) {
        GuideLibrary.reload(Minecraft.getInstance().getResourceManager());
    }
}
