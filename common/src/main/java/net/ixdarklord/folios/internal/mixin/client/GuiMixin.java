package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.handheld.HandheldBook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void folios$noCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (HandheldBook.current() != null) ci.cancel();
    }
}
