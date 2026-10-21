package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.folios.internal.client.handheld.HandheldRenderer;
import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While a handheld guide book is open, it is drawn in the reader's hands in place of what they hold.
@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    // Held up, the book is in place of the hands: vanilla's hands are kept at rest with what they hold, so once it is
    // put back the item is simply there, as the book left it, with no swap of its own.
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void folios$restWhileReading(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (HandheldBook.current() == null || player == null) return;
        mainHandHeight = oMainHandHeight = 1.0F;
        offHandHeight = oOffHandHeight = 1.0F;
        mainHandItem = player.getMainHandItem();
        offHandItem = player.getOffhandItem();
        ci.cancel();
    }


    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void folios$handheldBook(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int lightCoords, CallbackInfo ci) {
        if (HandheldRenderer.draw(frameInterp, poseStack, collector, player, lightCoords)) ci.cancel();
    }
}
