package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.BookLookup;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseLookupMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void folios$lookupButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        BookLookup.button(info.button(), action);
    }
}
