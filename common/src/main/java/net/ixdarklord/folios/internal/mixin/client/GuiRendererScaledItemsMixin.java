package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.paint.ScaledGuiItems;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiRenderer.class)
public class GuiRendererScaledItemsMixin {
    @Shadow @Final private GuiRenderState renderState;
    @Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;

    @Inject(method = "preparePictureInPicture", at = @At("HEAD"))
    private void folios$startFrame(CallbackInfo ci) {
        ScaledGuiItems.startFrame();
    }

    @Inject(method = "preparePictureInPictureState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void folios$prepare(PictureInPictureRenderState state, int guiScale, CallbackInfo ci) {
        if (state instanceof ScaledGuiItems.State item) {
            ScaledGuiItems.prepare(item, renderState, featureRenderDispatcher, guiScale);
            ci.cancel();
        }
    }

    @Inject(method = "preparePictureInPictureState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;IZ)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void folios$prepareNeoForge(PictureInPictureRenderState state, int guiScale, boolean flag, CallbackInfoReturnable<Boolean> cir) {
        if (state instanceof ScaledGuiItems.State item) {
            ScaledGuiItems.prepare(item, renderState, featureRenderDispatcher, guiScale);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void folios$close(CallbackInfo ci) {
        ScaledGuiItems.close();
    }
}
