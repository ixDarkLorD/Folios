# Reading

What players can do with books besides turning their pages, and the settings for it. Client settings are in
`config/folios-client.toml`, under `reading`.

## The Compendium

The **Compendium** holds every guide book installed. Using it opens a list of the books, each with its icon and
subtitle, and a search box; choosing one turns the Compendium in your hand into that book. To pick another, sneak
and use the book: a book chosen from a Compendium turns back into one (its tooltip says so). Guide books found or
crafted any other way don't.

Craft it from a **book** and a **bookshelf** (shapeless), or give it with
`/give @s folios:guide_book[folios:guide_book="folios:library"]`. On a lectern, the Compendium lets whoever uses it
choose a book to read, and stays a Compendium.

A book can keep off the Compendium's shelf with `"listed": false` in its [book.json](book.md#reading-keys), for a book
meant to be found, not browsed.

## On a lectern

Any guide book can be put on a lectern, as a written book can. Using the lectern opens the book for whoever uses it,
in the book's own style, and reading it there counts towards quests as reading a carried book does. Sneak and use the
lectern to take the book back. The lectern's comparator signal and its dropping the book when broken work as for a
written book.

## Looking things up

Hovering an item in an inventory and holding the look-up key (**B** by default, under Controls › Inventory) opens the
item's page. Books the player carries are searched first, then every installed book, so a lookup works without the
book at hand. It works over the item lists of JEI, REI and EMI too.

| Setting | Default | What it does |
| --- | --- | --- |
| `lookupAnyBook` | `true` | Look up in every installed book, not only carried ones. |

A book can ask to be looked up only when carried with `"lookup": "carried"` in its [book.json](book.md#reading-keys).

## Recipes

Books draw their recipes themselves, in the book's look, whether or not a recipe viewer is installed. With **JEI**
installed:

- Hovering an item in a recipe and pressing JEI's show-recipe key (**R** by default) or show-uses key (**U**) opens it
  in JEI, as over an item in an inventory.
- A small button on each recipe's frame adds the recipe to JEI's bookmarks, or takes it off again. Where the
  installed JEI can't bookmark whole recipes, it bookmarks what the recipe makes, or isn't shown.

| Setting | Default | What it does |
| --- | --- | --- |
| `bookmarkButton` | `true` | Show the JEI bookmark button on recipes. |
| `bookmarkCorner` | `BOTTOM_RIGHT` | Which corner of a recipe's frame the button sits in: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT` or `BOTTOM_RIGHT`. |

## Text size

| Setting | Default | What it does |
| --- | --- | --- |
| `textScale` | `1.0` | How much larger to draw what is on the pages, 1.0 to 2.0. The book stays about the same size, so each page holds less and the book has more pages. A book never grows past what fits on the screen. |

It applies to screen books and books held in the hands, the next time a book is opened.

## Read aloud

With the game's narrator on (Accessibility settings, or Ctrl + B), a book reads its page aloud as it opens and each
time a page is turned: the entry's name, then the words on the two pages in view.

| Setting | Default | What it does |
| --- | --- | --- |
| `narratePages` | `true` | Read pages aloud while the narrator is on. |

## Exporting a book as a website

`/folios export <book>` turns a book into a small website, for a mod's wiki or its project page. See
[Exporting a book](export.md).
