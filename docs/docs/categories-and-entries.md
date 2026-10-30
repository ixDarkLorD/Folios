# Categories and entries

A book's content is split into **categories** (the tabs) and **entries** (titled runs of pages inside a category).

## Categories

Each category is a file in the book's `categories/` folder. Its id is its file name: `categories/advanced.json` is the
category `advanced`.

```json title="categories/advanced.json"
{
  "icon": "minecraft:iron_ingot",
  "sort": 1,
  "color": "#3A6EA5",
  "requires": { "advancement": "minecraft:story/smelt_iron" }
}
```

```json title="lang/en_us.json"
{
  "folios.mymod.my_guide.category.advanced.name": "Advanced",
  "folios.mymod.my_guide.category.advanced.description": "Opens once you have smelted iron."
}
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `name` | text | `folios.<namespace>.<book>.category.<id>.name`, else its id | Its name, on its tab and at the head of its list. |
| `description` | text | `folios.<namespace>.<book>.category.<id>.description`, else none | Marked-up text shown above its entries. |
| `icon` | [icon](#icons) | none | Shown on its tab. |
| `sort` | integer | `0` | Lower comes first. |
| `color` | colour | the book's | The colour of its ribbon tab. |
| `requires`, `secret` | | | [Locks](#locks). |

## Entries

Each entry is a file under the book's `entries/` folder, in its category's folder. Its id is its path there, without
`.json`: `entries/basics/first_steps.json` is the entry `basics/first_steps`, in the category `basics`.

```json title="entries/getting_started/first_steps.json"
{
  "icon": "minecraft:wooden_pickaxe",
  "featured": true,
  "pages": [
    {
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
| `name` | text | `folios.<namespace>.<book>.entry.<category>.<id>.name`, else its id | Its name, as shown in lists and links. |
| `icon` | [icon](#icons) | none | Shown beside its name in lists. |
| `sort` | integer | `0` | Lower comes first. |
| `featured` | boolean | `false` | Shown first in its category, and marked. |
| `pages` | list | none | Its [pages](pages.md). A plain string in the list is a page of text. |
| `reveal` | string or object | the book's | Its own [reveal effect](styles-and-look.md#reveal-effects), written as in [book.json](book.md#reveal). |
| `index` | `"elements"` | none | List each of its elements that shows an item or has a title in the book's index and search, as if each had `"index": true`. See [Listed on their own](pages.md#listed-on-their-own). |
| `requires`, `secret` | | | [Locks](#locks). |

An entry's name key has its id with dots for slashes: `entries/getting_started/first_steps.json` is
`folios.mymod.my_guide.entry.getting_started.first_steps.name`. An entry whose folder isn't one of the book's
categories is skipped, with a warning in the log.

A text-only entry can be as short as this:

```json title="entries/basics/about.json"
{
  "pages": ["# About\nThis mod adds **shiny things**."]
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

!!! note mc-120 "1.20.1"
    On 1.20.1 an item stack object is `{"id": ..., "count": ..., "nbt": ...}`, where `"nbt"` is SNBT text or a JSON
    object.

## Locks

Categories, entries, pages and a page's elements can be locked until the player has earned them:

| Key | Type | What it does |
| --- | --- | --- |
| `requires` | condition, or list of them | Locked until it holds, or until every one in the list holds. A condition is written as a quest objective is: see [Conditions](quests.md#conditions). |
| `secret` | boolean | Hide it entirely until it opens. Without it, a locked category or entry is still listed, as `???`, and a locked page or element is sealed where it stands (see below). |
| `show_unlock` | boolean | Pages and elements: `false` leaves out the *How to unlock* list. `true` by default. |
| `peek` | number | Pages and elements: how plainly the page shows faintly behind its seal, as a float: `1.0` by default, `0.5` fainter, `2.0` plainer, `0.0` not at all. |

```json title="entries/secrets/the_end.json"
{
  "icon": "minecraft:dragon_egg",
  "secret": true,
  "requires": [
    { "flag": "mymod:secrets" },
    { "obtained": "minecraft:ender_eye" }
  ],
  "pages": ["This entry stays hidden until the flag is on and an eye of ender has been found."]
}
```

What a lock does:

- A locked **category** or **entry** shows as `???` with no icon and can't be opened. With `secret`, it isn't shown at
  all.
- A locked **page** or **element** stays where it stands, sealed: a wax seal with a padlock over *Not yet discovered*,
  and *How to unlock*, each condition that opens it ticked once done, with a bar for one that counts. A long list
  scrolls with the mouse wheel or its scrollbar. Behind the seal, unless `peek` is `0.0`, the page itself shows
  faintly: its words in runes, its items, blocks and entities as dark shapes. Nothing on it can be pressed until it
  opens. With `secret`, it is left out until then.
- A link to a locked entry or category does nothing, and its tooltip says "Not yet discovered".

```json title="A page that opens once a quest is done"
{
  "requires": { "quest": "basics/first_quest" },
  "title": "Well Done",
  "text": "This page appeared once the first quest was finished."
}
```

The server tells the client which advancements the player has. On a server without Folios, the advancements the client
has been shown are used instead.

## Reading progress

Folios keeps, for each book: which entries have been read, which spreads have been shown, bookmarks, where the reader
left off, and the items they have [obtained](quests.md#conditions). Unread entries are marked with a blinking sparkle,
and categories show how much of them has been read (see `show_progress` in [book.json](book.md)).

It is kept on the server, so it follows the player to any computer, and each world or server has its own. Where it is
kept is the book's `progress_scope`:

- **`"player"`** (the default): with each player, theirs in any copy of the book, kept when they die.
- **`"copy"`**: on each copy of the book, like a notebook. A copy carries its own bookmarks, read pages and place, so
  whoever reads it finds them, and a new copy starts fresh. Entries read in it still count for the player's quests and
  celebrations. Opened without a copy in hand (by a command or from code), the book uses the player's.
