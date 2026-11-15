# Exporting a book

A book can be exported as a website that reads like the book: its spreads drawn as in game, with page-turn buttons and
a turning leaf, the arrow keys and swipes, its category tabs, a search box, and links that work: pressing a link or a
row in a list goes where it goes in the book. Beside it is a text version of every entry for search engines and
screen readers. Host it anywhere that serves plain files, such as GitHub Pages, to have the book online.

In game, with the book loaded:

```
/folios export mymod:my_guide
```

The book is drawn page by page for a moment, then the chat says where the site was written, as a link to open it:

```
.minecraft/folios-export/<namespace>/<book>/
  index.html             the book itself: spreads, page turns, tabs and search
  book.js                the book's pages, links and search words, read by index.html
  contents.html          the text version: the landing text and every category with its entries
  entries/<id>.html      the text version of each entry, linked to the ones before and after it
  pages/*.png            every page of the book, as the book draws it
  items/*.png            the items named in the text
  icons/*.png            the book's, categories' and entries' icons
  style.css              the look, in the book's own colours
```

What becomes what:

- **Text pages** become text: headings, lists, notes, bold and italic, colours, and links between entries, which go to
  the entry's page. Items in the text show their icon and name, linked to the entry about them.
- **Every other page** (recipes, scenes, builds, quests, maps and the rest) becomes a picture of the page as the
  book draws it, with the page's words as its description for search engines and screen readers.
- An element that scrolls in game shows the part in view when it opens; a scene shows its first angle.
- `{key:...}` shows the key bound on the exporting player's game, `{player}` reads "the reader", and a `{flag:...}` shows
  its "off" text.

Pressing Escape while it runs stops the export. Exporting again replaces the files.
