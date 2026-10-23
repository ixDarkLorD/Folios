package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.handheld.SharedBooks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A player whose open book is drawn for this one to read along holds it by its sides, arms low and forward, clear
// of its pages.
@Mixin(PlayerModel.class)
public abstract class SharedBookPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void folios$holdSharedBook(AvatarRenderState state, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (SharedBooks.isShown(state.id)) {
            SharedBooks.holdArms(model.rightArm, model.leftArm, state.isCrouching, SharedBooks.openness(state.id));
            return;
        }
        // A guide book held shut: its arm raised a little, the book lying on the hand.
        if (state.attackTime > 0) return;
        if (state.rightHandItemStack.getItem() instanceof net.ixdarklord.folios.api.FoliosBookItem && state.rightArmPose == HumanoidModel.ArmPose.ITEM) model.rightArm.xRot -= 0.3F;
        if (state.leftHandItemStack.getItem() instanceof net.ixdarklord.folios.api.FoliosBookItem && state.leftArmPose == HumanoidModel.ArmPose.ITEM) model.leftArm.xRot -= 0.3F;
    }
}
