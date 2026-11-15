package net.ixdarklord.folios.internal.mixin;

import net.ixdarklord.folios.internal.FoliosLectern;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternBlockEntity.class)
public abstract class LecternBlockEntityMixin {
    @Shadow
    private ItemStack book;

    @Inject(method = "hasBook", at = @At("RETURN"), cancellable = true)
    private void folios$hasBook(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && FoliosLectern.holdsGuide(book)) cir.setReturnValue(true);
    }
}
