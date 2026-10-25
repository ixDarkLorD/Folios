# Spotlight

`"type": "spotlight"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

One item shown large on a card, then text. Pressing that item anywhere else in the book comes back to this page.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `item` | item | required | The item shown. A tag or list cycles through its items. |
| `title` | text | the item's name | A heading. Without one, the item's name is shown under the card. |
| `text` | text | `""` | Text under the card. |
| `link` | boolean | `true` | Whether pressing this item elsewhere in the book opens this page. |

```json
{
  "type": "spotlight",
  "item": "#minecraft:pickaxes",
  "title": "Pickaxes",
  "text": "Item tags are shown in turn."
}
```

The card is the book's `spotlight` texture, tinted with its panel colour: set your own under `"textures"` in
[book.json](../book.md#look), as `"textures": {"spotlight": "mymod:textures/book/my_card.png"}` (46 × 46).
