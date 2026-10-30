package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.BookLookup;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseLookupMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void folios$lookupButton(long handle, int button, int action, int modifiers, CallbackInfo ci) {
        BookLookup.button(button, action);
    }
}
