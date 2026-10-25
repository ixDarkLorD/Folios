# book.json

`assets/<namespace>/folios/<book>/book.json` describes the book itself. Every key has a default, so the smallest
book.json is `{"format_version": "3.0"}`, with its words in your [language files](#words).

Colours are written `"#RRGGBB"` or `"#AARRGGBB"`. Texts are [Folios Markdown](text-formatting.md), a translation key,
or `{"translate": ...}`.

## Example

```json title="book.json"
{
  "format_version": "3.0",
  "style": "handheld",
  "icon": "minecraft:writable_book",
  "accent": "#C0602A",
  "cover": { "color": "#5A3A22" },
  "handheld": { "tilt": 10, "walk": false },
  "reveal": { "effect": "folios:ink", "seconds": 1.0, "once": true },
  "colors": { "paper": "#F1E7D0", "text": "#3B3026" },
  "panel": { "frame": "#E8C66A", "title": "#F2E6CC" },
  "celebrations": { "entry": false },
  "alignment": "justify"
}
```

```json title="lang/en_us.json"
{
  "folios.mymod.my_guide.name": "My Guide",
  "folios.mymod.my_guide.subtitle": "Everything about my mod",
  "folios.mymod.my_guide.author": "The Pack Team",
  "folios.mymod.my_guide.landing": "Welcome to **My Guide**.\n\nPick a category from the tabs, or [start here](entry:getting_started/first_steps)."
}
```

## Words

A book's own texts can be written in book.json, or left out to come from the language files under a key named after
the book (a `/` in the book's path becomes a `.`):

| Key | Language key when left out | What it is |
| --- | --- | --- |
| `name` | `folios.<namespace>.<book>.name` | The book's name: on the cover, the front page, the bar and the item. Without the key either, the book's id in title case ("My Guide"). |
| `subtitle` | `folios.<namespace>.<book>.subtitle` | A line under the name, on the cover and front page. |
| `summary` | `folios.<namespace>.<book>.summary` | A short description on the cover. With a summary, the name and subtitle share the upper plate of the cover, and the summary (up to two lines) takes the lower one. |
| `landing` | `folios.<namespace>.<book>.landing` | The text of the book's front page. |
| `author` | `folios.<namespace>.<book>.author` | Who the book is by: the book item's tooltip says "By: ..." under its name. With neither, it says nothing. |

A key that isn't in the language files leaves that text out (the name excepted, as above). Written in book.json, each
takes text, a translation key or `{"translate": "key", "with": [...]}`.

## Keys

### Content

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `format_version` | string | `"2.0"` | The version of the format the book is written in: `"3.0"`. A book without it is read as a 2.x book and updated as it loads. See [Migrating to 3.0](migrating-to-3.0.md#format-versions). |
| `icon` | [icon](categories-and-entries.md#icons) | the book's own item | The icon shown in the bar and on the front page. |
| `show_progress` | boolean | `true` | Whether the lists of categories show how much of each has been read, as bars. |
| `progress_scope` | string | `"player"` | Where what is read is kept: `"player"` or `"copy"`. See [Reading progress](categories-and-entries.md#reading-progress). |
| `celebrations` | boolean or object | none | How finishing part of the book is celebrated. Off unless the book says so. See [Celebrations](celebrations.md). |

### Style and handling

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `style` | string | `"screen"` | How the book is shown: `"screen"` or `"handheld"`. A bare name means `folios:<name>`; mods can [add styles](api/styles.md). See [Styles and look](styles-and-look.md). |
| `close_on_click_outside` | boolean | `true` for handheld, `false` for screen | Whether clicking off the book closes it. |
| `handheld` | object | see below | Handheld books: how the book is held, and what the reader can do while holding it open. |
| `reveal` | string or object | `folios:rise`, 0.45 s | How a page's content appears once its leaf is down. See [Reveal](#reveal). |

`handheld` takes:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `tilt` | number | `6` | How far the book leans back from facing the reader, in degrees. -45 to 80. |
| `distance` | number | `0.64` | How far the book is held from the eyes, in blocks. 0.3 to 1.5. |
| `page_turn` | string | `"bend"` | How leaves turn: `"bend"` curls each leaf as it turns, `"flat"` swings it stiffly. Screen books turn their leaves this way too. |
| `look_around` | boolean | `true` | Holding the right mouse button turns the reader's head, and the book with it. |
| `walk` | boolean | `true` | The movement keys walk, jump, sneak and sprint while the book is open. The arrow keys still turn the leaves. |

### Colours

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `accent` | colour | `#8B6CFF` | The book's colour, for marks, links, rules and highlights. |
| `colors` | object | the style's own | The page colour scheme: page colours and dark mode. See [Colours](colours.md). |
| `panel` | object | taken from the book | The colours of what the book shows off its pages: its tooltips, its toasts and the build guide's panel. See [Panel colours](colours.md#panel-colours). |

### Cover

`cover` groups everything about the book's cover:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `color` | colour | `#3A2E58` | The colour of the cover and of the book item's leather. |
| `texture` | texture path | none | Handheld books: a texture drawn on the outside of the front cover, over its colour. |
| `model` | identifier or `"none"` | `folios:guide/handheld_cover` | What sits on a handheld book's boards: corner caps, clasps, bands. See [Cover models](styles-and-look.md#cover-models). |
| `text` | object | the model's own | Where the name, subtitle and summary are lettered on the cover. See [Cover lettering](styles-and-look.md#cover-lettering). |

```json
"cover": {
  "color": "#5A3A22",
  "texture": "mymod:textures/guide/cover_emboss.png",
  "text": { "title": { "y": 12 } }
}
```

### Look

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `textures` | object | Folios's own | The texture layers the book is drawn with. See [Textures](styles-and-look.md#textures). |
| `layout` | object | see [Layout](styles-and-look.md#layout) | Where tabs, turn buttons, page numbers and the close clasp sit. |
| `models` | object | Folios's own | The book's item model, open model, hands and slot icon. See [Models](styles-and-look.md#models). |

### Setting pages

`overflow`, `alignment`, `title_alignment` and `anchor` in book.json set every page of the book, unless a page or an
element says otherwise. See [Setting pages](setting-pages.md).

## Reveal

`reveal` is written in one of these ways:

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
