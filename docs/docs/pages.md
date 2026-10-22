# Pages

An entry's `pages` is a list of pages. A page is a `title` and its `elements`: what it shows, one under another. Each
element names its kind with `"type"`; without one it is `text`, and a plain string is a text element too:

```json
"pages": [
  {
    "title": "Torches",
    "elements": [
      "Light the way:",
      { "type": "recipe", "recipe": "minecraft:torch" },
      { "type": "build", "build": "tower", "height": 60 }
    ]
  }
]
```

A page can also be written as a single element, as pages were before 2.0. It is then a page of that one element, so
existing books read as they did:

```json
"pages": [
  "A text page, written as a plain string.",
  { "type": "spotlight", "item": "minecraft:diamond" }
]
```

A type without a namespace is Folios's own: `"text"` is `folios:text`. Mods can [add element types](for-mod-developers.md#page-types).

Pages are laid out for the page size and the reader's language. Text that doesn't fit runs on over the next pages, and
an element that doesn't fit in what is left of a page starts the next one, so one page object can become several pages
in the book.

## Elements

Every type below can stand in a page's `elements`, in any order and as many as you like. Each takes its own `title`
and `text`, shown with it. A page's own `text` comes before its elements.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `title` | text | none | The page's heading, at its top. |
| `text` | text | `""` | Text before the elements. |
| `elements` | list of up to 64 | `[]` | The elements, one under another. A plain string is a text element. |

```json title="Text, a recipe, a build and a structure on one page"
{
  "title": "All Together",
  "elements": [
    "Text, a recipe, a build and a structure, one under another.",
    { "type": "recipe", "recipe": "minecraft:lantern" },
    { "type": "build", "build": "well", "height": 60, "text": "Whatever doesn't fit goes on over the next page." },
    { "type": "structure", "title": "Igloo", "structure": "minecraft:igloo/top", "height": 60 }
  ]
}
```

`spread` and `empty` are pages of their own and can't be elements, and an element can't hold elements of its own.

## Element types

| Type | What it shows |
| --- | --- |
| [`text`](elements/text.md) | Text with an optional title, running on over as many pages as it needs. |
| [`spotlight`](elements/spotlight.md) | One item shown large on a card; pressing it elsewhere in the book comes back here. |
| [`image`](elements/image.md) | Pictures, one at a time, with arrows to step through them. |
| [`recipe`](elements/recipe.md) | One or two recipes, drawn as the game shows them. |
| [`entity`](elements/entity.md) | A creature turning in a window of its own. |
| [`multiblock`](elements/multiblock.md) | A build from a pattern, with its requirement list and the build guide in the world. |
| [`structure`](elements/structure.md) | Structures from data packs, several to a window. |
| [`build`](elements/build.md) | Builds designed in the book's own files, several to a window. |
| [`links`](elements/links.md) | Rows linking to other entries. |
| [`quest`](elements/quest.md) | A checklist of objectives with progress bars. |

Builds shown with `build` come from [build files](elements/build-files.md). Two kinds of page stand on their own: [`spread`](spread-and-empty.md#spread), over both pages of the open book, and [`empty`](spread-and-empty.md#empty), a blank page.

## Keys every page takes

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `type` | string | `"text"` | The kind of page. |
| `anchor` | string | none | A name links can reach this page by: `entry:<entry id>#<anchor>`. See [Links](text-formatting.md#links). |
| `icon` | [icon](categories-and-entries.md#icons) or `"none"` | see below | An icon before the page's title. |
| `advancement`, `flag`, `secret` | | | Leave the page out until it opens. See [Locks](categories-and-entries.md#locks). |

Most pages also take `title` and `text`. Texts can be translation keys and use [text formatting](text-formatting.md).

An entry's first page shows the entry's icon before its title, set over a mark of the book's colour at the start of the
line under it; the title starts where the rest of the line does. Later pages show an icon only when they name one with
`icon`, and `"icon": "none"` leaves it off the first page. A title without an icon has a plain line under it.

Items are written as described under [Icons](categories-and-entries.md#icons): an id, `id*count`, a `#tag`, a list,
or an item stack object.
