# Page elements

An element type is a record implementing `FoliosPageElement`, with a `MapCodec` that reads its JSON. Register it with
`FoliosPageElements` while the client initialises, before resources load. Its id is what `"type"` names in JSON.

```java
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPageElements;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.minecraft.network.chat.Component;

import java.util.List;

public record CounterElement(String title, int count) implements FoliosPageElement {
    public static final MapCodec<CounterElement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FoliosText.CODEC.fieldOf("title").forGetter(CounterElement::title),
            Codec.INT.fieldOf("count").forGetter(CounterElement::count)).apply(i, CounterElement::new));

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        // A title, 20 pixels kept for our own drawing, no text after it.
        return layout.flow(title, 20, null, (painter, y) -> {
            painter.fill(FoliosLayout.CONTENT_LEFT, y, painter.contentRight(), y + 20, painter.palette().panel());
            painter.text(Component.literal("× " + count), FoliosLayout.CONTENT_LEFT + 4, y + 6, painter.palette().text());
        });
    }
}

FoliosPageElements.register(MyMod.id("counter"), CounterElement.CODEC);
```

```json
{ "type": "mymod:counter", "title": "Count", "count": 3 }
```

`lay` turns the element into the pages it takes; each `Laid` draws one of them. `FoliosLayout.flow` does the usual
shape for you: a title, a part of your own of a given height (`Part.draw(painter, y)`, `y` being where it starts), and
text after it that runs onto further pages when it doesn't fit.

## Text fields

Read text fields with `FoliosText.CODEC` rather than `Codec.STRING`. It takes a string of
[markup](../text-formatting.md) (or a translation key) and `{"translate": "key", "with": [...], "fallback": "..."}`,
as every Folios element does, and gives the text as markup to pass to `flow` or `FoliosText`.

## In a page's elements

A type that lays itself out through `FoliosLayout.flow`, as above, can also be one of a page's
[`elements`](../pages.md#elements): Folios captures what it asks `flow` for (its title, its own part and that part's
height, its text) with `FoliosLayout.capture` and sets it among the page's other elements. It then takes what every
element takes, with no work on your part:

- [Setting](../setting-pages.md): `overflow` (its text kept, or flipped through in a box), `alignment`,
  `title_alignment` and `anchor`.
- [`observe`](../live-lists.md): a live list written after its text.

A spread can't be an element. A type that doesn't lay itself out through `flow` is set as a page of its own.

`FoliosPageElement` also has optional methods:

| Method | What it does |
| --- | --- |
| `spread()` | Return `true` to lay the element over both pages of the open book, like a [spread](../spread-and-empty.md#spread). |
| `searchText()` | Words the book's search should find the page by. |
| `items()` | Items the page is about: pressing one anywhere in the book opens this page. |
| `objectives()` | Quest objectives, making its entry a quest. |

## Page size

A page is `FoliosLayout.PAGE_WIDTH` by `PAGE_HEIGHT` (136 by 180) at the Auto GUI scale and in a handheld book. At a
smaller GUI scale the book keeps its size on the screen and its pages grow, up to 2.5 times, so more fits on each. Lay
out and draw to the page you are given rather than to the default one:

| Instead of | Use | What it is |
| --- | --- | --- |
| `PAGE_WIDTH`, `PAGE_HEIGHT` | `layout.pageWidth()`, `painter.pageWidth()`, and the same for height | The page's size. |
| `CONTENT_RIGHT` | `layout.contentRight()`, `painter.contentRight()` | Where content stops across. |
| `CONTENT_WIDTH` | `layout.contentWidth()` | The width content has. |
| `CONTENT_BOTTOM` | `layout.contentBottom()`, `painter.contentBottom()` | Where content stops down, above the page number and turn buttons. |

`CONTENT_LEFT` and `CONTENT_TOP` are the same on every page. Use the layout's while laying out (`lay`) and the
painter's while drawing. For a picture or scene that should keep its shape on a wider page, `layout.scaled(height)`
gives the height to use for one designed at the default width. Content keeps `MARGIN` (12) pixels in from the page's
sides, clear of the page frame.

## Drawing

Elements draw through a `FoliosPainter`, so the same element works on a screen book and a handheld book, and takes
part in reveal effects. It draws text, items, textures, fills and scenes, moves and scales (`push`, `translate`,
`scale`, `pop`), and marks places that can be pressed (`link`, `action`), dragged (`drag`) or scrolled (`scroll`), with
their tooltips. `painter.palette()` has the book's colours for the current light or dark mode.

Text is laid out with `FoliosText.of(font, markup, width, alignment)`, which splits it into lines for the width and
sets them `LEFT`, `CENTER`, `RIGHT` or `JUSTIFY` (`FoliosAlign`); `layout.alignment()` is the alignment the page or
element asks for.
