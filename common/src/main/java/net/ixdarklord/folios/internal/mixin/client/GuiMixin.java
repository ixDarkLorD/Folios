package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// No crosshair over a handheld book while it is held up: the pointer is what reads it.
@Mixin(Hud.class)
public abstract class GuiMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void folios$noCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (HandheldBook.current() != null) ci.cancel();
    }
}
