# Structure

`"type": "structure"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

Structures from data packs, turning in a window: the files structure blocks save and villages are built from,
<span class="mc-v mc-all"><code>data/&lt;namespace&gt;/structure/&lt;path&gt;.nbt</code> (on 1.20.1, <code>data/&lt;namespace&gt;/structures/&lt;path&gt;.nbt</code>)</span><span class="mc-v mc-only mc-26 mc-121"><code>data/&lt;namespace&gt;/structure/&lt;path&gt;.nbt</code></span><span class="mc-v mc-only mc-120"><code>data/&lt;namespace&gt;/structures/&lt;path&gt;.nbt</code></span>. Clients don't have data
packs, so the element asks the server for a structure's blocks the first time it is shown; until then it says it is
loading. Air, structure voids and structure blocks are left out, and a jigsaw shows the block it leaves behind.
Structures of more than 32,768 blocks are too large to show.

Structures are for looking at: they have no requirement list and no eye clasp.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `structure` | structure, or list of 1–32 | required | One structure, or several picked between under the window. |
| `selector` | string | `"arrows"` | How several are picked: `"arrows"`, clasps back and on with the name and "2/3" between them, or `"numbers"`, a numbered clasp each (arrows when there are too many to fit). |
| `title` | text | none | A heading. |
| `text` | text | `""` | Text under the window. |
| `height` | integer | `78` | The window's height in page pixels. 40 to 130. |

Each structure is its id, or `{"id": ..., "label": ...}` to show it by a name.

```json
{
  "type": "structure",
  "title": "Structures",
  "selector": "numbers",
  "structure": [
    { "id": "minecraft:igloo/top", "label": "Igloo" },
    { "id": "minecraft:village/plains/houses/plains_small_house_1", "label": "Plains House" },
    "minecraft:shipwreck/rightsideup_full"
  ]
}
```

!!! note
    Structures come from the server, so it needs Folios too. On a server without it, a structure says it can't be
    found.
