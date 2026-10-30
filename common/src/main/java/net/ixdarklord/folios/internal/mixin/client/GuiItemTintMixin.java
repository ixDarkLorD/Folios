package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.paint.TintedGuiItems;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemRenderState.class)
public class GuiItemTintMixin implements TintedGuiItems.Tinted {
    @Unique
    private int folios$tint = -1;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void folios$takeTint(CallbackInfo info) {
        folios$tint = TintedGuiItems.current();
    }

    @Override
    public int folios$tint() {
        return folios$tint;
    }
}
