package net.ixdarklord.folios.internal;

import net.ixdarklord.folios.api.FoliosBooks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FoliosLectern {
    private FoliosLectern() {}

    public static boolean holdsGuide(ItemStack book) {
        return FoliosBooks.bookOf(book) != null;
    }

    public static boolean place(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (state.getValue(LecternBlock.HAS_BOOK) || !holdsGuide(held)) return false;
        return LecternBlock.tryPlaceBook(player, level, pos, state, held);
    }

    public static boolean use(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) return false;
        ResourceLocation book = FoliosBooks.bookOf(lectern.getBook());
        if (book == null) return false;
        if (player.isSecondaryUseActive()) {
            ItemStack taken = lectern.getBook().copy();
            lectern.setBook(ItemStack.EMPTY);
            LecternBlock.resetBookState(player, level, pos, level.getBlockState(pos), false);
            player.getInventory().placeItemBackInInventory(taken);
            return true;
        }
        if (player instanceof ServerPlayer reader) FoliosBooks.open(reader, book);
        player.awardStat(Stats.INTERACT_WITH_LECTERN);
        return true;
    }
}
