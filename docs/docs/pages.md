# Pages

An entry's `pages` is a list of page objects. Each names its kind with `"type"`; without one it is a `text` page. A
plain string in the list is also a text page:

```json
"pages": [
  "A text page, written as a plain string.",
  { "type": "spotlight", "item": "minecraft:diamond" }
]
```

A type without a namespace is Folios's own: `"text"` is `folios:text`. Mods can [add page types](for-mod-developers.md#page-types).

Pages are laid out for the page size and the reader's language. Text that doesn't fit runs on over the next pages, so
one page object can become several pages in the book.

## Keys every page takes

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `type` | string | `"text"` | The kind of page. |
| `anchor` | string | none | A name links can reach this page by: `entry:<entry id>#<anchor>`. See [Links](text-formatting.md#links). |
| `advancement`, `flag`, `secret` | | | Leave the page out until it opens. See [Locks](categories-and-entries.md#locks). |

Most pages also take `title` and `text`. Texts can be translation keys and use [text formatting](text-formatting.md).

Items are written as described under [Icons](categories-and-entries.md#icons): an id, `id*count`, a `#tag`, a list,
or an item stack object.

## text

Text, with an optional title. It runs on over as many pages as it needs.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `title` | text | none | A heading at the top of the page. |
| `text` | text | `""` | The page's text. |

```json
{
  "type": "text",
  "title": "First Steps",
  "text": "Text runs on over as many pages as it needs.\n\n## A heading\n- a bullet\n- another"
}
```

## spotlight

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

## image

Pictures, kept at their own proportions. Several are shown one at a time, with arrows to step through them.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `images` | texture path, or list of 1–32 | required | The pictures, as full texture paths. |
| `title` | text | none | A heading. |
| `text` | text | `""` | A caption under the pictures. |
| `border` | boolean | `true` | Whether the pictures are framed. |
| `height` | integer | `90` | The most room the pictures take, in page pixels. 16 to 140. |

```json
{
  "type": "image",
  "images": [
    "mymod:textures/gui/guide/valley.png",
    "mymod:textures/gui/guide/rift.png"
  ],
  "title": "Pictures",
  "text": "Several images are shown in turn."
}
```

## recipe

One or two recipes, drawn as the game shows them: a crafting grid, a furnace (with its fuel and time), a stonecutter
or a smithing table. Each slot cycles through every item it accepts, and the block the recipe is made at is shown
under the result.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `recipe` | identifier | | One recipe id. |
| `recipes` | list of 1–2 identifiers | | Two recipes on one page. Use either `recipe` or `recipes`. |
| `title` | text | the result's name | A heading. A page with one recipe and no title is named after what it makes. |
| `text` | text | `""` | Text under the recipes. |

```json
{
  "type": "recipe",
  "recipes": ["minecraft:iron_pickaxe", "minecraft:wooden_pickaxe"],
  "text": "Two recipes fit on a page."
}
```

A recipe that doesn't exist shows "No recipe" and its id.

!!! note "Where recipes come from"
    On 26.1 and later, clients aren't sent recipes, so the page asks the server for them the first time it is shown.
    On 1.21 and 1.20.1, the page reads the recipes the client was sent when the player joined.

## entity

A creature in a window of its own, standing on a grid with its shadow, turning slowly. Drag it to turn it, use the
mouse wheel to zoom, and press the clasp to put it back.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `entity` | identifier | required | The entity type, such as `minecraft:allay`. |
| `title` | text | the entity's name | A heading. Without one, the entity's name is shown under the window. |
| `text` | text | `""` | Text under the window. |
| `zoom` | number | `1.0` | How near it is drawn. 0.25 to 4. |
| `spin` | number | `24` | How fast it turns by itself, in degrees per second. `0` stops it. |
| `yaw` | number | `30` | The angle it starts at, in degrees. |
| `height` | integer | `74` | The window's height in page pixels. 40 to 130. |

```json
{
  "type": "entity",
  "entity": "minecraft:allay",
  "title": "Allay",
  "text": "A helpful spirit.",
  "spin": 12
}
```

## multiblock

A build, turning in a window, with the blocks it takes listed under it, most needed first. Drag and zoom it like an
entity. Its eye clasp shows the build in the world, to be built: see [Building in the world](#building-in-the-world).

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `pattern` | list of layers | required | The build's layers **from the top down**. Each layer is a list of rows, each row a string with one character per block. |
| `mapping` | object | required | Which block each character stands for, as a block state (`"minecraft:lantern[hanging=false]"`). A space, or any character not mapped, is air. |
| `title` | text | none | A heading, also the build's name in the world. |
| `text` | text | `""` | Text under the list. |
| `height` | integer | `78` | The window's height in page pixels. 40 to 130. |

```json
{
  "type": "multiblock",
  "title": "Shrine",
  "pattern": [
    ["   ", " L ", "   "],
    ["S S", "   ", "S S"],
    ["CCC", "CGC", "CCC"]
  ],
  "mapping": {
    "C": "minecraft:chiseled_stone_bricks",
    "S": "minecraft:stone_brick_wall",
    "G": "minecraft:glowstone",
    "L": "minecraft:lantern[hanging=false]"
  },
  "text": "Layers from the top down."
}
```

### Building in the world

Pressing a build's eye clasp puts the book away and shows the build in the world as see-through ghost blocks, where
you look and turned the way you face. Right-click to set it down. The layer to build next is the clearest, wrong blocks
show red, and a panel on the HUD tracks progress and what is still needed. It ends when the build is done (a toast
says so), when you walk away, or when you sneak and right-click.

Which corner of the screen the panel sits in, and how far from it, are client settings in Folios's config:
`buildPanel.corner` (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`), `buildPanel.x` and `buildPanel.y`
(both 8 by default).

## links

Rows linking to other entries, each with the entry's icon. Entries the reader can't see yet are left out; locked ones
show as `???`.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `links` | list of 1–12 | required | Each an entry id (optionally with `#anchor`), or `{"entry": ..., "label": ...}`. |
| `title` | text | "See also" | A heading. |
| `text` | text | `""` | Text under the links. |

Each link object takes:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `entry` | string | required | The entry id, optionally with `#anchor`. |
| `label` | text | the entry's name | The words shown for it. |

```json
{
  "type": "links",
  "links": [
    "getting_started/recipes",
    { "entry": "advanced/my_build", "label": "Build a shrine" }
  ]
}
```

## quest

A checklist of objectives with progress bars, updated live as the player plays. See [Quests](quests.md) for the
objective types.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `objectives` | list of 1–8 | required | The [objectives](quests.md#objectives). |
| `title` | text | "Quest" | A heading. |
| `text` | text | `""` | Text under the checklist. |
| `reward` | text | none | What it gives, shown as "Reward: ..." under the list. |

```json
{
  "type": "quest",
  "title": "Getting Going",
  "text": "These tick themselves off as you play.",
  "reward": "Bragging rights",
  "objectives": [
    { "read": "getting_started/first_steps" },
    { "item": "minecraft:oak_log", "count": 8 },
    { "advancement": "minecraft:story/mine_stone", "label": "Mine some stone" }
  ]
}
```

## empty

A blank page. Use it to start what follows on a fresh spread. It takes no keys besides `type`.

```json
{ "type": "empty" }
```

## spread

One thing laid over both pages of the open book, across the fold: a wide picture, a build, or a map of places linked
to entries. A spread always starts on a left page; an empty page is put before it when needed. Its title is centred
over it and its text is centred under it.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `title` | text | none | A heading, centred across the fold. |
| `text` | text | `""` | Centred lines under the window, as many as fit. |
| `image` | texture path | none | A wide picture, stretched to the window. |
| `build` | object | none | A build: `pattern` and `mapping`, as on a [multiblock](#multiblock) page. Its blocks are listed under it, and its eye clasp [builds it in the world](#building-in-the-world). |
| `map` | object | none | A map of places. See below. |
| `height` | integer | `104` | The window's height in page pixels. 40 to 150. A build's window may be shorter, to leave room for its block list and text. |

Give one of `image`, `build` or `map`. If more than one is given, `build` wins, then `map`, then `image`.

```json title="A wide picture"
{
  "type": "spread",
  "title": "Far Off",
  "image": "mymod:textures/gui/guide/panorama.png",
  "height": 128
}
```

```json title="A build"
{
  "type": "spread",
  "title": "A Watchtower",
  "build": {
    "pattern": [
      ["  L  ", "     ", "     ", "     ", "     "],
      [" CCC ", "C   C", "C   C", "C   C", " CDC "],
      [" BBB ", "BBBBB", "BBGBB", "BBBBB", " BBB "]
    ],
    "mapping": {
      "C": "minecraft:stone_bricks",
      "B": "minecraft:polished_andesite",
      "G": "minecraft:glowstone",
      "L": "minecraft:lantern[hanging=false]",
      "D": "minecraft:air"
    }
  },
  "text": "Drag it round; the eye builds it in the world."
}
```

### Maps

A `map` places nodes in the window, each a link to an entry, joined by dotted paths.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `nodes` | list | required | The places. |
| `paths` | list of pairs | `[]` | Each `["<node id>", "<node id>"]` draws a dotted path between two nodes. |
| `background` | texture path | none | A picture under the map, stretched to the window. |

Each node takes:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `id` | string | required | The node's name, for `paths`. |
| `x`, `y` | integer | required | Where its middle is, in the window's pixels from its top left. The window is 252 pixels wide. Nodes are kept inside the frame and off the fold. |
| `icon` | [icon](categories-and-entries.md#icons) | `minecraft:book` | Its icon. |
| `entry` | string | none | The entry it opens. |
| `name` | text | none | A tooltip shown when the pointer is over it. |

```json title="A map"
{
  "type": "spread",
  "title": "The Path Ahead",
  "map": {
    "nodes": [
      { "id": "start", "x": 22, "y": 58, "icon": "minecraft:oak_sapling", "entry": "basics/first_steps", "name": "First Steps" },
      { "id": "tools", "x": 84, "y": 90, "icon": "minecraft:iron_pickaxe", "entry": "crafting/tools", "name": "Tools" },
      { "id": "end", "x": 228, "y": 58, "icon": "minecraft:ender_eye", "entry": "secrets/the_end", "name": "The End" }
    ],
    "paths": [
      ["start", "tools"],
      ["tools", "end"]
    ]
  },
  "text": "Each place opens its entry."
}
```
