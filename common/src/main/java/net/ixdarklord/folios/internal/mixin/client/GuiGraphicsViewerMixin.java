package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.ixdarklord.folios.internal.client.viewer.ViewerTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsViewerMixin {
    @Inject(method = "blitSprite(Lcom.mojang.renderpearl.api.pipeline.RenderPipeline;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void folios$sprite(RenderPipeline pipeline, TextureAtlasSprite sprite, int x, int y, int width, int height, int color, CallbackInfo ci) {
        if (ViewerTheme.active() && ViewerTheme.replace(sprite, x, y, width, height, width, height, 0, 0)) ci.cancel();
    }

    @Inject(method = "blitSprite(Lcom.mojang.renderpearl.api.pipeline.RenderPipeline;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIIIIIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void folios$spritePart(RenderPipeline pipeline, TextureAtlasSprite sprite, int spriteWidth, int spriteHeight, int textureX, int textureY,
                                   int x, int y, int width, int height, int color, CallbackInfo ci) {
        if (ViewerTheme.active() && ViewerTheme.replace(sprite, x, y, width, height, spriteWidth, spriteHeight, textureX, textureY)) ci.cancel();
    }

    @Inject(method = "setTooltipForNextFrameInternal", at = @At("HEAD"), cancellable = true)
    private void folios$tooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner,
                                Identifier style, boolean focused, CallbackInfo ci) {
        if (ViewerTheme.active() && ViewerTheme.tooltip(lines)) ci.cancel();
    }
}
