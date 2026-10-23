package net.ixdarklord.folios.api;

import net.ixdarklord.folios.internal.GuideNetwork;
import net.ixdarklord.folios.internal.core.GuideRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * Guide books from either side: the item that carries one, and opening one for a player from the server.
 * <p>
 * A book lives in resource packs, at {@code assets/<namespace>/folios/<path>/book.json} (id
 * {@code <namespace>:<path>}), with its categories and entries beside it; the client reads them and draws them (see
 * {@code net.ixdarklord.folios.api.client.FoliosGuides}). The server only knows books by id.
 * <pre>{@code
 * player.addItem(FoliosBooks.createStack(MyMod.id("handbook")));
 * player.addItem(FoliosBooks.createStack("mymod:handbook"));
 * player.addItem(FoliosBooks.createStack(MyItems.HANDBOOK.get(), MyMod.id("handbook")));
 * FoliosBooks.open(player, MyMod.id("handbook"), "basics/first_steps");
 * }</pre>
 * In commands: {@code /give @s folios:guide_book{folios_book:"mymod:handbook"}}.
 */
public final class FoliosBooks {
    private FoliosBooks() {}

    /** Folios's guide book item, opening the given book. */
    public static ItemStack createStack(ResourceLocation book) {
        return createStack(GuideRegistry.BOOK_ITEM.get(), book);
    }

    /** As {@link #createStack(ResourceLocation)}, the book given as {@code "namespace:path"}. */
    public static ItemStack createStack(String book) {
        return createStack(new ResourceLocation(book));
    }

    /**
     * A mod's own item carrying a book in its NBT ({@code folios_book}): a {@link FoliosBookItem} of its own opens that
     * book and takes its name. (On any other item it only names the book, for {@link #bookOf}.)
     */
    public static ItemStack createStack(ItemLike item, ResourceLocation book) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(GuideRegistry.GUIDE_BOOK, book.toString());
        return stack;
    }

    /**
     * The book a stack names: in its NBT ({@code folios_book}), on any item, or else the book a {@link FoliosBookItem}
     * of a mod's own always opens (so its stacks need no NBT to show their cover); null when none.
     */
    public static @Nullable ResourceLocation bookOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return stack.getItem() instanceof FoliosBookItem item ? item.book(stack) : named(stack);
    }

    /** The book a stack's NBT names; null when none. */
    static @Nullable ResourceLocation named(ItemStack stack) {
        if (stack.isEmpty() || stack.getTag() == null || !stack.getTag().contains(GuideRegistry.GUIDE_BOOK, net.minecraft.nbt.Tag.TAG_STRING)) return null;
        return ResourceLocation.tryParse(stack.getTag().getString(GuideRegistry.GUIDE_BOOK));
    }

    /** Opens a book on a player's screen at its first page. */
    public static void open(ServerPlayer player, ResourceLocation book) {
        open(player, book, null);
    }

    /**
     * Opens a book on a player's screen at an entry ({@code "category/entry"}, its path under the book's
     * {@code entries/}), or at its start for null. Does nothing for a client without Folios.
     */
    public static void open(ServerPlayer player, ResourceLocation book, @Nullable String entry) {
        GuideNetwork.open(player, book, entry);
    }
}
