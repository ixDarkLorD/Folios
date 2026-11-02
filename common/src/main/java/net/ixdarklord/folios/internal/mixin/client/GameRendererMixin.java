package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.ixdarklord.folios.internal.client.handheld.HandheldRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void folios$noOutline(CallbackInfoReturnable<Boolean> cir) {
        if (HandheldBook.current() != null) cir.setReturnValue(false);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void folios$handsStart(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        HandheldRenderer.vanillaHands = true;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void folios$handsEnd(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        HandheldRenderer.vanillaHands = false;
    }

    @Unique
    private static final float FOLIOS$BOB = 0.3F;

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void folios$steadyBook(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (!HandheldRenderer.vanillaHands || HandheldBook.current() == null) return;
        ci.cancel();
        if (!cameraState.entityRenderState.isPlayer) return;
        float walked = cameraState.entityRenderState.backwardsInterpolatedWalkDistance * Mth.PI;
        float bob = cameraState.entityRenderState.bob * FOLIOS$BOB;
        poseStack.translate(Mth.sin(walked) * bob * 0.5F, -Math.abs(Mth.cos(walked) * bob), 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(walked) * bob * 3.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walked - 0.2F) * bob) * 5.0F));
    }
}
