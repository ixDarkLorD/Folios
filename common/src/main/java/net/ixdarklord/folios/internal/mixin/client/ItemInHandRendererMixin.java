package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.folios.internal.client.handheld.HandheldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While a handheld guide book is open, it is drawn in the reader's hands in place of what they hold.
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void folios$handheldBook(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
                                     FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || playerState.avatarRenderState == null) return;
        if (HandheldRenderer.draw(frameInterp, poseStack, collector, player, playerState.avatarRenderState.lightCoords)) ci.cancel();
    }
}
