# Quests

Quests give players goals inside a book. A `quest` page lists objectives with progress bars, and they tick themselves
off live as the player plays, whether the book is open or not.

## Quest pages

```json title="entries/getting_started/first_quest.json"
{
  "name": "First Quest",
  "icon": "minecraft:compass",
  "sort": -1,
  "pages": [
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
  ]
}
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `objectives` | list of 1–8 | required | What the quest asks for. |
| `title` | text | "Quest" | A heading. |
| `text` | text | `""` | Text under the checklist. |
| `reward` | text | none | Shown under the list as "Reward: ...". It is only text: Folios gives no items. |

The page shows a bar for the whole quest with a count (or "Complete"), then each objective with a box that is ticked
once it is done. Objectives that count up (items, values, categories, the book) get a bar of their own.

An entry with a quest page is a **quest entry**: its row in the category list shows how far along it is, on a bar
notched between its objectives. When an objective or the whole quest is done, the book can
[celebrate](celebrations.md) it.

## Objectives

Each objective is an object naming one thing to follow. Any objective can also have a `label`, text (or a translation
key) shown in place of the one it makes for itself.

| Objective | Done when | Default label |
| --- | --- | --- |
| `{"advancement": "minecraft:story/mine_stone"}` | The player has the advancement. | The advancement's title. |
| `{"read": "basics/first_steps"}` | That entry of this book has been read. | "Read *entry name*" |
| `{"category": "basics"}` | Every open entry in that category has been read. Shows a bar. | "Read all of *category name*" |
| `{"book": true}` | Every open entry in the book has been read. Shows a bar. | "Read the whole book" |
| `{"flag": "mymod:met_the_wizard"}` | The flag, set by a mod with [`FoliosGuides.setFlag`](for-mod-developers.md#flags), is on. | The flag's name. |
| `{"item": "minecraft:diamond", "count": 5}` | The player carries that many. | "Collect *item name*", with a count. |
| `{"source": "mymod:mana", "goal": 100}` | A value a mod reports reaches the goal. | The source's id, with a count. |

Details:

- `item` takes an item, a `#tag` or a list, as for [icons](categories-and-entries.md#icons). Every matching item in
  the inventory counts. `count` defaults to 1.
- `source` values come from mods, registered with
  [`FoliosObjectives.register`](for-mod-developers.md#objectives). `goal` defaults to 1. A source no mod registered
  stays at 0.
- `goal` and `count` mean the same thing; either works for `item` and `source`.
- An object names one kind. If it has several of the keys above, the first in the table's order is used.

```json
"objectives": [
  { "category": "basics", "label": "Read the basics" },
  { "item": "#minecraft:logs", "count": 16 },
  { "flag": "mymod:met_the_wizard", "label": "Meet the wizard" },
  { "source": "mymod:mana", "goal": 100, "label": "Gather mana" },
  { "book": true }
]
```

## Locks

Quests often go with locked content. Categories, entries and pages can be locked behind an advancement or a flag, and
hidden until they open with `secret`:

```json title="entries/advanced/second_quest.json"
{
  "name": "The Next Step",
  "icon": "minecraft:iron_pickaxe",
  "advancement": "minecraft:story/smelt_iron",
  "pages": [
    {
      "type": "quest",
      "objectives": [
        { "item": "minecraft:iron_pickaxe" },
        { "advancement": "minecraft:story/mine_diamond" }
      ]
    }
  ]
}
```

| Key | Type | What it does |
| --- | --- | --- |
| `advancement` | identifier | Locked until the player has this advancement. |
| `flag` | string | Locked until the flag is on. A leading `!` reverses it. |
| `secret` | boolean | Hidden entirely until it opens. |

See [Locks](categories-and-entries.md#locks) for the details.

!!! note
    On a quest page, `advancement` and `flag` at the top of the page object lock the page itself. Inside
    `objectives`, they are objectives.

## Mastery

When a book has quests, its front page shows its **mastery**: a trophy and "Mastery", with "Progression:" and how far
along it is in percent under them, over a gilt bar split into quarters. The bar counts every objective of every quest
in the book. Once all are done the percentage is ticked, the bar turns green, and the trophy glints: every few seconds
a rainbow runs across it diagonally for a moment, and a sheen sweeps over it.

## Celebrations

Finishing an objective, a quest or the whole book (and reading an entry or a category through) can be celebrated with a
toast in the book's look, a sound, sparkles and, for mastery, fireworks. It is off unless the book turns it on: see
[Celebrations](celebrations.md).
