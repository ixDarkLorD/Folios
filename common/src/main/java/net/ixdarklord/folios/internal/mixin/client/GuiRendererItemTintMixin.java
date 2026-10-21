package net.ixdarklord.folios.internal.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.ixdarklord.folios.internal.client.paint.TintedGuiItems;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// An item's picture drawn with the colour it was handed to the screen with: white, unless a page fades it in.
@Mixin(GuiRenderer.class)
public class GuiRendererItemTintMixin {
    @ModifyArg(method = "submitBlitFromItemAtlas", index = 11, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
    private int folios$tint(int color, @Local(argsOnly = true) GuiItemRenderState itemState) {
        int tint = ((TintedGuiItems.Tinted) (Object) itemState).folios$tint();
        return tint == -1 ? color : TintedGuiItems.premultiplied(tint);
    }
}
