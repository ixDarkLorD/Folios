package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.BookLookup;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardLookupMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void folios$lookupKey(long handle, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        BookLookup.key(key, scancode, action);
    }
}
