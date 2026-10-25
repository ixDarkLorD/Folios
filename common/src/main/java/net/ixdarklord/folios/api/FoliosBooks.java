package net.ixdarklord.folios.api;

import net.ixdarklord.folios.internal.FoliosNetwork;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

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
 * In commands: {@code /give @s folios:guide_book[folios:guide_book="mymod:handbook"]}.
 */
public final class FoliosBooks {
    private FoliosBooks() {}

    /** Folios's guide book item, opening the given book. */
    public static ItemStack createStack(Identifier book) {
        return createStack(FoliosRegistry.BOOK_ITEM.get(), book);
    }

    /** As {@link #createStack(Identifier)}, the book given as {@code "namespace:path"}. */
    public static ItemStack createStack(String book) {
        return createStack(Identifier.parse(book));
    }

    /**
     * A mod's own item carrying a book in its {@code folios:guide_book} component: a {@link FoliosBookItem} of its own
     * opens that book and takes its name. (On any other item it only names the book, for {@link #bookOf}.)
     */
    public static ItemStack createStack(ItemLike item, Identifier book) {
        ItemStack stack = new ItemStack(item);
        stack.set(FoliosRegistry.GUIDE_BOOK.get(), book);
        return stack;
    }

    /**
     * The book a stack names: its {@code folios:guide_book} component, on any item, or else the book a
     * {@link FoliosBookItem} of a mod's own always opens (so its stacks need no component to show their cover); null
     * when none.
     */
    public static @Nullable Identifier bookOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return stack.getItem() instanceof FoliosBookItem item ? item.book(stack) : stack.get(FoliosRegistry.GUIDE_BOOK.get());
    }

    /** Opens a book on a player's screen at its first page. */
    public static void open(ServerPlayer player, Identifier book) {
        open(player, book, null);
    }

    /**
     * Opens a book on a player's screen at an entry ({@code "category/entry"}, its path under the book's
     * {@code entries/}), or at its start for null. Does nothing for a client without Folios.
     */
    public static void open(ServerPlayer player, Identifier book, @Nullable String entry) {
        FoliosNetwork.open(player, book, entry);
    }
}
