# Opening books and book items

## Opening books

From the server, open a book on a player's screen, at its start or at an entry:

```java
import net.ixdarklord.folios.api.FoliosBooks;

FoliosBooks.open(player, MyMod.id("handbook"));
FoliosBooks.open(player, MyMod.id("handbook"), "basics/first_steps");
```

This does nothing for a client without Folios.

On the client, `FoliosGuides` opens a book directly, in its own style. An entry can include a
[marker](../pages.md#keys-every-page-takes), to open at that page:

```java
import net.ixdarklord.folios.api.client.FoliosGuides;

FoliosGuides.open(MyMod.id("handbook"));                              // where the player left off
FoliosGuides.open(MyMod.id("handbook"), "crafting/tools#pickaxe");     // false if missing or locked
boolean read = FoliosGuides.isRead(MyMod.id("handbook"), "basics/first_steps");
```

`FoliosGuides.book(id)` and `FoliosGuides.books()` give the loaded books. Books are read again whenever resources
reload, so don't keep a `FoliosBook` past that.

## Book items

`folios:guide_book` opens whichever book its stack names. Make a stack for a book with `FoliosBooks.createStack`:

```java
player.addItem(FoliosBooks.createStack(MyMod.id("handbook")));
player.addItem(FoliosBooks.createStack("mymod:handbook"));
```

For an item of your own, register a `FoliosBookItem`. Given a book, it always opens that book and needs nothing on the
stack: a stack from the creative tab, `/give` or a recipe shows the book's cover and opens it as one made with
`createStack` does.

```java
import net.ixdarklord.folios.api.FoliosBookItem;

ITEMS.register("handbook", () -> new FoliosBookItem(properties, MyMod.id("handbook")));
```

A `FoliosBookItem` takes the name of its book, unless the stack has a custom name, and its tooltip says "By: ..." with
the book's [`author`](../book.md#content) when it has one. To put a book on your own item's stack instead, use
`FoliosBooks.createStack(item, bookId)`; a book named on the stack wins over the item's own.

`FoliosBooks.bookOf(stack)` says which book a stack is: the one it names, or else, for a `FoliosBookItem` of your own,
that item's book. It is null for a stack that is neither.

!!! note mc-120 "1.20.1"
    On 1.20.1 the book is stored in the stack's NBT under `folios_book`, not in a `folios:guide_book` component. The
    methods above work the same.
