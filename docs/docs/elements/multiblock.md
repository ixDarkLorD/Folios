# Multiblock

`"type": "multiblock"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

A build, turning in a window. Drag and zoom it like an entity. Its eye clasp shows the build in the world, to be
built: see [Building in the world](#building-in-the-world). The list clasp in its bottom-left corner lays the
**requirement list** over the window: every block the build takes, most needed first, in rows from the left, scrolled
with the mouse wheel when there are more than fit. Its close clasp takes it away again.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `pattern` | list of layers | required | The build's layers **from the top down**. Each layer is a list of rows, each row a string with one character per block. |
| `mapping` | object | required | Which block each character stands for, as a block state (`"minecraft:lantern[hanging=false]"`) or a block tag (`"#minecraft:logs[axis=y]"`). A space, or any character not mapped, is air. |
| `title` | text | none | A heading, also the build's name in the world. |
| `text` | text | `""` | Text under the window. |
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

A mapping can name a **block tag**, with properties set on each block of it that has them: `"#minecraft:planks"`,
`"#minecraft:logs[axis=y]"`. Any block of the tag will do. The window and the requirement list show its blocks in
turn, a second each, and the build guide in the world takes any of them.

Blocks are counted by the items they take: the top half of a door or a tall flower and the head of a bed come with the
other half and aren't counted again, and a double slab counts as two slabs.

```json title="Tags, a door and a bed"
{
  "type": "multiblock",
  "title": "A Hut",
  "pattern": [
    ["PPP", "PPP", "PPP", "PPP"],
    ["LPL", "P P", "P P", "LUL"],
    ["LPL", "PHP", "PFP", "LDL"],
    ["CCC", "CCC", "CCC", "CCC"]
  ],
  "mapping": {
    "P": "#minecraft:planks",
    "L": "#minecraft:logs[axis=y]",
    "C": "minecraft:cobblestone",
    "D": "minecraft:oak_door[half=lower,facing=north]",
    "U": "minecraft:oak_door[half=upper,facing=north]",
    "F": "minecraft:red_bed[part=foot,facing=north]",
    "H": "minecraft:red_bed[part=head,facing=north]"
  },
  "text": "Any planks and any logs will do; the door and the bed count once each."
}
```

## Building in the world

Pressing a build's eye clasp puts the book away and shows the build in the world as see-through ghost blocks, where
you look and turned the way you face. Right-click to set it down. The layer to build next is the clearest, wrong blocks
show red, and a panel on the HUD tracks progress and what is still needed. It ends when the build is done (a toast
says so), when you walk away, or when you sneak and right-click.

Which corner of the screen the panel sits in, and how far from it, are client settings in Folios's config:
`buildPanel.corner` (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`), `buildPanel.x` and `buildPanel.y`
(both 8 by default).
