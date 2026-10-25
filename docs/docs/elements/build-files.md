# Build files

A [build](build.md) element shows builds designed in the book's own files,
`assets/<namespace>/folios/<book>/builds/<name>.json`.

A build file gives the build a `name` and its blocks: from a `pattern` and `mapping` as a [multiblock](multiblock.md)'s,
from `blocks`, or both, the pattern first and then each entry of `blocks` in order, over what came before. Wherever its
blocks are placed, the build is moved so its lowest corner is at 0.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `name` | text | none | The name it is shown by, unless the page gives it a `label`. |
| `pattern`, `mapping` | | none | As on a multiblock. Mappings take tags too. |
| `blocks` | list | `[]` | Blocks placed one entry at a time. |

Each entry of `blocks` takes:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `block` | block state or `#tag` | required | The block. `minecraft:air` clears what it covers. |
| `pos` | `[x, y, z]` | | One block. |
| `from`, `to` | `[x, y, z]` | | A box of blocks, corner to corner. Up to 65,536 blocks. |
| `hollow` | boolean | `false` | Only the box's outside. |

```json title="builds/watchtower.json"
{
  "name": "Watchtower",
  "blocks": [
    { "block": "minecraft:cobblestone", "from": [0, 0, 0], "to": [4, 0, 4] },
    { "block": "#minecraft:logs[axis=y]", "from": [0, 1, 0], "to": [0, 5, 0] },
    { "block": "#minecraft:logs[axis=y]", "from": [4, 1, 0], "to": [4, 5, 0] },
    { "block": "#minecraft:logs[axis=y]", "from": [0, 1, 4], "to": [0, 5, 4] },
    { "block": "#minecraft:logs[axis=y]", "from": [4, 1, 4], "to": [4, 5, 4] },
    { "block": "#minecraft:planks", "from": [0, 6, 0], "to": [4, 6, 4] },
    { "block": "minecraft:air", "pos": [2, 6, 2] },
    { "block": "minecraft:lantern", "pos": [0, 7, 0] }
  ]
}
```
