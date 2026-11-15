package net.ixdarklord.folios.internal.mixin;

import com.google.gson.JsonObject;
import net.ixdarklord.folios.internal.GuideBookRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapedRecipe.class)
public abstract class ShapedRecipeMixin {
    @Inject(method = "itemStackFromJson", at = @At("RETURN"))
    private static void folios$guideBookTag(JsonObject json, CallbackInfoReturnable<ItemStack> cir) {
        GuideBookRecipes.readTag(json, cir.getReturnValue());
    }
}
