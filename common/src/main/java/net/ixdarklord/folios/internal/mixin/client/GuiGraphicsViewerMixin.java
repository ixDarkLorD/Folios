package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.viewer.ViewerTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsViewerMixin {
    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void folios$tooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, CallbackInfo ci) {
        if (ViewerTheme.active() && ViewerTheme.tooltip(lines)) ci.cancel();
    }
}
