package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.FoliosProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The advancements the client was shown changed (new ones visible, some done): books lay out again what follows them,
// the folios:advancements source and, on a server that doesn't send its own, advancement locks and objectives.
@Mixin(ClientAdvancements.class)
public abstract class ClientAdvancementsMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void folios$advancementsChanged(CallbackInfo ci) {
        FoliosProgress.changed();
    }
}
