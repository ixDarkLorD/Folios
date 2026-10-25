package net.ixdarklord.folios.internal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ixdarklord.folios.internal.client.paint.ScaledGuiItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// A book's item drawn larger or smaller than 16 pixels goes to the screen as one drawn at its size (ScaledGuiItems).
@Mixin(GuiGraphicsExtractor.class)
public class GuiScaledItemsMixin {
    @WrapOperation(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addItem(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;)V"))
    private void folios$scaled(GuiRenderState screen, GuiItemRenderState item, Operation<Void> original) {
        PictureInPictureRenderState scaled = ScaledGuiItems.scaled(item);
        if (scaled != null) screen.addPicturesInPictureState(scaled);
        else original.call(screen, item);
    }
}
