package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.BookLookup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsLookupMixin {
    @ModifyVariable(method = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;ZZ)V", at = @At("HEAD"), argsOnly = true)
    private List<ClientTooltipComponent> folios$lookup(List<ClientTooltipComponent> lines) {
        ClientTooltipComponent lookup = BookLookup.takePending();
        if (lookup == null) return lines;
        List<ClientTooltipComponent> with = new ArrayList<>(lines);
        with.add(lookup);
        return with;
    }
}
