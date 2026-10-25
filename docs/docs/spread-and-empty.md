# Spread and empty

Two kinds of page stand on their own and can't be [elements](pages.md#elements): `spread`, one thing over both pages of
the open book, and `empty`, a blank page.

## spread

One thing laid over both pages of the open book, across the fold: a wide picture, a build, or a map of places linked
to entries. A spread always starts on a left page; an empty page is put before it when needed. Its title is centred
over it and its text is centred under it.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `title` | text | none | A heading, centred across the fold. |
| `text` | text | `""` | Centred lines under the window, as many as fit. |
| `image` | texture path | none | A wide picture, stretched to the window. |
| `build` | string | none | A [build file](elements/build-files.md) of the book's, named as a [build](elements/build.md) element names one. |
| `multiblock` | object | none | A build written in place: `pattern` and `mapping`, as on a [multiblock](elements/multiblock.md) element. |
| `map` | object | none | A map of places. See below. |
| `height` | integer | `104` | The window's height in page pixels. 40 to 150. A build's window may be shorter, to leave room for its block list and text. |

Give one of `image`, `build`, `multiblock` or `map`. If more than one is given, a build (`multiblock`, then `build`)
wins, then `map`, then `image`. A build's blocks are listed under it, and its eye clasp
[builds it in the world](elements/multiblock.md#building-in-the-world).

```json title="A wide picture"
{
  "type": "spread",
  "title": "Far Off",
  "image": "mymod:textures/gui/guide/panorama.png",
  "height": 128
}
```

```json title="A build from a build file"
{
  "type": "spread",
  "title": "A Watchtower",
  "build": "watchtower"
}
```

```json title="A build written in place"
{
  "type": "spread",
  "title": "A Watchtower",
  "multiblock": {
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
| `label` | text | none | A tooltip shown when the pointer is over it. |

```json title="A map"
{
  "type": "spread",
  "title": "The Path Ahead",
  "map": {
    "nodes": [
      { "id": "start", "x": 22, "y": 58, "icon": "minecraft:oak_sapling", "entry": "basics/first_steps", "label": "First Steps" },
      { "id": "tools", "x": 84, "y": 90, "icon": "minecraft:iron_pickaxe", "entry": "crafting/tools", "label": "Tools" },
      { "id": "end", "x": 228, "y": 58, "icon": "minecraft:ender_eye", "entry": "secrets/the_end", "label": "The End" }
    ],
    "paths": [
      ["start", "tools"],
      ["tools", "end"]
    ]
  },
  "text": "Each place opens its entry."
}
```

## empty

A blank page. Use it to start what follows on a fresh spread. It takes no keys besides `type`.

```json
{ "type": "empty" }
```
