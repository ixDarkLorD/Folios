# book.json

`assets/<namespace>/folios/<book>/book.json` describes the book itself. Every key has a default, but you will want at least a `name`.

Colours are written as `"#RRGGBB"` or `"RRGGBB"` (or a number). Texts can be translation keys.

## Example

```json title="book.json"
{
  "name": "My Guide",
  "subtitle": "Everything about my mod",
  "summary": "Notes on the land, gathered on the road.",
  "author": "The Pack Team",
  "style": "handheld",
  "accent": "#C0602A",
  "cover": "#5A3A22",
  "icon": "minecraft:writable_book",
  "landing": "Welcome to **My Guide**.\n\nPick a category from the tabs, or [start here](entry:getting_started/first_steps).",
  "show_progress": true,
  "tilt": 6,
  "distance": 0.64,
  "page_turn": "bend",
  "close_on_click_outside": false,
  "reveal": { "effect": "folios:ink", "seconds": 1.0, "once": true },
  "handheld": { "look_around": true, "walk": true },
  "cover_text": {
    "title": { "x": 8, "y": 11.6, "size": 1.15, "width": 6.4 },
    "subtitle": { "x": 8, "y": 4.05, "size": 0.85, "width": 6.4 }
  },
  "models": { "hands": true, "gui": "flat" },
  "colors": {
    "paper": "#F1E7D0",
    "text": "#3B3026"
  },
  "panel": { "frame": "#E8C66A", "title": "#F2E6CC" },
  "celebrations": { "entry": false },
  "layout": { "tabs": { "x": 0, "y": 6, "spacing": 2 } }
}
```

## Keys

### Content

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `name` | text | `guide.<namespace>.<book>.name` | The book's name: on the cover, the front page, the bar and the item. Without it, the book uses that translation key. |
| `subtitle` | text | none | A line under the name, on the cover and front page. |
| `summary` | text | none | A short description on the cover. With a summary, the name and subtitle share the upper plate of the cover, and the summary (up to two lines) takes the lower one. |
| `landing` | text | none | The text of the book's front page, in [Folios Markdown](text-formatting.md). |
| `icon` | [icon](categories-and-entries.md#icons) | the book's own item | The icon shown in the bar and on the front page. |
| `show_progress` | boolean | `true` | Whether the lists of categories show how much of each has been read, as bars. |
| `author` | text | the mod its namespace belongs to | Who the book is from: the guide book item's tooltip says "From ..." under its name. Without it, the name of the mod whose namespace the book is in. |
| `celebrations` | boolean or object | none | How finishing part of the book is celebrated. Off unless the book says so. See [Celebrations](celebrations.md). |

### Style and handling

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `style` | string | `"screen"` | How the book is shown: `"screen"` or `"handheld"`. A bare name means `folios:<name>`; mods can [add styles](for-mod-developers.md#styles). See [Styles and look](styles-and-look.md). |
| `tilt` | number | `6` | Handheld books: how far the book leans back from facing the reader, in degrees. Clamped to -45 to 80. |
| `distance` | number | `0.64` | Handheld books: how far the book is held from the eyes, in blocks. Clamped to 0.3 to 1.5. |
| `page_turn` | string | `"bend"` | How leaves turn: `"bend"` curls each leaf as it turns, `"flat"` swings it stiffly. |
| `close_on_click_outside` | boolean | `true` for handheld, `false` for screen | Whether clicking off the book closes it. |
| `handheld` | object | see below | Handheld books: what the reader can do while holding the book open. |
| `reveal` | string or object | `folios:rise`, 0.45 s | How a page's content appears once its leaf is down. See [Reveal effects](styles-and-look.md#reveal-effects). |
| `progress` | string | `"player"` | Where what is read in the book is kept. `"player"`: with each player, saved on the server, so it is theirs in any copy of the book and from any computer. `"book"`: on each copy of the book, like a notebook, so whoever reads that copy finds its bookmarks and read pages, and a new copy starts fresh. See [Reading progress](categories-and-entries.md#reading-progress). |

`handheld` has two keys:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `look_around` | boolean | `true` | Holding the right mouse button turns the reader's head, and the book with it. |
| `walk` | boolean | `true` | The movement keys walk, jump, sneak and sprint while the book is open. The arrow keys still turn the leaves. |

### Colours

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `accent` | colour | `#8B6CFF` | The book's colour, for marks, links, rules and highlights. |
| `cover` | colour | `#3A2E58` | The colour of the cover and of the book item's leather. |
| `colors` | object | the style's own | The page colour scheme: page colours and dark mode. See [Colours](colours.md). |
| `panel` | object | taken from the book | The panel colour scheme, for what the book shows off its pages: its tooltips, its toasts and the build guide's panel. See [Panel colours](colours.md#panel-colours). |

### Look

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `textures` | object | Folios's own | The texture layers the book is drawn with. See [Textures](styles-and-look.md#textures). |
| `layout` | object | see [Layout](styles-and-look.md#layout) | Where tabs, turn buttons, page numbers and the close clasp sit. |
| `models` | object | Folios's own | The book's item model, open model, handheld cover model, hands and slot icon. See [Models](styles-and-look.md#models). |
| `item_model` | identifier | none | Older way to set the item model; `models.item` takes its place. |
| `cover_text` | object | the model's own | Where the name, subtitle and summary are lettered on the cover. See [Cover lettering](styles-and-look.md#cover-lettering). |
| `cover_texture` | texture path | none | Handheld books: a texture drawn on the outside of the front cover, over its colour. |

## Reveal

`reveal` is written in one of three ways:

```json
"reveal": "folios:ink"
```

```json
"reveal": { "effect": "folios:burn", "seconds": 1.2, "once": true }
```

```json
"reveal": { "effect": "folios:runes", "seconds": 1.2, "unread": true }
```

```json
"reveal": "none"
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `effect` | identifier | `folios:rise` | The effect. A bare name means `folios:<name>`. |
| `seconds` | number | `0.45` | How long it takes. Clamped to 0 to 5. |
| `once` | boolean | `false` | Play it only the first time each spread is seen this game. Pages of entries read in an earlier game count as seen. |
| `unread` | boolean | `false` | Play it only on spreads the reader hasn't been shown in full before. Kept with their reading progress, so it doesn't play again after a restart, on another computer or in another copy of a book that keeps its own. |

An entry can set its own `reveal` the same way. An effect no mod has registered is logged as a warning and the content
shows at once. See [Reveal effects](styles-and-look.md#reveal-effects) for the list.
