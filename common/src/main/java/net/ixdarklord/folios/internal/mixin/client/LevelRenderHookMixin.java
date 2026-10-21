package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A build guide's ghosts are drawn once the level is, in its depth, before the hands.
@Mixin(GameRenderer.class)
public abstract class LevelRenderHookMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V", shift = At.Shift.AFTER))
    private void folios$buildGuide(CallbackInfo ci) {
        if (BuildGuide.isActive()) {
            var level = ((GameRenderer) (Object) this).gameRenderState().levelRenderState;
            BuildGuide.render(level.cameraRenderState, level.worldPartialTicks);
        }
    }
}
