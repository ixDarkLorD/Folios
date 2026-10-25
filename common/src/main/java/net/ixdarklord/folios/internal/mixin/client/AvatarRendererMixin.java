package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A player reading a handheld guide book holds it up in both hands, as everyone around them sees.
@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void folios$reading(AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        // The handheld book's hands, drawn alone: posed as 26.1 draws a hand, for nothing held.
        if (net.ixdarklord.folios.internal.client.handheld.HandheldRenderer.drawingHands) {
            cir.setReturnValue(HumanoidModel.ArmPose.EMPTY);
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() && stack.has(FoliosRegistry.READING.get())) cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
    }
}
