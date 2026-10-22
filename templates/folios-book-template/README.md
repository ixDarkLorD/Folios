# Folios guide book template

A resource pack with one complete guide book. Books are client resources, so they live in a **resource pack** (or a
mod's own `assets`), not a data pack.

## Use it

1. Copy this folder into `.minecraft/resourcepacks/` and turn it on in Options → Resource Packs.
2. Rename `assets/mymod` to your namespace and `folios/my_guide` to your book's id. The book is then `<namespace>:<id>`.
   Rename the keys in `lang/en_us.json` (and where the book's files use them) to match.
3. Get it in game: `/give @s folios:guide_book{folios_book:"mymod:my_guide"}`
4. Edit the JSON and press **F3 + T** to reload while the game runs.

## Layout

```
assets/mymod/folios/my_guide/
  book.json                       the book itself
  categories/<id>.json            one per tab
  entries/<category>/<id>.json    one per entry, in its category's folder
assets/mymod/lang/en_us.json      the book's words
```

## Words

The book's texts are translation keys, and its words are in `lang/en_us.json`, so it can be translated like any other
resource: add `lang/<language>.json` with the same keys. The keys follow the book: `guide.<namespace>.<book>.name`,
`.category.<id>.name`, `.entry.<id>.page<n>.text` and so on. A text can also be written straight into the JSON; a text
that isn't a key is shown as it is.

## book.json

| Key | What it does |
| --- | --- |
| `name`, `subtitle` | Shown on the cover, the front page and the item. |
| `style` | `"screen"` (a book on the screen) or `"handheld"` (held open in your hands). |
| `accent`, `cover` | The book's colour for marks and links, and its cover colour. |
| `icon` | An item id or a texture, for the bar and front page. |
| `landing` | The front page's text (Markdown). |
| `reveal` | How a page's content appears: `folios:rise`, `fade`, `ink`, `sweep`, `scatter`, `burn`, `runes`; `seconds`; `once` plays it only the first time each spread is seen this game, `unread` only on spreads the reader has never been shown. |
| `progress` | Where what is read is kept: `"player"` (with each player, the default) or `"book"` (on each copy of the book). |
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

A page is a `title` and its `elements`, shown one under another; one that doesn't fit starts the next page, and text
runs on. A plain string is a text element, and a page written as one element (`{"type": "recipe", ...}`) is a page of
that element alone:

```json
{"title": "Torches", "elements": ["Light the way:", {"type": "recipe", "recipe": "minecraft:torch"}, {"type": "build", "build": "tower", "height": 60}]}
```

Element types: `text`, `spotlight`, `image`, `recipe` (`recipe` or `recipes`), `entity`, `multiblock`, `structure`,
`build`, `links` and `quest`, each with its own `title` and `text` if it has them. Two are pages of their own: `spread`
(two pages wide: a map, picture or build) and `empty`.

- `multiblock`: a `pattern` (layers top to bottom) and a `mapping` from each character to a block state, or to a tag
  any block of which will do (`"#minecraft:logs[axis=y]"`, shown in turn). Doors and beds count as one block.
- `structure`: `"structure": "minecraft:igloo/top"`, or `"structures": [...]` (ids, or `{"id": ..., "name": ...}`)
  for several in one window, picked with `"selector": "arrows"` (the default) or `"numbers"`.
- `build`: as `structure`, with `"build"`/`"builds"` naming files in the book's `builds/` folder:

```json
{
  "name": "Watchtower",
  "pattern": [["L L", "   ", "L L"]],
  "mapping": {"L": "#minecraft:logs"},
  "blocks": [
    {"block": "minecraft:cobblestone", "from": [0, -1, 0], "to": [2, -1, 2]},
    {"block": "#minecraft:planks", "from": [0, 1, 0], "to": [2, 3, 2], "hollow": true},
    {"block": "minecraft:lantern", "pos": [1, 2, 1]}
  ]
}
```
