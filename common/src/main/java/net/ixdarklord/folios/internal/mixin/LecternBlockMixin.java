package net.ixdarklord.folios.internal.mixin;

import net.ixdarklord.folios.internal.FoliosLectern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LecternBlock.class)
public abstract class LecternBlockMixin {
    @Inject(method = "openScreen", at = @At("HEAD"), cancellable = true)
    private void folios$open(Level level, BlockPos pos, Player player, CallbackInfo ci) {
        if (FoliosLectern.use(level, pos, player)) ci.cancel();
    }
}
