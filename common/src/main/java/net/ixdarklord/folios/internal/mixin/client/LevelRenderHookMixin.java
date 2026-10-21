package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.build.BuildGuide;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A build guide's ghosts are drawn once the level is, in its depth, before the hands.
@Mixin(GameRenderer.class)
public abstract class LevelRenderHookMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void folios$buildGuide(float partialTicks, long nanoTime, PoseStack pose, CallbackInfo ci) {
        if (BuildGuide.isActive()) {
            BuildGuide.render(((GameRenderer) (Object) this).getMainCamera(), partialTicks);
        }
    }
}
