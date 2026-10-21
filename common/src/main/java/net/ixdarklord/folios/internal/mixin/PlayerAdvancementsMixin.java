package net.ixdarklord.folios.internal.mixin;

import net.ixdarklord.folios.internal.GuideNetwork;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Tells the guide's network when an advancement is completed or revoked, so the player's books unlock with it.
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void folios$awarded(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ((PlayerAdvancements) (Object) this).getOrStartProgress(holder).isDone()) GuideNetwork.changed(this.player, holder, true);
    }

    @Inject(method = "revoke", at = @At("RETURN"))
    private void folios$revoked(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !((PlayerAdvancements) (Object) this).getOrStartProgress(holder).isDone()) GuideNetwork.changed(this.player, holder, false);
    }
}
