# Folios guide book template

A resource pack with one complete guide book. Books are client resources, so they live in a **resource pack** (or a
mod's own `assets`), not a data pack.

## Use it

1. Copy this folder into `.minecraft/resourcepacks/` and turn it on in Options → Resource Packs.
2. Rename `assets/mymod` to your namespace and `folios/my_guide` to your book's id. The book is then `<namespace>:<id>`.
   Rename the keys in `lang/en_us.json` (and where the book's files use them) to match.
3. Get it in game: `/give @s folios:guide_book[folios:guide_book="mymod:my_guide"]`
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

The book's words are in `lang/en_us.json`, so it can be translated like any other resource: add `lang/<language>.json`
with the same keys. A text the JSON leaves out comes from its own key:

| Text | Key |
| --- | --- |
| The book's `name`, `subtitle`, `summary`, `landing`, `author` | `folios.<namespace>.<book>.<name>` |
| A category's `name`, `description` | `folios.<namespace>.<book>.category.<id>.name` / `.description` |
| An entry's `name` | `folios.<namespace>.<book>.entry.<category>.<id>.name` |

Any text can also be written in the JSON: as Markdown, as a translation key, or as `{"translate": "key"}`. Pages here use
keys of their own (`...page1.text`), but that is only a habit; `"text": "Some **words**"` works as well.

## book.json

`"format_version": "3.0"` says which version of Folios's format the book is written in. A book without it is read as a
2.x book and updated as it loads (see the [migration guide](https://ixdarklord.github.io/Folios/docs/migrating-to-3.0/)).

| Key | What it does |
| --- | --- |
| `style` | `"screen"` (a book on the screen, the default) or `"handheld"` (held open in your hands). |
| `icon` | An item id or a texture, for the bar and front page. |
| `accent` | The book's colour for marks and links, as `"#RRGGBB"`. |
| `cover` | `color`, `texture`, `model`, and `text` (where the name and subtitle sit: `title`/`subtitle` with `x`, `y`, `size`, `width`). |
| `reveal` | How a page's content appears: `folios:rise`, `fade`, `ink`, `sweep`, `scatter`, `burn`, `runes`; `seconds`; `once` plays it only the first time each spread is seen this game, `unread` only on spreads the reader has never been shown. |
| `progress_scope` | Where what is read is kept: `"player"` (the default) or `"copy"` (on each copy of the book). |
| `handheld` | `look_around`, `walk`, `tilt`, `distance` and `page_turn` (`"bend"` or `"flat"`). |
| `models` | `item`, `open` and `cover` model ids, `hands`, and `gui`: `"flat"` or `"model"` in slots. |
| `colors` | Page colours by name (`paper`, `text`, `muted`, `heading`, `accent`, `link`, `panel`, `line`) and `dark` for dark mode. |
| `panel` | The colours of the book's tooltips and toasts: `background`, `frame`, `title`, `detail`. |
| `layout`, `textures` | Where tabs, buttons and numbers sit; the texture layers it is drawn with (`boards` for the cover). |
| `overflow`, `alignment`, `title_alignment`, `anchor` | How every page is set, unless a page or element says otherwise. |

Everything is optional. Colours are `"#RRGGBB"` or `"#AARRGGBB"`.

## Categories and entries

A category has `icon` and `sort` (and `name`, `description` when not from the lang file). An entry lives in its
category's folder and has `icon`, `sort`, optional `featured`, and `pages`.

Either can be locked with `requires`: an objective, or a list of them all needed. `"secret": true` hides it until open.

```json
"requires": [{"advancement": "minecraft:story/smelt_iron"}, {"obtained": "minecraft:diamond"}]
```

A page is a `title` and its `elements`, shown one under another; one that doesn't fit starts the next page, and text
runs on. A plain string is a text element, and a page written as one element (`{"type": "recipe", ...}`) is a page of
that element alone:

```json
{"title": "Torches", "elements": ["Light the way:", {"type": "recipe", "recipe": "minecraft:torch"}, {"type": "build", "build": "tower", "height": 60}]}
```

Element types: `text`, `spotlight`, `image`, `recipe`, `entity`, `multiblock`, `structure`, `build`, `links` and `quest`,
each with its own `title` and `text` if it has them. Two are pages of their own: `spread` (two pages wide: a map, picture
or build) and `empty`. `recipe`, `image`, `structure` and `build` take one id or a list.

- `multiblock`: a `pattern` (layers top to bottom) and a `mapping` from each character to a block state, or to a tag
  any block of which will do (`"#minecraft:logs[axis=y]"`, shown in turn). Doors and beds count as one block.
- `structure`: `"structure": "minecraft:igloo/top"`, or a list (ids, or `{"id": ..., "label": ...}`) for several in one
  window, picked with `"selector": "arrows"` (the default) or `"numbers"`.
- `quest`: `objectives`, each one of `advancement`, `read`, `read_category`, `read_book`, `carried`, `obtained`, `quest`,
  `flag` or `any`, with `goal`, `label` and `hidden`; `"in_order": true` opens each once the one before it is done.
- `build`: as `structure`, naming files in the book's `builds/` folder:

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
