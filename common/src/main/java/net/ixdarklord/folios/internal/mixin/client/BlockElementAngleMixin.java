package net.ixdarklord.folios.internal.mixin.client;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Model elements turned by any angle, as later versions allow: the open book's leaves lean at angles of their own.
// 1.21.1 bakes any angle; only its model reader refuses those that aren't a multiple of 22.5 degrees.
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
public abstract class BlockElementAngleMixin {
    @Inject(method = "getAngle", at = @At("HEAD"), cancellable = true)
    private void folios$anyAngle(JsonObject json, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(GsonHelper.getAsFloat(json, "angle"));
    }
}
