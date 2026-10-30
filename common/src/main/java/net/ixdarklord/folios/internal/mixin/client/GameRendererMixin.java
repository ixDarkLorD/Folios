package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private boolean folios$drawingHands;

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void folios$noOutline(CallbackInfoReturnable<Boolean> cir) {
        if (HandheldBook.current() != null) cir.setReturnValue(false);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void folios$handsStart(Camera camera, float partialTick, Matrix4f projectionMatrix, CallbackInfo ci) {
        folios$drawingHands = true;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void folios$handsEnd(Camera camera, float partialTick, Matrix4f projectionMatrix, CallbackInfo ci) {
        folios$drawingHands = false;
    }

    @Unique
    private static final float FOLIOS$BOB = 0.3F;

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void folios$steadyBook(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (!folios$drawingHands || HandheldBook.current() == null) return;
        ci.cancel();
        if (!(Minecraft.getInstance().getCameraEntity() instanceof Player player)) return;
        float walked = -(player.walkDist + (player.walkDist - player.walkDistO) * partialTicks) * Mth.PI;
        float bob = Mth.lerp(partialTicks, player.oBob, player.bob) * FOLIOS$BOB;
        poseStack.translate(Mth.sin(walked) * bob * 0.5F, -Math.abs(Mth.cos(walked) * bob), 0.0F);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(walked) * bob * 3.0F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(Math.abs(Mth.cos(walked - 0.2F) * bob) * 5.0F));
    }
}
