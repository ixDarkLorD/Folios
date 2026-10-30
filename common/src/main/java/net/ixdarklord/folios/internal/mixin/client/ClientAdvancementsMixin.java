package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.FoliosProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientAdvancements.class)
public abstract class ClientAdvancementsMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void folios$advancementsChanged(CallbackInfo ci) {
        FoliosProgress.changed();
    }
}
