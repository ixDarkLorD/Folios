# Styles and look

Every page type works in either style, so you can switch a book between them with one key. This page covers the
styles, then everything in `book.json` that changes how a book looks.

## Styles

Set with `"style"` in [book.json](book.md).

=== "screen (default)"

    A flat book in the middle of the screen, with the world dimmed around it.

    - A bar like the book's cover across the top shows where you are, with a bookmark star, search (a glass that opens
      into a field as you type), a light/dark paper switch, and a button to fold the book away.
    - Ribbon tabs come out of the leaves' edge: one for the front page and one per category, with bookmarks and an
      index of every entry from A to Z at the foot.
    - Leaves fold across the spine as they turn.
    - Using the book item raises it out of the hand and opens it part way in 3D before the screen comes up.
    - Clicking off the book does **not** close it, unless `close_on_click_outside` is `true`.

=== "handheld"

    The book held open in the player's hands, in the world.

    - Using the item raises the book shut. Click its cover (or press ++arrow-right++, ++enter++ or ++space++) to open
      it. Leaves turn by hand, with a shadow under them, and the pointer presses what is on the pages.
    - Once it lies open, the same bar grows out over its head. Its close button shuts the book in the reader's hands;
      ++esc++ puts it away. Clicking off the book also puts it away, unless `close_on_click_outside` is `false`.
    - Holding the right mouse button looks around, and the movement keys walk, jump, sneak and sprint (turn either
      off with `handheld`). The arrow keys still turn the leaves.
    - The perspective key works: from outside, you see yourself holding the book.
    - Put away while open, it opens again where it was left.

In both styles, other players within 16 blocks see the book in the reader's hands. A handheld book is shown at the
spread being read, its leaves turning as the reader turns them.

Mods can add more styles with [`GuideStyles.register`](for-mod-developers.md#styles). A book naming a style that no
mod provides is shown as a screen book.

### Style keys

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `tilt` | number | `6` | Handheld: how far the book leans back from facing the reader, in degrees (-45 to 80). |
| `distance` | number | `0.64` | Handheld: how far the book is held from the eyes, in blocks (0.3 to 1.5). |
| `page_turn` | string | `"bend"` | `"bend"` curls each leaf as it turns, with what is on it following the curve; `"flat"` swings it stiffly. Both styles. |
| `close_on_click_outside` | boolean | `true` for handheld, `false` for screen | Whether a click off the book closes it. |
| `handheld` | object | both `true` | Handheld: `look_around` (hold the right mouse button to look about) and `walk` (move while reading). |

```json title="book.json"
{
  "name": "Field Journal",
  "style": "handheld",
  "tilt": 10,
  "distance": 0.7,
  "page_turn": "flat",
  "handheld": { "look_around": true, "walk": false }
}
```

## Colours

`accent` and `cover` set the book's two main colours. `colors` sets the rest of its colour scheme. Any colour left out
keeps the style's own.

Colours are `"#RRGGBB"`, or `"#AARRGGBB"` with alpha.

| Key | What it colours |
| --- | --- |
| `paper` | The paper. |
| `text` | Body text. |
| `muted` | Quieter text: captions, counts, code. |
| `heading` | Headings and titles. |
| `accent` | Marks and rules on the page (the book's `accent` by default). |
| `link` | Links. |
| `panel` | Panels and slots on the page. |
| `line` | Frames and dividing lines. |
| `dark` | An object with the same keys, for a screen book's dark mode. |
| `tooltip` | An object for the book's tooltips: `background`, `frame`, `title` and `detail`. |

By default, tooltips take on the book: a background from the cover colour and a gilt frame warmed by the accent.

```json title="book.json"
{
  "colors": {
    "paper": "#F1E7D0",
    "text": "#3B3026",
    "muted": "#8B7B66",
    "heading": "#2E241B",
    "link": "#5A3A8A",
    "panel": "#E6D8BA",
    "line": "#C4B08C",
    "dark": { "paper": "#1F222C", "text": "#D5D8E2" },
    "tooltip": { "background": "#281E18", "frame": "#FFE28C", "title": "#F2E6CC", "detail": "#A89A86" }
  }
}
```

A category can give its own tab a colour with `"color"` in its file. See
[Categories](categories-and-entries.md#categories).

## Textures

Both styles are drawn from texture layers. They are grey or white and tinted by the book's colours, so one set suits
any book. `textures` names a book's own; any left out keep the default.

| Key | Default | What it is |
| --- | --- | --- |
| `page` | `folios:textures/guide/page.png` | Each page's paper, stretched to the page (136 × 180), tinted with the paper colour. |
| `frame` | `folios:textures/guide/page_frame.png` | Drawn over the paper, under the page's content. Its alpha is how strong each part is. |
| `cover` | `folios:textures/guide/cover.png` | The cover around the pages, and a handheld book's boards, tinted with the cover colour. |
| `tab` | `folios:textures/guide/ribbon.png` | A ribbon tab, its tip at the right. Stretched to the tab's length, tinted with its category's colour. |
| `tab_plate` | `folios:textures/guide/ribbon_plate.png` | The plate under each tab's icon, tinted with the paper colour. |
| `backdrop` | none (the world, dimmed) | Screen books: drawn over the whole screen behind the book. |

```json title="book.json"
{
  "textures": {
    "page": "mymod:textures/guide/parchment.png",
    "backdrop": "mymod:textures/guide/desk.png"
  }
}
```

!!! tip
    A resource pack that replaces the textures at `folios:textures/guide/` restyles every book that doesn't name its
    own.

## Layout

`layout` moves what sits around a page's content, in page pixels. Any value left out keeps its default. Values outside
the range are clamped.

| Key | Values | Default | Range |
| --- | --- | --- | --- |
| `tabs` | `x`: out from the book's edge; `y`: down from the pages' top; `spacing`: between tabs | `0`, `6`, `2` | x -20 to 40, y -20 to 160, spacing -10 to 30 |
| `turn_buttons` | `x`: in from the page's outer edge; `y`: up from its bottom | `8`, `19` | x 0 to 60, y 0 to 170 |
| `page_numbers` | `x`: across from the page's middle; `y`: up from its bottom | `0`, `17` | x -60 to 60, y 0 to 170 |
| `close_button` | `x`, `y`: a page close clasp, where one is drawn | `14`, `11` | x 0 to 125, y 0 to 169 |

```json title="book.json"
{
  "layout": {
    "tabs": { "x": 0, "y": 6, "spacing": 2 },
    "turn_buttons": { "x": 11, "y": 21 },
    "page_numbers": { "x": 0, "y": 17 }
  }
}
```

## Models

The book item is drawn as a bound book in three dimensions in the hand, on the ground and in item frames, and as a
flat icon in inventory slots. `models` changes what is drawn:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `item` | identifier | Folios's book | The item model of the book's item. |
| `open` | identifier | Folios's open book | The model used while someone reads the book in hand, as other players see it. |
| `cover` | identifier or `"none"` | `folios:guide/handheld_cover` | What sits on a handheld book's boards: corner caps, clasps, bands. `"none"` for nothing. |
| `hands` | boolean | `true` | Whether the reader's hands are drawn holding a handheld book. |
| `gui` | string | `"flat"` | In inventory slots: `"flat"` for the flat icon, `"model"` for the book's model. |

```json title="book.json"
{
  "models": {
    "item": "mymod:handbook",
    "open": "mymod:handbook_open",
    "cover": "mymod:guide/handbook_cover",
    "hands": true,
    "gui": "model"
  }
}
```

The older `"item_model"` key at the top of book.json still works in place of `models.item`.

Where `item` and `open` point depends on the version:

=== "26.1 and later"

    Item model definitions: `"mymod:handbook"` is `assets/mymod/items/handbook.json`.

=== "1.21 and 1.20.1"

    Block-format models: `"mymod:handbook"` is `assets/mymod/models/item/handbook.json`.

### Cover models

A `cover` model is a model in the block and item model format, at `assets/<namespace>/models/<path>.json`. It is laid
on the outside of each board, mirrored on the left one:

- x runs from 0 at the spine to 16 at the board's outer edge, y from 0 at its foot to 16 at its head, and z from 0 on
  the board outwards, in the same units as x.
- Faces with `"tintindex": 0` take the cover colour; `"tintindex": 1` the book's accent.
- An element with `"board": "front"` or `"board": "back"` sits on that board only.
- An element whose `name` contains `#shut` holds the book shut, like a strap or clasp: it unlatches first as the book
  opens and is gone once it lies open.
- An element whose `name` contains `#open` shows only once the book lies open.
- An element whose `name` contains `#spine` wraps round the spine and bends with it as the cover opens.

### Item model helpers

These are for item model definitions on 26.1 and later:

- **`folios:guide_cover` tint**: the cover colour of the book the stack names. With `"part": "accent"`, the book's
  accent instead. One model can then suit every book.
- **`folios:guide_cover_text` special model**: the book's name and subtitle lettered on the cover. Placed in the
  model's own units with `x` (default 8), `title_y` (11.5), `subtitle_y` (4.25), `z` (10.21) and `width` (8.5), or by
  `"markers": "<model id>"`: a model whose elements named `#title`, `#subtitle` and `#summary` mark where each line
  goes, centred on the element's face and sized to it.

```json title="assets/mymod/items/handbook.json"
{
  "model": {
    "type": "minecraft:composite",
    "models": [
      {
        "type": "minecraft:model",
        "model": "mymod:item/handbook",
        "tints": [
          { "type": "folios:guide_cover" },
          { "type": "folios:guide_cover", "part": "accent" }
        ]
      },
      {
        "type": "minecraft:special",
        "base": "mymod:item/handbook",
        "model": { "type": "folios:guide_cover_text", "markers": "mymod:item/handbook" }
      }
    ]
  }
}
```

## Cover lettering

`cover_text` places the book's name (`title`), `subtitle` and `summary` on the cover of the book item and of a handheld
book. Places are in the item model's units: the default cover runs from 3 to 13 across and from 1 (its foot) to 15
(its head) up.

| Key | Type | Default for title / subtitle | What it does |
| --- | --- | --- | --- |
| `x` | number | `8` / `8` | The middle of the line, across. |
| `y` | number | `11.6` / `4.05` | The middle of the line, up. |
| `size` | number | `1.15` / `0.85` | How large: 1 is a line 1.6 units tall. 0.1 to 4. |
| `width` | number | `6.4` / `6.4` | The room across; a longer line shrinks to fit. 0.5 to 16. |

`summary` takes the same keys, with the subtitle's defaults. A book that says nothing keeps where its item model puts
the lettering.

```json title="book.json"
{
  "cover_text": {
    "title": { "x": 8, "y": 11.6, "size": 1.15, "width": 6.4 },
    "subtitle": { "y": 4.05, "size": 0.85 }
  }
}
```

## Reveal effects

A page's content appears once its leaf is down, through a reveal effect. Everything drawn on the page takes part,
including items, scenes and pages added by other mods. A spread's two pages reveal together across the fold.

| Effect | What it does |
| --- | --- |
| `folios:rise` | The default. Each part fades in as it rises a few pixels into place, from the top of the page down. |
| `folios:fade` | Everything fades in together. |
| `folios:ink` | The page fills in from top to bottom, like ink soaking down it, with a soft edge. |
| `folios:sweep` | The page fills in from the fold outwards. |
| `folios:scatter` | Parts drift in from a little way off, each from its own side. |
| `folios:burn` | Each thing is burnt in turn down the page: it flares white-hot, burns with flames and sparks, chars and settles. |
| `folios:runes` | Parts first show as glowing runes that flicker and settle into their words, under drifting runes and a shimmer of light. |
| `none` | Content shows at once. |

Set it for the whole book in [book.json](book.md#reveal), or for one entry with `reveal` in the entry's file. A bare
name means `folios:<name>`.

```json
"reveal": "folios:ink"
```

```json
"reveal": { "effect": "folios:burn", "seconds": 1.2, "once": true }
```

```json
"reveal": { "effect": "folios:runes", "seconds": 1.2, "unread": true }
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `effect` | identifier | `folios:rise` | The effect. |
| `seconds` | number | `0.45` | How long it takes, 0 to 5. |
| `once` | boolean | `false` | Play only the first time each spread is seen this game. Pages of entries read in an earlier game count as seen. |
| `unread` | boolean | `false` | Play only on spreads the reader hasn't been shown in full before. Remembered with their [reading progress](categories-and-entries.md#reading-progress), so it doesn't play again after a restart. |

Mods can add effects with [`GuideReveals.register`](for-mod-developers.md#reveal-effects).
