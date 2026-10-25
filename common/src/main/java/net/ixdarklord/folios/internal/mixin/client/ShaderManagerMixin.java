package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Guide books are read again with every resource reload, at the same point as Canvas's own files.
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
    @Inject(method = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
    private void folios$reloadBooks(ShaderManager.Configs preparations, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        FoliosLibrary.reload(manager);
    }
}
