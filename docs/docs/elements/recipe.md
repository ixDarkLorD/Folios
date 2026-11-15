# Recipe

`"type": "recipe"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

One or two recipes, drawn as the game shows them: a crafting grid, a furnace (with its fuel and time), a stonecutter
or a smithing table. Each slot cycles through every item it accepts, and the block the recipe is made at is shown
as a badge on the frame's top-right corner. A slot that takes a tag or several items has a white mark in its corner,
and its tooltip shows every item it accepts.

A mod's machines are drawn as the mod lays them out through Folios's [recipe layouts](../api/recipe-layouts.md):
its slots, arrows, flames and gauges in the book's look. Any other kind of recipe is drawn as its ingredients in slots,
an arrow, the result, and the block it is made at; one with more than nine ingredients shows them nine at a time, in
turn. No recipe viewer is needed for any of it.

A player with **JEI** or **REI** installed can have recipes drawn by the viewer instead, laid out exactly as the viewer
lays them out, by turning on `recipesThroughViewer` in their [client settings](../reading.md#recipes). In a book held
in the hands the page always draws it itself.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `recipe` | identifier, or list of 1–2 | required | One recipe id, or two in a list to show both. |
| `title` | text | the result's name | A heading. A page with one recipe and no title is named after what it makes. |
| `text` | text | `""` | Text under the recipes. |

```json
{
  "type": "recipe",
  "recipe": ["minecraft:iron_pickaxe", "minecraft:wooden_pickaxe"],
  "text": "Two recipes fit on a page."
}
```

A recipe that doesn't exist shows "No recipe" and its id. A recipe that can't be drawn, because it has nothing to show
or its mod fails while it is drawn, says so in the middle of its frame; the error is written to the game's log.

!!! note mc-26 "Where recipes come from: 26.1 and later"
    Clients aren't sent recipes, so the page asks the server for them the first time it is shown.

!!! note mc-121 mc-120 "Where recipes come from: 1.21 and 1.20.1"
    The page reads the recipes the client was sent when the player joined.
