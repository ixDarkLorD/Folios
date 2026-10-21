package net.ixdarklord.folios.api;

import net.ixdarklord.folios.internal.GuideNetwork;
import net.ixdarklord.folios.internal.core.GuideRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

/**
 * Guide books from either side: the item that carries one, and opening one for a player from the server.
 * <p>
 * A book lives in resource packs, at {@code assets/<namespace>/guides/<path>/book.json} (id
 * {@code <namespace>:<path>}), with its categories and entries beside it; the client reads them and draws them (see
 * {@code net.ixdarklord.folios.api.client.Guides}). The server only knows books by id.
 * <pre>{@code
 * player.addItem(GuideBooks.createStack(MyMod.id("handbook")));
 * player.addItem(GuideBooks.createStack("mymod:handbook"));
 * player.addItem(GuideBooks.createStack(MyItems.HANDBOOK.get(), MyMod.id("handbook")));
 * GuideBooks.open(player, MyMod.id("handbook"), "basics/first_steps");
 * }</pre>
 * In commands: {@code /give @s folios:guide_book[folios:guide_book="mymod:handbook"]}.
 */
public final class GuideBooks {
    private GuideBooks() {}

    /** Folios's guide book item, opening the given book. */
    public static ItemStack createStack(Identifier book) {
        return createStack(GuideRegistry.BOOK_ITEM.get(), book);
    }

    /** As {@link #createStack(Identifier)}, the book given as {@code "namespace:path"}. */
    public static ItemStack createStack(String book) {
        return createStack(Identifier.parse(book));
    }

    /**
     * A mod's own item carrying a book in its {@code folios:guide_book} component: a {@link GuideBookItem} of its own
     * opens that book and takes its name. (On any other item it only names the book, for {@link #bookOf}.)
     */
    public static ItemStack createStack(ItemLike item, Identifier book) {
        ItemStack stack = new ItemStack(item);
        stack.set(GuideRegistry.GUIDE_BOOK.get(), book);
        return stack;
    }

    /** The book a stack names with the {@code folios:guide_book} component, on any item; null when none. */
    public static @Nullable Identifier bookOf(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(GuideRegistry.GUIDE_BOOK.get());
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
        GuideNetwork.open(player, book, entry);
    }
}
