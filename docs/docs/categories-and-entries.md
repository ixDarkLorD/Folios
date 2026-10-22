# Categories and entries

A book's content is split into **categories** (the tabs) and **entries** (titled runs of pages inside a category).

## Categories

Each category is a file in the book's `categories/` folder. Its id is its file name: `categories/basics.json` is the
category `basics`.

```json title="categories/advanced.json"
{
  "name": "Advanced",
  "description": "Opens once you have smelted iron.",
  "icon": "minecraft:iron_ingot",
  "sort": 1,
  "color": "#3A6EA5",
  "advancement": "minecraft:story/smelt_iron"
}
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `name` | text | the category's id | Its name, on its tab and at the head of its list. |
| `description` | text | none | Marked-up text shown above its entries. |
| `icon` | [icon](#icons) | none | Shown on its tab. |
| `sort` | integer | `0` | Lower comes first. |
| `color` | colour | the book's | The colour of its ribbon tab. |
| `advancement`, `flag`, `secret` | | | [Locks](#locks). |

## Entries

Each entry is a file under the book's `entries/` folder. Its id is its path there, without `.json`:
`entries/basics/first_steps.json` is the entry `basics/first_steps`. The first folder is its category.

```json title="entries/getting_started/first_steps.json"
{
  "name": "First Steps",
  "icon": "minecraft:wooden_pickaxe",
  "featured": true,
  "sort": 0,
  "pages": [
    {
      "type": "text",
      "title": "First Steps",
      "text": "Text runs on over as many pages as it needs."
    },
    {
      "type": "spotlight",
      "item": "minecraft:diamond",
      "text": "One item shown large."
    }
  ]
}
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `name` | text | the entry's id | Its name, as shown in lists and links. |
| `icon` | [icon](#icons) | none | Shown beside its name in lists. |
| `sort` | integer | `0` | Lower comes first. |
| `featured` | boolean | `false` | Shown first in its category, and marked. |
| `category` | string | its first folder | The category it belongs to, when you don't want to use the folder. |
| `pages` | list | none | Its [pages](pages.md). A plain string in the list is a text page. |
| `text` | text | none | A shortcut for an entry of plain text. It becomes the first page, before any `pages`. |
| `reveal` | string or object | the book's | Its own [reveal effect](styles-and-look.md#reveal-effects), written as in [book.json](book.md#reveal). |
| `advancement`, `flag`, `secret` | | | [Locks](#locks). |

An entry whose category doesn't exist in the book is skipped, with a warning in the log.

A text-only entry can be as short as this:

```json title="entries/basics/about.json"
{
  "name": "About",
  "text": "# About\nThis mod adds **shiny things**."
}
```

## Order

- **Categories** are ordered by `sort`, then by name.
- **Entries** in a category show featured entries first, then by `sort`, then by name.
- **Pages** are shown in the order of the `pages` list.

The book also has an index of every entry from A to Z, and a bookmarks list, at the foot of its tabs.

## Icons

Books, categories, entries and map places take an icon. It is one of:

| Written as | Shows |
| --- | --- |
| `"minecraft:compass"` | An item. |
| `"minecraft:diamond*4"` | An item with a count. |
| `"#minecraft:logs"` | Every item of a tag, in turn. |
| `["minecraft:iron_ingot", "minecraft:gold_ingot"]` | Several items, in turn. |
| `{"id": "minecraft:potion", "count": 1, "components": {...}}` | An item stack. |
| `"mymod:textures/gui/guide/compass.png"` | A 16 × 16 texture: any path ending in `.png`. |
| `{"texture": "mymod:textures/gui/guide/compass.png"}` | The same texture, written as an object. |
| `{"item": "minecraft:compass"}` | An item, written as an object. |

Items shown in turn change every 1.25 seconds. An item that doesn't exist shows as a barrier, so the mistake is easy to
spot.

!!! note "1.20.1"
    On 1.20.1 an item stack object is `{"id": ..., "count": ..., "nbt": ...}`, where `"nbt"` is SNBT text or a JSON
    object.

## Locks

Categories, entries and single pages can be locked until the player has earned them. Locks are keys of the thing
locked:

| Key | Type | What it does |
| --- | --- | --- |
| `advancement` | identifier | Locked until the player has this advancement. |
| `flag` | string | Locked until this flag, set by a mod with [`Guides.setFlag`](for-mod-developers.md#flags), is on. Start it with `!` to reverse it: open while the flag is off. |
| `secret` | boolean | Hide it entirely until it opens. Without it, a locked category or entry is still listed, as `???`. |

When both `advancement` and `flag` are given, both must be met.

```json title="entries/secrets/the_end.json"
{
  "name": "The End",
  "icon": "minecraft:dragon_egg",
  "secret": true,
  "flag": "mymod:secrets",
  "pages": [
    { "type": "text", "title": "Secret", "text": "This entry stays hidden until the flag is on." }
  ]
}
```

What a lock does:

- A locked **category** or **entry** shows as `???` with no icon and can't be opened. With `secret`, it isn't shown at
  all.
- A locked **page** is left out of its entry until it opens.
- A link to a locked entry or category does nothing, and its tooltip says "Not yet discovered".

The server tells the client which advancements the player has. On a server without Folios, the advancements the client
has been shown are used instead.

```json title="A page locked behind an advancement"
{
  "type": "text",
  "advancement": "minecraft:story/mine_diamond",
  "text": "You found diamonds, so this page appeared."
}
```

## Reading progress

Folios keeps, for each book: which entries have been read, which spreads have been shown, bookmarks, and where the
reader left off. Unread entries are marked with a blinking sparkle, and categories show how much of them has been
read (see `show_progress` in [book.json](book.md)).

It is kept on the server, so it follows the player to any computer, and each world or server has its own:

- **With each player** (the default, `"progress": "player"`): theirs in any copy of the book, kept when they die.
- **On each copy of the book** (`"progress": "book"` in [book.json](book.md)): like a notebook, a copy carries its own
  bookmarks, read pages and place, so whoever reads it finds them, and a new copy starts fresh. Entries read in it
  still count for the player's quests and celebrations. Opened without a copy in hand (by a command or from code),
  the book uses the player's.
