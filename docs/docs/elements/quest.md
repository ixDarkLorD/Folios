# Quest

`"type": "quest"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

A checklist of objectives with progress bars, updated live as the player plays. See [Quests](../quests.md) for the
objective types.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `objectives` | list of 1–8 | required | The [objectives](../quests.md#objectives). |
| `title` | text | "Quest" | A heading. |
| `text` | text | `""` | Text under the checklist. |
| `reward` | text | none | What it gives, shown as "Reward: ..." under the list. |

```json
{
  "type": "quest",
  "title": "Getting Going",
  "text": "These tick themselves off as you play.",
  "reward": "Bragging rights",
  "objectives": [
    { "read": "getting_started/first_steps" },
    { "item": "minecraft:oak_log", "count": 8 },
    { "advancement": "minecraft:story/mine_stone", "label": "Mine some stone" }
  ]
}
```
