package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// No crosshair over a handheld book while it is held up: the pointer is what reads it.
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void folios$noCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (HandheldBook.current() != null) ci.cancel();
    }
}
