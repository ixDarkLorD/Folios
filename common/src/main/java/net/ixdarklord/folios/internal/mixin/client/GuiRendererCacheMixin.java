package net.ixdarklord.folios.internal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.ixdarklord.folios.internal.client.screen.BookCache;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public class GuiRendererCacheMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void folios$renderCache(CallbackInfo ci) {
        BookCache.renderPending((GuiRenderer) (Object) this);
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget folios$cacheTarget(GameRenderer renderer, Operation<RenderTarget> original) {
        RenderTarget target = BookCache.target((GuiRenderer) (Object) this);
        return target != null ? target : original.call(renderer);
    }
}
