# Migrating to 3.0

Folios 3.0 gives book JSON one clear shape: each setting has one key and one way to write it, related settings are
grouped, and lists and locks are written the same way everywhere. Books written for 2.x **still load**: Folios updates
them as it reads them, so nothing breaks the day you update the mod. Updating the files themselves is still worth it,
since new features are only documented in their 3.0 shape.

## Format versions

`book.json` says which version of the format the book is written in:

```json title="book.json"
{
  "format_version": "3.0"
}
```

| The book says | Folios reads it |
| --- | --- |
| `"format_version": "3.0"` | As it is. A 2.x shape left in it isn't read: it is logged as the book loads, by file and key, so you can fix it. |
| Nothing | As a 2.x book: every change below is made as it loads, and the log says the book should be updated. |
| A newer version than this Folios knows | As well as it can, with a warning to update Folios. |

A format version is named after the Folios release that introduced it: Folios 3.0 introduced format `"3.0"`, and
books before it are format `"2.0"`.

## Updating a book

Three ways, from least to most work:

1. **The [book upgrader](../upgrader/index.md)** on this site. Pick your book's folder (or a resource pack, or a .zip);
   it shows what will change in each file, then gives you the updated book as a .zip. It runs in your browser and
   nothing is uploaded. It makes the same changes Folios makes as it loads a book, from the same list.
2. **The log**. Load the old book in game and read what Folios logged: each change it made, by file.
3. **By hand**, with the tables below.

After updating, add `"format_version": "3.0"` to book.json (the upgrader does this for you).

## What changed in book.json

| 2.x | 3.0 |
| --- | --- |
| `"cover": "#5A3A22"` | `"cover": {"color": "#5A3A22"}` |
| `"cover_text": {...}` | `"cover": {"text": {...}}` |
| `"cover_texture": "..."` | `"cover": {"texture": "..."}` |
| `"models": {"cover": ...}` | `"cover": {"model": ...}` |
| `"textures": {"cover": ...}` | `"textures": {"boards": ...}` |
| `"tilt"`, `"distance"`, `"page_turn"` | Inside `"handheld"`, beside `look_around` and `walk`. |
| `"progress": "book"` | `"progress_scope": "copy"` (and `"player"` stays `"player"`) |
| `"item_model": "mymod:handbook"` | `"models": {"item": "mymod:handbook"}` |
| `"colors": {"tooltip": {...}}` | `"panel": {...}` |
| `"author": "The Pack Team"` | `"credits": {"authors": "The Pack Team"}` (with an optional `"prefix"`, "Written by" by default) |
| Colours as `"RRGGBB"` or a number | `"#RRGGBB"` or `"#AARRGGBB"` only |
| `"name": "guide.mymod.my_guide.name"` | Leave it out: the key `folios.mymod.my_guide.name` is used by itself. |

The book's `name`, `subtitle`, `summary` and `landing`, left out, come from `folios.<namespace>.<book>.<key>`, and its
`credits` from `folios.<namespace>.<book>.credits.authors` and `.credits.prefix`.
A 2.x book without a `name` used the key `guide.<namespace>.<book>.name`; the upgrade writes that key in as its
`"name"`, so its lang file keeps working. To move to the new default, rename the key in your lang files to
`folios.<namespace>.<book>.name` and remove `"name"`.

## What changed in categories and entries

| 2.x | 3.0 |
| --- | --- |
| `"advancement": "minecraft:story/smelt_iron"` | `"requires": {"advancement": "minecraft:story/smelt_iron"}` |
| `"flag": "mymod:secrets"` | `"requires": {"flag": "mymod:secrets"}` |
| Both of them | `"requires": [{"advancement": ...}, {"flag": ...}]` |
| An entry's `"category": "basics"` | The entry's file in `entries/basics/`. The upgrader moves it. |
| An entry's `"text": "..."` | A first page: `"pages": ["...", ...]` |

`"secret"` is unchanged. A category's and an entry's `name` (and a category's `description`), left out, come from
`folios.<namespace>.<book>.category.<id>.name` and `folios.<namespace>.<book>.entry.<category>.<id>.name`.

Locks are on [Quests, locks and progress](quests.md#locks). They take any condition a quest takes, so a page can now
open on an item found, a quest done or a category read.

## What changed in pages and elements

| 2.x | 3.0 |
| --- | --- |
| A page's `"anchor": "pickaxe"` (where links go) | `"marker": "pickaxe"` |
| A page's `"advancement"` / `"flag"` | `"requires"`, as above |
| `"recipes": ["a", "b"]` | `"recipe": ["a", "b"]` (`"recipe": "a"` for one) |
| `"images": [...]` | `"image": [...]` |
| `"structures": [...]` | `"structure": [...]` |
| `"builds": [...]` | `"build": [...]` |
| A structure or build choice's `"name"` | `"label"` |
| A map node's `"name"` | `"label"` |
| A spread's `"build": {"pattern": ..., "mapping": ...}` | `"multiblock": {"pattern": ..., "mapping": ...}`; `"build"` now names a [build file](elements/build-files.md) |

`"anchor"` on a page now means where its content sits down the page (`"top"`, `"center"`, `"bottom"`): see
[Setting pages](setting-pages.md). A 2.x anchor that isn't one of those words is logged, so a link target left under the
old key is easy to find. The upgrader leaves it to you: only you know whether it was meant as a marker.

## What changed in quests

| 2.x | 3.0 |
| --- | --- |
| `{"category": "basics"}` | `{"read_category": "basics"}` |
| `{"book": true}` | `{"read_book": true}` |
| `{"item": "minecraft:diamond", "count": 5}` | `{"carried": "minecraft:diamond", "goal": 5}` |
| `"count"` on any objective | `"goal"` |

New in 3.0: `obtained`, `quest`, `any`, `"hidden"` and a quest's `"in_order"`. See
[Quests, locks and progress](quests.md).

## Text

Nothing in 2.x text needs to change. 3.0 adds `{"translate": ...}` objects for any text field, and the `{lang:...}`,
`{player}`, `{item:...}`, `{entry:...}` and `{flag:...}` tags, on [Text formatting](text-formatting.md).

## Mods

For mods using the Java API, every class was renamed from `Guide…` to `Folios…`; see
[Coming from 2.x](api/index.md#coming-from-2x).
