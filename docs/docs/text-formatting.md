# Text formatting

Page text, a book's `landing`, category descriptions, titles and labels are written in a small part of Markdown, so
long text stays readable in a JSON file.

In JSON, a line break is written `\n`, and an empty line (`\n\n`) starts a new paragraph:

```json
"text": "First paragraph.\n\nSecond paragraph,\nwith a second line."
```

## Translation keys

If a whole text is a translation key in the current language, it is translated first, then formatted. A book can keep
all its words in lang files:

```json title="entries/basics/first_steps.json"
{ "type": "text", "title": "mymod.guide.first_steps.title", "text": "mymod.guide.first_steps.text" }
```

```json title="assets/mymod/lang/en_us.json"
{
  "mymod.guide.first_steps.title": "First Steps",
  "mymod.guide.first_steps.text": "Welcome!\n\n- **Gather** wood\n- *Craft* a table"
}
```

Any key works. The book template and Folios's own demo books name theirs after the book, as `book.json`'s own default
name does: `guide.<namespace>.<book>.name`, `.subtitle`, `.landing`, `.category.<id>.name`, `.entry.<id>.name` and
`.entry.<id>.page<n>.text`, `.title`, `.link<n>`, `.objective<n>`, `.reward` and `.place.<id>` for a map's places. To
translate a book, add `lang/<language>.json` with the same keys.

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

Indent a bullet or numbered item by two spaces per level to nest it, up to three levels.

```json
{
  "type": "text",
  "title": "Lists",
  "text": "# Getting started\n\n- Gather wood\n  - Oak or birch\n- Craft a table\n\n1. Mine stone\n1. Smelt iron\n\n---\n\n> Tip: press F3 + T to reload books."
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
| `{#E06A3A}coloured{/}` | Text in a colour, as `#RRGGBB`. `{/}` ends it. |
| `{key:key.jump}` | The key currently bound to a control, in bold. Use the control's id, such as `key.inventory` or `key.jump`. |

An underscore inside a word, as in `snake_case`, is kept as it is.

A backslash keeps the next character as it is: `\*` is a plain `*`. In JSON the backslash itself must be escaped, so
write `\\*`.

```json
{
  "type": "text",
  "text": "Press {key:key.inventory} to open your inventory.\n\n**Bold**, *italic*, __underlined__, ~~struck~~, `code` and {#E06A3A}coloured words{/}."
}
```

## Links

`[label](target)` makes a link. The label can use inline marks. The target is one of:

| Target | Goes to |
| --- | --- |
| `entry:basics/first_steps` | An entry, by its id. |
| `basics/first_steps` | The same: a bare id is an entry. |
| `entry:crafting/tools#pickaxe` | The page of an entry with that [`anchor`](pages.md#keys-every-page-takes). |
| `category:basics` | A category. |
| `https://example.com` | A web page. The game asks before opening it, and the link's tooltip shows the address. |

Links take the book's link colour, and are underlined under the pointer. A link to a locked entry or category does
nothing, and its tooltip says "Not yet discovered".

```json
{
  "type": "text",
  "text": "See [the tools](entry:crafting/tools), the [pickaxe recipe](entry:crafting/tools#pickaxe), the [whole category](category:crafting) or [the wiki](https://example.com/wiki)."
}
```

The page with the anchor:

```json title="entries/crafting/tools.json (one of its pages)"
{
  "type": "recipe",
  "anchor": "pickaxe",
  "recipe": "minecraft:iron_pickaxe"
}
```
