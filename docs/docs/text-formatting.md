# Text formatting

Page text, a book's `landing`, category descriptions, titles and labels are written in a small part of Markdown, so
long text stays readable in a JSON file.

In JSON, a line break is written `\n`, and an empty line (`\n\n`) starts a new paragraph:

```json
"text": "First paragraph.\n\nSecond paragraph,\nwith a second line."
```

## Text fields

Every text field takes one of three things:

| Written as | Is |
| --- | --- |
| `"Some **words**"` | Markup, as on this page. |
| `"mymod.guide.tools.text"` | A translation key: when the whole text is a key in the current language, it is translated first, then read as markup. |
| `{"translate": "mymod.guide.mana", "with": ["**12**"], "fallback": "Mana: %s"}` | A translation with its `%s` filled from `with` (each itself markup), and `fallback` shown where the key is missing. Only `translate` is needed. |

A book can keep all its words in lang files:

```json title="entries/basics/first_steps.json"
{ "title": "mymod.guide.first_steps.title", "text": "mymod.guide.first_steps.text" }
```

```json title="assets/mymod/lang/en_us.json"
{
  "mymod.guide.first_steps.title": "First Steps",
  "mymod.guide.first_steps.text": "Welcome!\n\n- **Gather** wood\n- *Craft* a table"
}
```

Any key works for page text. The names of the book, its categories and entries need none at all: left out, they come
from keys named after them, `folios.<namespace>.<book>.name`, `.category.<id>.name` and so on (see
[book.json](book.md#words) and [Categories and entries](categories-and-entries.md)). Folios's demo books and the book
template name their page keys the same way: `folios.<namespace>.<book>.entry.<id>.page<n>.text`. To translate a book,
add `lang/<language>.json` with the same keys.

## Blocks

These work at the start of a line, in page text and the landing page. Titles and labels are one line and keep them as
written.

| Write | Gives |
| --- | --- |
| `# Heading` | A heading, bold, with a short accent mark under it. |
| `## Smaller heading` | A smaller heading. |
| `---` (or `***`) alone on a line | A dividing rule. |
| `- item` or `* item` | A bullet. |
| `1. item` | A numbered item. Numbers count up by themselves within a list. |
| `> text` | A note, set apart with a tinted box and a bar in the accent colour. |
| `->text<-` | The paragraph centred, whatever the page's [alignment](setting-pages.md#alignment). |
| `->text` | The paragraph at the right. |

Indent a bullet or numbered item by two spaces per level to nest it, up to three levels.

```json
{
  "title": "Lists",
  "text": "# Getting started\n\n- Gather wood\n  - Oak or birch\n- Craft a table\n\n1. Mine stone\n1. Smelt iron\n\n---\n\n> Tip: press F3 + T to reload books.\n\n->*Good luck!*<-"
}
```

## Inline marks

| Write | Gives |
| --- | --- |
| `**bold**` | **bold** |
| `*italic*` or `_italic_` | *italic* |
| `__underlined__` | underlined |
| `~~struck~~` | struck through |
| `` `code` `` | `code`, in the page's muted colour |

An underscore inside a word, as in `snake_case`, is kept as it is.

A backslash keeps the next character as it is: `\*` is a plain `*`, `\{` a plain `{`. In JSON the backslash itself must
be escaped, so write `\\*`.

## Tags

Tags in braces put something in the text as it is read:

| Write | Gives |
| --- | --- |
| `{#E06A3A}coloured{/}` | Text in a colour, as `#RRGGBB`. `{/}` ends it. |
| `{key:key.jump}` | The key currently bound to a control, in bold. Use the control's id, such as `key.inventory` or `key.jump`. |
| `{player}` | The reader's name. |
| `{item:minecraft:diamond}` | The item in a small slot, then its name, as a link to its page when the book has one (a [spotlight](elements/spotlight.md) of it, say). <code>{item:minecraft:diamond&#124;16}</code> sets the slot's size, 8 to 18 pixels (14 if left out). |
| `{slot:minecraft:diamond}` | The item in its slot, without its name, to set items side by side or in a grid. Takes a size as `{item:...}` does. |
| `{entry:crafting/tools}` | A link to an entry, with the entry's name as its words. `{entry:crafting/tools#pickaxe}` goes to a marker. |
| `{entity:minecraft:blaze}` | The entity's name, as a link showing it in a [popup](#popups). |
| <code>{flag:mymod:hard_mode&#124;on text&#124;off text}</code> | One text or the other, by a [flag](api/progress.md#flags). Either may be empty. |
| <code>{lang:mymod.guide.tip&#124;first&#124;second}</code> | A translation, read as markup, its `%s` filled from the parts after the key. |

A tag that can't be read is shown as written.

Items' slots are drawn in page text. In titles, quest objectives and other one-line labels, `{item:...}` and
`{slot:...}` are the item's name alone. Hovering a slot shows the item's tooltip, and pressing it opens its page in the
book when there is one. A name too long for its line runs on beside its slot, and a line of slots alone (a grid) has
its slots spread evenly across the page.

```json
{
  "text": "Welcome, {player}! Press {key:key.inventory} to open your inventory, then see {entry:crafting/tools}. Mine some {item:minecraft:diamond}.{flag:mymod:hard_mode| **Hard mode is on.**|}"
}
```

## Links

`[label](target)` makes a link. The label can use inline marks. The target is one of:

| Target | Goes to |
| --- | --- |
| `entry:basics/first_steps` | An entry, by its id. |
| `basics/first_steps` | The same: a bare id is an entry. |
| `entry:crafting/tools#pickaxe` | The page of an entry with that [`marker`](pages.md#keys-every-page-takes). |
| `category:basics` | A category. |
| `item:minecraft:diamond` | The book's page about an item. |
| `entity:minecraft:blaze` | A [popup](#popups) of the entity. |
| `structure:minecraft:igloo/top` | A popup of a structure. |
| `build:tower` | A popup of one of the book's [build files](elements/build-files.md) (or `build:mymod:my_guide/builds/tower`). |
| `multiblock:world/builds#hut` | A popup of the multiblock on that entry's page with the marker (its first multiblock without one). |
| `https://example.com` | A web page. The game asks before opening it, and the link's tooltip shows the address. |

Links take the book's link colour, and are underlined under the pointer. A link to a locked entry or category does
nothing, and its tooltip says "Not yet discovered".

```json
{
  "text": "See [the tools](entry:crafting/tools), the [pickaxe recipe](entry:crafting/tools#pickaxe), the [whole category](category:crafting) or [the wiki](https://example.com/wiki)."
}
```

The page with the marker:

```json title="entries/crafting/tools.json (one of its pages)"
{
  "type": "recipe",
  "marker": "pickaxe",
  "recipe": "minecraft:iron_pickaxe"
}
```

Mods add kinds of link of their own, `[label](scheme:target)`: see [Links and popups](api/links.md).

## Popups

A link to an entity, a structure, a build or a multiblock shows it in a popup window while the pointer is over its
words: the name centred along the top, and under it the thing turning in a window, as its page element shows it.

```json
{
  "title": "Dangers",
  "text": "Keep clear of the {entity:minecraft:blaze}, and of [anything that flies](entity:minecraft:phantom).

The [old shrine](multiblock:world/builds#shrine) is where they gather."
}
```

Pressing the link pins its popup in place: drag it to turn the scene, and use the mouse wheel to zoom it. Press off it,
press Esc or turn the page to put it away. A link to a locked entry's multiblock shows nothing until the entry opens.

