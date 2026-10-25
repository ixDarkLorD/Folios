# Setting pages

A page's elements are set one under another, from the top, text at the left, and whatever doesn't fit runs on over the
next pages. Four keys change that:

| Key | Values | What it sets |
| --- | --- | --- |
| [`overflow`](#overflow) | `"flow"`, `"keep"`, `"flip"`, `"auto"`, or an object | What an element does when it doesn't fit in what is left of the page. |
| [`alignment`](#alignment) | `"left"`, `"center"`, `"right"`, `"justify"` | Where text and the element's own part sit across the page. |
| [`title_alignment`](#alignment) | `"left"`, `"center"`, `"right"` | Where titles sit across the page. |
| [`anchor`](#anchor) | `"top"`, `"center"`, `"bottom"` | Where content sits down the page, or down an element's box. |

Each can be written on an element, on a page, or in [book.json](book.md#setting-pages). The nearest that says wins: an
element's own, else its page's, else the book's, else how pages have always been set.

```json title="book.json: every page justified, every element kept whole"
{
  "format_version": "3.0",
  "alignment": "justify",
  "overflow": "keep"
}
```

## Overflow

| Mode | When an element doesn't fit in what is left of the page |
| --- | --- |
| `"flow"` | The default. Its text runs on over the next pages; its other parts move whole to the next. |
| `"keep"` | It moves whole to the next page, text and all, rather than being split. |
| `"flip"` | It stays in a box on this page, and its text is paged in place: a pager under it shows a square for each part (up to 7 at a time, the one shown marked), with clasps to step back and on, and the mouse wheel over the box does the same. Everything after it stays on this spread. |
| `"auto"` | It keeps whole when it fits on a page of its own, and flips when not even that is enough. |

```json title="A long description kept beside its card"
{
  "type": "spotlight",
  "item": "minecraft:writable_book",
  "title": "Flip",
  "overflow": "flip",
  "text": "A long text that pages in place under the card, so what comes after it stays on this spread..."
}
```

Written as an object, `overflow` also sizes the element's box:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `mode` | string | `"flow"` | One of the modes above. |
| `height` | `"fill"`, `"fit"` or a number | `"fill"` for flip | How tall its box is: `"fill"` the rest of the page, `"fit"` only as tall as what it holds (up to the rest of the page), or that many pixels. |
| `min_height` | integer | `30` (three lines) | The least room worth flipping text in. With less left on the page, the element starts the next page. |

```json
"overflow": { "mode": "flip", "height": 112 }
```

A box with a `"fill"` or fixed `height` holds its element even when it fits, so the next element starts under the box.
Set down the box with [`anchor`](#anchor).

An element flips one run of text: an element with no text, or with more than one run, flows instead.

## Alignment

`alignment` sets text, and an element's own part (a recipe, a card, a window), across the page:

| Value | Text |
| --- | --- |
| `"left"` | The default. Lines start at the left. |
| `"center"` | Each line centred. |
| `"right"` | Each line ends at the right. |
| `"justify"` | Every line of a paragraph but its last fills the width, the room shared out between its words. A line that would be stretched too far is left as it is. |

`title_alignment` sets the title the same way (justify isn't for titles). Without one, a title follows `alignment`,
except that justified text keeps its title at the left.

```json
{
  "type": "spotlight",
  "item": "minecraft:clock",
  "title": "Left and Right",
  "alignment": "left",
  "title_alignment": "right",
  "text": "The card sits at the left, its title at the right."
}
```

A title too long for its line is made a little smaller, down to three quarters of its size, and past that is cut
short with an ellipsis. The page's icon moves with its title.

### One paragraph

In [text](text-formatting.md#blocks), a paragraph can set itself, whatever the page's alignment:

| Write | Gives |
| --- | --- |
| `->Centred text<-` | The paragraph centred. |
| `->Text at the right` | The paragraph at the right. |

```json
"text": "A page of text.\n\n->*A centred line.*<-\n\n->A line at the right."
```

## Anchor

On a page, `anchor` sets its content down the page: at its `"top"` (the default), its `"center"`, or its `"bottom"`.

```json
{
  "title": "The End",
  "anchor": "center",
  "alignment": "center",
  "text": "A closing page, set in the middle of the page."
}
```

On an element with a box taller than what it holds (an `overflow` `height` of `"fill"` or pixels), `anchor` sets it
down the box.

!!! note "Before 3.0"
    `"anchor"` used to name a page for links. That is now [`"marker"`](pages.md#keys-every-page-takes). A page whose
    `anchor` isn't `top`, `center` or `bottom` is logged as a marker left under the old name.
