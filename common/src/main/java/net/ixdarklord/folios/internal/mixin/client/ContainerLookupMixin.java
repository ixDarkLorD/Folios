package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.BookLookup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerLookupMixin {
    @Shadow
    protected @Nullable Slot hoveredSlot;

    @Inject(method = "extractTooltip", at = @At("HEAD"))
    private void folios$lookUp(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        BookLookup.hovering(hoveredSlot != null && hoveredSlot.hasItem() ? hoveredSlot.getItem() : ItemStack.EMPTY);
    }

    @Inject(method = "extractTooltip", at = @At("RETURN"))
    private void folios$lookedUp(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        BookLookup.doneHovering();
    }
}
