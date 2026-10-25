# Live lists

A page can **observe** a source: a list that a mod or a data pack gives, such as a mod's spells, its machines, or the
rituals a modpack adds. The page writes a line of markup for each row of the list, after its own text, and is written
again whenever the list changes. Content added later, by an addon or a data pack, shows in the book without anyone
editing the book.

```json title="A page listing a mod's spells"
{
  "title": "Spells",
  "text": "Every spell you can learn, the cheapest first:",
  "observe": {
    "source": "mymod:spells",
    "row": "- **{$name}**: {$mana} mana",
    "sort": "mana",
    "empty": "*No spells are known.*"
  }
}
```

Each row of a source has an `id` and named fields. `{$name}` in `row` is the row's `name` field; a field holding a
translation key shows its translation. Which fields a source has is up to whoever gives it: a mod's documentation, or
the data pack file.

## observe

`observe` can be written on a page, or on any element in a page's `elements`. Its lines go after the page's or
element's own text, and run on, [keep or flip](setting-pages.md#overflow) with it.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `source` | identifier | required | The source to list. |
| `of` | string | all | For a [source Folios gives](#sources-from-the-game): which part of it, such as a tag or a mod. |
| `row` | text | `"- {$id}"` | The markup written for each row, its `{$field}`s filled in. |
| `separator` | string | `"\n"` | Put between rows. `"\n\n"` makes each row a paragraph of its own. |
| `header` | text | none | A line before the rows. |
| `footer` | text | none | A line after the rows. |
| `empty` | text | none | Shown in place of the header, rows and footer when no rows are left. |
| `sort` | string | the source's order | A field to sort by: `"name"`, or `"-name"` the other way. Fields that are numbers sort as numbers. |
| `where` | object | every row | Keeps only some rows: `field`, and one of `is`, `not`, `above` or `below` a value. |
| `limit` | integer | all | The most rows shown, after sorting. |

```json title="A spotlight followed by the three most dangerous creatures"
{
  "type": "spotlight",
  "item": "minecraft:iron_sword",
  "title": "Know Your Foes",
  "text": "The most dangerous creatures of these lands:",
  "observe": {
    "source": "mymod:creatures",
    "row": "- {$name} ({$danger})",
    "sort": "-danger",
    "where": { "field": "danger", "above": 0 },
    "limit": 3,
    "footer": "*And many more.*"
  }
}
```

The words of a live list are found by the book's search, as the rest of the page's are.

A source no one gives is empty: the page shows its `empty` text, and the log says once which source it is waiting for.

## Sources from the game

Folios gives four sources itself, worked out from the game each time the page is laid out, so a book or a data pack can
list game content with no code. `of` narrows each one:

| Source | Rows | `of` | Fields |
| --- | --- | --- | --- |
| `folios:items` | Items | a tag (`"#minecraft:logs"`) or a mod (`"create"`) | `name`, `mod`, `durability`, `stack` |
| `folios:blocks` | Blocks | a tag or a mod | `name`, `mod` |
| `folios:entities` | Entity types | a tag (`"#minecraft:skeletons"`) or a mod | `name`, `mod`, `category` (`monster`, `creature`, ...) |
| `folios:advancements` | The advancements the reader has been shown | a mod, or an id the rest are under (`"minecraft:story"`) | `name`, `description`, `mod`, `done` (`true` or `false`) |

Without `of`, a source lists everything it has. `name` and `description` are translation keys, so they show in the
reader's language.

A row of `folios:items` (or of a source whose ids are items) can show its item in a slot with the
[`{item:...}` and `{slot:...}` tags](text-formatting.md#tags): `"row": "- {item:{$id}}"` lists each item in a slot with
its name, and `"row": "{slot:{$id}|16}", "separator": ""` sets them out as a grid of slots.

```json title="Every sapling, as a grid"
{
  "title": "Saplings",
  "observe": { "source": "folios:items", "of": "#minecraft:saplings", "row": "{slot:{$id}|16}", "separator": "" }
}
```

```json title="The reader's story so far, and what is next"
{
  "title": "Your Story",
  "elements": [
    {
      "text": "Done:",
      "observe": { "source": "folios:advancements", "of": "minecraft:story", "row": "- {$name}",
                   "where": { "field": "done", "is": "true" }, "empty": "*Nothing yet.*" }
    },
    {
      "text": "Still to do:",
      "observe": { "source": "folios:advancements", "of": "minecraft:story", "row": "- {$name}",
                   "where": { "field": "done", "is": "false" } }
    }
  ]
}
```

`folios:advancements` is the reader's own: it lists what the game has shown them, as the advancements screen does (so
a few at a time, as they unlock), and it is written again the moment one is done. A hidden advancement is left out
until it is done.

## Sources from data packs

A data pack can add rows to any source, a mod's own or a new one of its own, with
`data/<namespace>/folios/sources/<name>.json`:

```json title="data/mypack/folios/sources/creatures.json"
{
  "source": "mymod:creatures",
  "rows": [
    { "id": "minecraft:zombie", "name": "entity.minecraft.zombie", "danger": 2 },
    { "id": "minecraft:warden", "name": "entity.minecraft.warden", "danger": 10 },
    { "id": "mypack:moon_wolf", "name": "Moon Wolf", "danger": 5 }
  ]
}
```

| Key | Type | What it does |
| --- | --- | --- |
| `source` | identifier | The source the rows go in. |
| `rows` | list | Each an object with an `id` and any fields, as text, numbers or booleans. |

A row whose `id` is already in the source takes its fields over the ones it had, so a pack can change one field of a
mod's row (`{"id": "minecraft:zombie", "danger": 3}`). Rows from several files of the same source are put together in
the order of the files' ids.

### Removing rows

`"remove": true` takes a row out of a source, whoever gave it: a mod's, another pack's, or one of
[Folios's own](#sources-from-the-game).

```json title="data/mypack/folios/sources/trim.json"
{
  "source": "mymod:creatures",
  "rows": [
    { "id": "minecraft:zombie", "remove": true }
  ]
}
```

A later file's row with the same id brings it back.

### Rows that need mods

`"requires_mods"`, a mod's id or a list of them, leaves out a row, or a whole file, unless those mods are loaded:

```json
{
  "source": "mymod:creatures",
  "requires_mods": "create",
  "rows": [
    { "id": "create:golem", "name": "Brass Golem", "danger": 7 },
    { "id": "mypack:clockwork_bat", "name": "Clockwork Bat", "danger": 3, "requires_mods": ["create", "createaddition"] }
  ]
}
```

The server sends every source to each player as they join, and again whenever data packs are reloaded with `/reload`,
so the book follows without a restart. The server needs Folios for data pack sources to reach players.

## Sources from mods

Mods give sources from code, on the client or the server: see [Live lists](api/live-lists.md) in the Java API.
