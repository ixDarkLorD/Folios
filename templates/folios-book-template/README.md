# Folios guide book template

A resource pack with one complete guide book. Books are client resources, so they live in a **resource pack** (or a
mod's own `assets`), not a data pack.

## Use it

1. Copy this folder into `.minecraft/resourcepacks/` and turn it on in Options → Resource Packs.
2. Rename `assets/mymod` to your namespace and `folios/my_guide` to your book's id. The book is then `<namespace>:<id>`.
3. Get it in game: `/give @s folios:guide_book{folios_book:"mymod:my_guide"}`
4. Edit the JSON and press **F3 + T** to reload while the game runs.

## Layout

```
assets/mymod/folios/my_guide/
  book.json                       the book itself
  categories/<id>.json            one per tab
  entries/<category>/<id>.json    one per entry, in its category's folder
```

## book.json

| Key | What it does |
| --- | --- |
| `name`, `subtitle` | Shown on the cover, the front page and the item. |
| `style` | `"screen"` (a book on the screen) or `"handheld"` (held open in your hands). |
| `accent`, `cover` | The book's colour for marks and links, and its cover colour. |
| `icon` | An item id or a texture, for the bar and front page. |
| `landing` | The front page's text (Markdown). |
| `reveal` | How a page's content appears: `folios:rise`, `fade`, `ink`, `sweep`, `scatter`, `burn`, `runes`; `seconds`; `once` plays it only on unread pages. |
| `handheld` | `look_around` (hold right mouse button to look about) and `walk` while reading. |
| `cover_text` | Where the name (`title`) and `subtitle` sit on the cover: `x`, `y` (cover is 3–13 across, 1–15 up), `size`, `width`. |
| `models` | `item`, `open` and `cover` model ids, `hands`, and `gui`: `"flat"` or `"model"` in slots. |
| `colors` | Page colours by name (`paper`, `text`, `muted`, `heading`, `accent`, `link`, `panel`, `line`), `dark` for dark mode, `tooltip` (`background`, `frame`, `title`, `detail`). |
| `tilt`, `distance`, `page_turn` | A handheld book's lean, how far from the eyes, `"bend"` or `"flat"` leaves. |
| `layout`, `textures` | Where tabs, buttons and numbers sit; the texture layers it is drawn with. |

Everything except `name` is optional.

## Categories and entries

A category has `name`, `description`, `icon` and `sort`. An entry has `name`, `icon`, `sort`, optional `featured`, and
`pages`. Either can be locked with `"advancement": "<id>"`.

Page types: `text`, `spotlight`, `recipe` (`recipe` or `recipes`), `entity`, `multiblock`, `links`, `quest`,
`spread` (two pages wide: a map, picture or build) and `empty`. A plain string in `pages` is a text page.
