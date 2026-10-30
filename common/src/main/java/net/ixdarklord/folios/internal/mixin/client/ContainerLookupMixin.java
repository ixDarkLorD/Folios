package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.BookLookup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerLookupMixin {
    @Shadow
    protected @Nullable Slot hoveredSlot;

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void folios$lookUp(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        BookLookup.hovering(hoveredSlot != null && hoveredSlot.hasItem() ? hoveredSlot.getItem() : ItemStack.EMPTY);
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void folios$lookedUp(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        BookLookup.doneHovering();
    }
}
