package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.api.FoliosBookItem;
import net.ixdarklord.folios.internal.client.handheld.SharedBooks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A player whose open book is drawn for this one to read along holds it by its sides, arms low and forward, clear
// of its pages.
@Mixin(PlayerModel.class)
public abstract class SharedBookPoseMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void folios$holdSharedBook(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (SharedBooks.isShown(entity.getId())) {
            SharedBooks.holdArms(model.rightArm, model.leftArm, entity.isCrouching(), SharedBooks.openness(entity.getId()));
            this.folios$sleeves();
            return;
        }
        // A guide book held shut: its arm raised a little, the book lying on the hand.
        if (model.attackTime > 0) return;
        boolean rightMain = entity.getMainArm() == HumanoidArm.RIGHT;
        ItemStack right = rightMain ? entity.getMainHandItem() : entity.getOffhandItem();
        ItemStack left = rightMain ? entity.getOffhandItem() : entity.getMainHandItem();
        if (right.getItem() instanceof FoliosBookItem && model.rightArmPose == HumanoidModel.ArmPose.ITEM) model.rightArm.xRot -= 0.3F;
        if (left.getItem() instanceof FoliosBookItem && model.leftArmPose == HumanoidModel.ArmPose.ITEM) model.leftArm.xRot -= 0.3F;
        this.folios$sleeves();
    }

    // The sleeves follow the arms: vanilla copies them over before this runs.
    @Unique
    private void folios$sleeves() {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }
}
