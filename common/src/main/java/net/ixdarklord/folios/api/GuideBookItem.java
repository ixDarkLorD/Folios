package net.ixdarklord.folios.api;

import net.ixdarklord.folios.internal.GuideHooks;
import net.ixdarklord.folios.internal.GuideNetwork;
import net.ixdarklord.folios.internal.core.GuideRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An item that opens a guide book when used. Folios registers one, {@code folios:guide_book}, that opens the
 * book its {@code folios:guide_book} component names ({@link GuideBooks#createStack}); a mod can register its own for a
 * single book, which then needs no component:
 * <pre>{@code
 * ITEMS.register("handbook", () -> new GuideBookItem(properties, MyMod.id("handbook")));
 * }</pre>
 * Its name is the book's own, unless the stack has a custom name or {@code item_name}; under it, who the book is from:
 * its {@code "author"} in book.json, or the mod its namespace belongs to.
 */
public class GuideBookItem extends Item {
    private final @Nullable ResourceLocation book;

    /** An item that opens whichever book its stack's component names. */
    public GuideBookItem(Properties properties) {
        this(properties, null);
    }

    /** An item that always opens one book (its stacks' component, if any, wins). */
    public GuideBookItem(Properties properties, @Nullable ResourceLocation book) {
        super(properties);
        this.book = book;
    }

    /** The book a stack of this item opens, or null when it names none. */
    public @Nullable ResourceLocation book(ItemStack stack) {
        ResourceLocation named = GuideBooks.bookOf(stack);
        return named != null ? named : this.book;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation id = this.book(stack);
        if (id == null) return InteractionResultHolder.pass(stack);
        if (level.isClientSide()) GuideHooks.openFromItem(id, hand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity owner, int slot, boolean selected) {
        if (level.isClientSide()) return;
        // A player's book marked as being read but no longer in their reading hand (moved, or they stopped): the mark
        // goes. Anyone else's keeps it.
        if (owner instanceof Player && GuideRegistry.isReading(stack) && !GuideNetwork.isRead(owner, stack)) GuideRegistry.setReading(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = this.book(stack);
        if (id == null) return;
        tooltip.add(Component.translatableWithFallback("folios.guide_book.from", "From %s", GuideHooks.bookAuthor(id)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = this.book(stack);
        return id != null ? GuideHooks.bookName(id) : super.getName(stack);
    }
}
