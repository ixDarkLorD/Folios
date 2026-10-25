# Styles

A style decides how a book is shown. Register one, and books choose it with `"style": "mymod:scroll"`:

```java
import net.ixdarklord.folios.api.client.FoliosStyles;

FoliosStyles.register(MyMod.id("scroll"), (book, at, hand) -> {
    // book: the FoliosBook to show
    // at:   where to open it (a FoliosBook.Location), or null for where the player left off
    // hand: the hand holding the book item, or null when opened another way
    Minecraft.getInstance().setScreen(new ScrollScreen(book, at));
});
```

The built-in styles are `FoliosStyles.SCREEN` (`folios:screen`) and `FoliosStyles.HANDHELD` (`folios:handheld`). A
book naming a style that no mod provides is shown as a screen book.
