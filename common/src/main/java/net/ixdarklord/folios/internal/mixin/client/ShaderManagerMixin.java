package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.GuideLibrary;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

// Guide books are read again with every resource reload, at the same point as Canvas's own files.
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
    // The resources being reloaded, kept from the reload's start: applying the shaders is no longer handed them.
    @Unique
    private @Nullable ResourceManager folios$manager;

    @Inject(method = "reload", at = @At("HEAD"))
    private void folios$keepManager(PreparableReloadListener.SharedState state, Executor tasks, PreparableReloadListener.PreparationBarrier barrier,
                                    Executor reloads, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        this.folios$manager = state.resourceManager();
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void folios$reloadBooks(CallbackInfo ci) {
        if (this.folios$manager != null) GuideLibrary.reload(this.folios$manager);
    }
}
