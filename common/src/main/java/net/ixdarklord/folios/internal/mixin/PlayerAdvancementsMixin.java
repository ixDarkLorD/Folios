package net.ixdarklord.folios.internal.mixin;

import net.ixdarklord.folios.internal.FoliosNetwork;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void folios$awarded(Advancement advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ((PlayerAdvancements) (Object) this).getOrStartProgress(advancement).isDone()) FoliosNetwork.changed(this.player, advancement, true);
    }

    @Inject(method = "revoke", at = @At("RETURN"))
    private void folios$revoked(Advancement advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !((PlayerAdvancements) (Object) this).getOrStartProgress(advancement).isDone()) FoliosNetwork.changed(this.player, advancement, false);
    }
}
