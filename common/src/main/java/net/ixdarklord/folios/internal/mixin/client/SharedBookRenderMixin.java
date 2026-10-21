package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.folios.internal.client.handheld.SharedBooks;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A player reading a handheld guide book near this one has it drawn open in their hands, to be read along with.
@Mixin(LivingEntityRenderer.class)
public abstract class SharedBookRenderMixin<T extends LivingEntity> {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void folios$sharedBook(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) SharedBooks.submit(player, partialTicks, poseStack, SubmitNodeCollector.of(buffers), light);
    }
}
