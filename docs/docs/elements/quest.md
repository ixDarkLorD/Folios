# Quest

`"type": "quest"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

A path of objectives, each in its own frame, leading to the reward, updated live as the player plays. See
[Quests, locks and progress](../quests.md) for the conditions an objective can be, and for
[server quests](../quests.md#server-quests), which the server follows and rewards.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `objectives` | list of up to 32 | required, unless `quest` | The objectives: [conditions](../quests.md#conditions). |
| `quest` | id | none | A [server quest](../quests.md#server-quests) to show in their place, with its rewards to claim. |
| `in_order` | boolean | `false` | Each objective counts only once those before it are done. |
| `title` | text | "Quest" | A heading. |
| `text` | text | `""` | Text under the quest. |
| `reward` | text | none | What it gives, shown as "Reward: ..." at the end of the path. |
| `locked` | text | "Not yet open" | What a server quest says while it is shut. |

```json
{
  "type": "quest",
  "title": "Getting Going",
  "text": "These tick themselves off as you play.",
  "reward": "Bragging rights",
  "objectives": [
    { "read": "getting_started/first_steps" },
    { "carried": "minecraft:oak_log", "goal": 8 },
    { "advancement": "minecraft:story/mine_stone", "label": "Mine some stone" }
  ]
}
```
