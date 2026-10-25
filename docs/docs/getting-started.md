# Your first book

This page walks through making a small book with one category and one entry, then opening it in game.

## Start from the template

The quickest start is the template resource pack, `folios-book-template`, which you can get from the
[Downloads](../downloads.md) section. It holds one complete example book with every kind of page, and a README of the
keys.

1. Copy the template folder into `.minecraft/resourcepacks/` and turn it on in **Options → Resource Packs**.
2. Rename `assets/mymod` to your namespace and `folios/my_guide` to your book's id, and the keys in
   `lang/en_us.json` to match.

The rest of this page builds the same kind of book by hand, so you can see what each file does.

## 1. The book

Create `assets/mymod/folios/my_guide/book.json`:

```json title="book.json"
{
  "format_version": "3.0",
  "style": "handheld",
  "icon": "minecraft:writable_book",
  "accent": "#C0602A",
  "cover": { "color": "#5A3A22" }
}
```

- `format_version` says the book is written for Folios 3.0.
- `style` is `"screen"` (a flat book on the screen, the default) or `"handheld"` (held open in your hands).
- `accent` colours marks and links; `cover` › `color` colours the cover.

The book's name, subtitle and front page text come from the language file, in step 4. See [book.json](book.md) for
every key.

## 2. A category

Create `assets/mymod/folios/my_guide/categories/getting_started.json`:

```json title="categories/getting_started.json"
{
  "icon": "minecraft:oak_sapling"
}
```

The file name, `getting_started`, is the category's id.

## 3. An entry

Create `assets/mymod/folios/my_guide/entries/getting_started/first_steps.json`:

```json title="entries/getting_started/first_steps.json"
{
  "icon": "minecraft:wooden_pickaxe",
  "pages": [
    {
      "title": "First Steps",
      "text": "Text runs on over as many pages as it needs.\n\n- **bold**, *italic*, ~~struck~~\n- {#E06A3A}coloured words{/} and `code`\n\nPress {key:key.inventory} to open your inventory."
    },
    {
      "type": "spotlight",
      "item": "minecraft:diamond",
      "title": "Diamonds",
      "text": "One item shown large."
    }
  ]
}
```

The entry is in the `getting_started` folder, so it belongs to that category. Its id is
`getting_started/first_steps`, which is what the book's landing text links to.

## 4. The words

Create `assets/mymod/lang/en_us.json`. The book's, the category's and the entry's names are read from keys named after
them, so nothing in the JSON above needed to name them:

```json title="lang/en_us.json"
{
  "folios.mymod.my_guide.name": "My Guide",
  "folios.mymod.my_guide.subtitle": "Everything about my mod",
  "folios.mymod.my_guide.landing": "Welcome to **My Guide**.\n\nPick a category from the tabs, or [start here](entry:getting_started/first_steps).",
  "folios.mymod.my_guide.category.getting_started.name": "Getting Started",
  "folios.mymod.my_guide.category.getting_started.description": "The basics of my mod.",
  "folios.mymod.my_guide.entry.getting_started.first_steps.name": "First Steps"
}
```

Any of them can be written in the JSON instead (`"name": "My Guide"` in book.json, say). To translate the book, add
`lang/<language>.json` with the same keys.

Your folders now look like this:

```text
assets/mymod/
  folios/my_guide/
    book.json
    categories/getting_started.json
    entries/getting_started/first_steps.json
  lang/en_us.json
```

## 5. Get the book in game

Folios has one item, `folios:guide_book`, that can open any book. Which book it opens is stored on the stack:

=== "1.20.5 and later"

    ```text
    /give @s folios:guide_book[folios:guide_book="mymod:my_guide"]
    ```

=== "1.20.1"

    ```text
    /give @s folios:guide_book{folios_book:"mymod:my_guide"}
    ```

The item takes the book's name. Use it (right-click) to open the book.

You can also open a book without the item, with the client command `/folios guide mymod:my_guide`.

## 6. Reload and open it

Press ++f3+t++ to reload resources whenever you change a file, then open the book again. If a page has a mistake, the
book shows an error in its place and the game log says what is wrong.

## Next steps

- Add more [elements](pages.md#element-types) to your pages: recipes, images, entities, builds, structures, links and
  quests, and [spreads](spread-and-empty.md).
- Learn the [text formatting](text-formatting.md), and [set pages](setting-pages.md) your way.
- Set goals with [quests](quests.md), and lock entries until they are earned.
- List your mod's content with [live lists](live-lists.md).
- Change how the book looks: [Styles and look](styles-and-look.md).
