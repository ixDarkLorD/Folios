# Viewer recipe

`"type": "viewer_recipe"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

Any recipe, from any mod, drawn by the recipe viewer the player has: **JEI** or **REI** (and **EMI** on 1.21 and
1.20.1), in the book's own look: its slots and arrow are the book's. Machines, anvils, altars and other recipes show
here as the viewer lays them out, with the block they are made at as a badge on the frame's corner. Hovering an ingredient shows its
tooltip, in the book's own style, pressing it opens it in the viewer, and a list of recipes holds still while the
pointer is over it.

The [recipe](recipe.md) element draws through the viewer too when one is installed, so the two look alike; this
element is for recipes only a viewer knows, and for several recipes shown in turn.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `recipe` | identifier, or list of 1–16 | required | The recipe's id, as the viewer knows it (usually the recipe's own id). Several are shown in turn, a few seconds each. |
| `title` | text | none | A heading. |
| `text` | text | `""` | Text under the recipe. |
| `height` | integer | `72` | The room the recipe takes, in page pixels. 32 to 150. A recipe larger than its room is drawn smaller to fit. |
| `item` | item, or list | none | Items this page is about: looking one of them up opens this page. Viewer recipes aren't known when the book loads, so name them here. |

```json
{
  "type": "viewer_recipe",
  "title": "The Crusher",
  "recipe": ["mymod:crushing/iron_ore", "mymod:crushing/gold_ore"],
  "item": "mymod:crusher",
  "text": "Ores come out doubled."
}
```

What a reader without the viewer sees:

- **No viewer installed:** a vanilla recipe is drawn as the [recipe](recipe.md) element would; any other says "Needs JEI
  or REI to show this recipe".
- **A book held in the hands:** viewers draw only on the screen, so the page shows what the recipe makes, where it is
  made and "Open on screen to see this recipe".
- **A recipe the viewer doesn't know:** "No recipe" and its id. On a server without Folios's recipes synced to the
  viewer, modded recipes may not be known.

## Looking things up from the viewer

Holding the look-up key (**B** by default) over an item in the viewer's item list, its bookmarks or its recipe screen
opens that item's page, as it does over an item in an inventory. See [Reading](../reading.md#looking-things-up).
