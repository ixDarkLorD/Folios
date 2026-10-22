# Overview

Folios adds guide books to Minecraft: in-game manuals written as JSON. A book can be shown flat on the screen or held
open in the player's hands in the world, and its pages can hold text, items, pictures, recipes, turning 3D scenes of
entities and builds, maps and quests.

Books are client resources. They live in a **resource pack** or in a mod's own `assets` folder, not in a data pack.
Resource packs can add, translate and replace them like any other asset.

## How a book is put together

A book is a folder of JSON files:

```text
assets/<namespace>/guides/<book>/
  book.json                       the book itself: name, style, colours, front page
  categories/<category>.json      one file per category (a tab in the book)
  entries/<category>/<entry>.json one file per entry, in its category's folder
```

- The book's id is `<namespace>:<book>`. For `assets/mymod/guides/my_guide/` that is `mymod:my_guide`.
- A **category** is a group of entries, shown as a ribbon tab. Its id is its file name: `categories/basics.json` is
  `basics`.
- An **entry** is a titled run of pages. Its id is its path under `entries/`: `entries/basics/first_steps.json` is
  `basics/first_steps`, and the folder name is its category.
- An entry's **pages** are listed in its JSON. Long text runs on over as many pages as it needs.

Only `book.json` is required for a book to load, and every key in it has a default.

Every piece of text (names, descriptions, page text) can be a translation key, so a book's words can live in your
language files.

## Reloading while you write

Press ++f3+t++ in game to reload resource packs. Folios reads every book again, so you can edit the JSON and see the
result without restarting. A page that can't be read is shown in the book as an error message, in place of the page,
and the log says what went wrong.

## Supported versions

| Minecraft | Loaders |
| --- | --- |
| 26.1 – 26.3 | Fabric, NeoForge |
| 1.21 – 1.21.1 | Fabric, NeoForge, Forge |
| 1.20.1 | Fabric, Forge |

Books work the same on every version. The few differences are noted on the pages they affect. Folios needs
[CoolCatLib](https://modrinth.com/mod/coolcatlib), and should be installed on both the client and the server.

## Where to go next

- [Your first book](getting-started.md): make a book, a category and an entry, and open it in game.
- [book.json](book.md): every key of the book file.
- [Categories and entries](categories-and-entries.md): their keys, ordering, icons and locks.
- [Pages](pages.md): every built-in page type and its fields.
- [Text formatting](text-formatting.md): the Markdown used in page text.
- [Styles and look](styles-and-look.md): screen and handheld books, colours, textures, layout, models and reveal
  effects.
- [Quests](quests.md): quest pages, objectives, locks, mastery and celebrations.
- [For mod developers](for-mod-developers.md): the Java API for opening books, book items, flags and adding your own
  page types, reveal effects, objectives and styles.
