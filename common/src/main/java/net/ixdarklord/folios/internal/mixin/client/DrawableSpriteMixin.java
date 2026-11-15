package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.viewer.ViewerTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.common.gui.elements.DrawableSprite", remap = false)
public abstract class DrawableSpriteMixin {
    @Shadow
    @Final
    private ResourceLocation location;
    @Shadow
    @Final
    private int width;
    @Shadow
    @Final
    private int height;

    @Inject(target = @Desc(value = "draw", args = {GuiGraphics.class, int.class, int.class, int.class, int.class, int.class, int.class}),
            at = @At("HEAD"), cancellable = true, require = 0)
    private void folios$sprite(GuiGraphics graphics, int x, int y, int maskTop, int maskBottom, int maskLeft, int maskRight, CallbackInfo ci) {
        if (!ViewerTheme.active()) return;
        if (ViewerTheme.replace(location, x, y, width, height, maskTop, maskBottom, maskLeft, maskRight)) ci.cancel();
    }
}
