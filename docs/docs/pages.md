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

A page can also be written as a single element. It is then a page of that one element:

```json
"pages": [
  "A page of text, written as a plain string.",
  { "title": "Diamonds", "text": "A page of text, written as an object." },
  { "type": "spotlight", "item": "minecraft:diamond" }
]
```

A type without a namespace is Folios's own: `"text"` is `folios:text`. Mods can [add element types](api/page-elements.md).

Pages are laid out for the page size and the reader's language. Text that doesn't fit runs on over the next pages, and
an element that doesn't fit in what is left of a page starts the next one, so one page object can become several pages
in the book. [Setting pages](setting-pages.md) changes that: an element can keep to its box, or page through its text
in place.

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
| `marker` | string | none | A name links can reach this page by: `entry:<entry id>#<marker>`. See [Links](text-formatting.md#links). |
| `icon` | [icon](categories-and-entries.md#icons) or `"none"` | see below | An icon before the page's title. |
| `requires`, `secret` | | | Leave the page out until it opens. See [Locks](categories-and-entries.md#locks). |
| `overflow`, `alignment`, `title_alignment`, `anchor` | | the book's | How the page and its elements are set. See [Setting pages](setting-pages.md). |
| `observe` | object | none | A live list, written after the page's text. See [Live lists](live-lists.md). |

Every element in a page's `elements` takes `overflow`, `alignment`, `title_alignment`, `anchor` and `observe` too.

Most pages also take `title` and `text`. Texts are [Folios Markdown](text-formatting.md), translation keys, or
`{"translate": ...}`.

An entry's first page shows the entry's icon before its title, set over a mark of the book's colour at the start of the
line under it; the title starts where the rest of the line does. Later pages show an icon only when they name one with
`icon`, and `"icon": "none"` leaves it off the first page. A title without an icon has a plain line under it.

Items are written as described under [Icons](categories-and-entries.md#icons): an id, `id*count`, a `#tag`, a list,
or an item stack object.

## Mentioning a page in chat

With a book open, point at a page and press **M** (Mention a page in chat, under Multiplayer in the controls). The chat
opens with a mention of the page written in, `[[mymod:handbook|basics/first_steps|1]]`, to add a question to and
send.

Every player with Folios sees the mention as the page's name, `[Handbook › First Steps, p. 3]`, in the book's colour;
pressing it opens their book there. A player who doesn't have the book, or hasn't unlocked the entry or page yet, sees
it scrambled instead, so a mention gives nothing away. A mention names a page of the entry's JSON, so it opens at the
same place whatever language or GUI scale the other player reads in; a text that runs over several printed pages opens
at the first of them. From a book's front spread, the mention is of the book itself.

