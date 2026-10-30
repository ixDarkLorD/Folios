package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class LevelRenderHookMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void folios$buildGuide(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BuildGuide.isActive()) {
            BuildGuide.render(((GameRenderer) (Object) this).getMainCamera(), deltaTracker.getGameTimeDeltaPartialTick(false));
        }
    }
}
