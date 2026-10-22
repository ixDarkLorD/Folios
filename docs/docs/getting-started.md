# Your first book

This page walks through making a small book with one category and one entry, then opening it in game.

## Start from the template

The quickest start is the template resource pack, `folios-book-template`, which you can get from the
[Downloads](../downloads.md) section. It holds one complete example book with every kind of page, and a README of the
keys.

1. Copy the template folder into `.minecraft/resourcepacks/` and turn it on in **Options → Resource Packs**.
2. Rename `assets/mymod` to your namespace and `guides/my_guide` to your book's id.

The rest of this page builds the same kind of book by hand, so you can see what each file does.

## 1. The book

Create `assets/mymod/guides/my_guide/book.json`:

```json title="book.json"
{
  "name": "My Guide",
  "subtitle": "Everything about my mod",
  "style": "handheld",
  "accent": "#C0602A",
  "cover": "#5A3A22",
  "icon": "minecraft:writable_book",
  "landing": "Welcome to **My Guide**.\n\nPick a category from the tabs, or [start here](entry:getting_started/first_steps)."
}
```

- `name` and `subtitle` are shown on the cover, the front page and the item.
- `style` is `"screen"` (a flat book on the screen, the default) or `"handheld"` (held open in your hands).
- `accent` colours marks and links; `cover` colours the cover.
- `landing` is the text of the book's front page.

See [book.json](book.md) for every key.

## 2. A category

Create `assets/mymod/guides/my_guide/categories/getting_started.json`:

```json title="categories/getting_started.json"
{
  "name": "Getting Started",
  "description": "The basics of my mod.",
  "icon": "minecraft:oak_sapling",
  "sort": 0
}
```

The file name, `getting_started`, is the category's id.

## 3. An entry

Create `assets/mymod/guides/my_guide/entries/getting_started/first_steps.json`:

```json title="entries/getting_started/first_steps.json"
{
  "name": "First Steps",
  "icon": "minecraft:wooden_pickaxe",
  "pages": [
    {
      "type": "text",
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

Your folder now looks like this:

```text
assets/mymod/guides/my_guide/
  book.json
  categories/getting_started.json
  entries/getting_started/first_steps.json
```

## 4. Get the book in game

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

## 5. Reload and open it

Press ++f3+t++ to reload resources whenever you change a file, then open the book again. If a page has a mistake, the
book shows an error in its place and the game log says what is wrong.

## Next steps

- Add more [page types](pages.md): recipes, images, entities, builds, links, quests and spreads.
- Learn the [text formatting](text-formatting.md).
- Lock entries behind advancements, or make them secret: [Categories and entries](categories-and-entries.md#locks).
- Change how the book looks: [Styles and look](styles-and-look.md).
