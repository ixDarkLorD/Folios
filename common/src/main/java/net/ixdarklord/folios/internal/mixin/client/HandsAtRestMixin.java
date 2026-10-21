package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Held up, the book is in place of the hands: vanilla's hands are kept at rest with what they hold, so once it is put
// back the item is simply there, as the book left it, with no swap of its own.
@Mixin(value = FirstPersonHandsAndItems.class, priority = 900)
public abstract class HandsAtRestMixin {
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void folios$restWhileReading(LocalPlayer player, CallbackInfo ci) {
        if (HandheldBook.current() == null) return;
        mainHandHeight = oMainHandHeight = 1.0F;
        offHandHeight = oOffHandHeight = 1.0F;
        mainHandItem = player.getMainHandItem();
        offHandItem = player.getOffhandItem();
        ci.cancel();
    }
}
