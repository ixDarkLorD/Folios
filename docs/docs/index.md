# Folios docs

Folios adds guide books to Minecraft: in-game manuals written as JSON. A book can be shown flat on the screen or held
open in the player's hands in the world, and its pages can hold text, items, pictures, recipes, turning 3D scenes of
entities and builds, maps, quests and live lists.

There are two ways in. Most books need no code at all.

<div class="f-doors" markdown>

<a class="f-door" href="getting-started/">
<span class="f-door__eyebrow">Resource & data packs</span>
<span class="f-door__title">Write a book</span>
<span class="f-door__text">A book is a folder of JSON in a resource pack or your mod's assets: its categories, entries,
pages and quests. Start with your first book, then look up each key as you need it.</span>
<span class="f-door__go">Your first book →</span>
</a>

<a class="f-door" href="api/">
<span class="f-door__eyebrow">Java API</span>
<span class="f-door__title">Build on it from a mod</span>
<span class="f-door__text">Give and open books, unlock content with flags, feed live lists from your mod's content,
and add your own page elements, reveal effects, objectives and styles.</span>
<span class="f-door__go">Overview and setup →</span>
</a>

</div>

!!! tip "Coming from Folios 2.x?"
    3.0 gives book JSON one clear shape. Older books still load and are updated as they are read; see
    [Migrating to 3.0](migrating-to-3.0.md), or update a book's files at once with the
    [book upgrader](../upgrader/index.md).

## How a book is put together

A book is a folder of JSON files:

```text
assets/<namespace>/folios/<book>/
  book.json                       the book itself: style, colours, front page
  categories/<category>.json      one file per category (a tab in the book)
  entries/<category>/<entry>.json one file per entry, in its category's folder
  builds/<build>.json             builds its pages show (optional)
```

- The book's id is `<namespace>:<book>`. For `assets/mymod/folios/my_guide/` that is `mymod:my_guide`.
- A **category** is a group of entries, shown as a ribbon tab. Its id is its file name: `categories/basics.json` is
  `basics`.
- An **entry** is a titled run of pages. Its id is its path under `entries/`: `entries/basics/first_steps.json` is
  `basics/first_steps`, and the folder it is in is its category.
- An entry's **pages** are listed in its JSON. Long text runs on over as many pages as it needs.

Only `book.json` is required for a book to load, and every key in it has a default. Names and descriptions left out of
the JSON come from your language files, so a book can keep all its words there.

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

Books work the same on every version. The few differences are noted on the pages they affect: pick your Minecraft
version at the top of the page, and each page shows what is true for it. Folios needs
[CoolCatLib](https://modrinth.com/mod/coolcatlib), and should be installed on both the client and the server.
