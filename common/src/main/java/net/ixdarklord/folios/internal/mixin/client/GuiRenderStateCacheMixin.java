package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.screen.BookCache;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public class GuiRenderStateCacheMixin {
    @Inject(method = "nextStratum", at = @At("HEAD"))
    private void folios$stratum(CallbackInfo ci) {
        if (BookCache.records((GuiRenderState) (Object) this)) BookCache.stratum();
    }

    @Inject(method = "up", at = @At("HEAD"))
    private void folios$up(CallbackInfo ci) {
        if (BookCache.records((GuiRenderState) (Object) this)) BookCache.up();
    }

    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void folios$element(GuiElementRenderState element, CallbackInfo ci) {
        if (BookCache.records((GuiRenderState) (Object) this)) BookCache.element(element);
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void folios$text(GuiTextRenderState text, CallbackInfo ci) {
        if (BookCache.records((GuiRenderState) (Object) this)) BookCache.text(text);
    }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void folios$item(GuiItemRenderState item, CallbackInfo ci) {
        if (BookCache.records((GuiRenderState) (Object) this)) BookCache.item(item);
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void folios$picture(PictureInPictureRenderState picture, CallbackInfo ci) {
        if (BookCache.records((GuiRenderState) (Object) this)) BookCache.picture(picture);
    }
}
