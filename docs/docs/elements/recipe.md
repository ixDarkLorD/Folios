# Recipe

`"type": "recipe"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

One or two recipes, drawn as the game shows them: a crafting grid, a furnace (with its fuel and time), a stonecutter
or a smithing table. Each slot cycles through every item it accepts, and the block the recipe is made at is shown
under the result.

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

A recipe that doesn't exist shows "No recipe" and its id.

!!! note mc-26 "Where recipes come from: 26.1 and later"
    Clients aren't sent recipes, so the page asks the server for them the first time it is shown.

!!! note mc-121 mc-120 "Where recipes come from: 1.21 and 1.20.1"
    The page reads the recipes the client was sent when the player joined.
