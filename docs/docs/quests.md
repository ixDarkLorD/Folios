# Quests, locks and progress

Quests give players goals inside a book, and locks keep content closed until it is earned. Both are written with the
same **conditions**: a quest lists them as objectives, with progress bars, and a lock waits for them.

## Quest pages

```json title="entries/getting_started/first_quest.json"
{
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
        { "carried": "minecraft:oak_log", "goal": 8 },
        { "advancement": "minecraft:story/mine_stone", "label": "Mine some stone" }
      ]
    }
  ]
}
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `objectives` | list of up to 32 | required, unless `quest` | What the quest asks for: [conditions](#conditions). |
| `quest` | id | none | A [server quest](#server-quests) to show in place of `objectives`: the server follows it and gives its rewards. |
| `in_order` | boolean | `false` | Each objective counts only once those before it are done. Until then it is shown muted, without a bar. |
| `title` | text | "Quest" | A heading. |
| `text` | text | `""` | Text under the quest. |
| `reward` | text | none | Shown at the end of the path as "Reward: ...". On its own it is only text; a [server quest](#server-quests) gives items. |
| `locked` | text | "Not yet open" | What a server quest says while its [`requires`](#opening-a-quest) isn't met. |

The page says how far along the quest is ("2 of 5 done", or "Complete"), then lays its objectives out as a path: a
stop for each, in its own frame, joined by a line drawn in as far as the player has come and dotted beyond. A stop is
ticked once its objective is done, and the one to work on next is ringed in the book's colour. Objectives that count up
(items carried, values, categories, the book) get a slim bar of their own. The path ends at the reward.

A quest is never split over pages. One too tall for a page draws its labels smaller, then lets its objectives scroll
with the mouse wheel, as many in view as the page holds, between the line at its top and the reward, which stay
where they are.

An entry with a quest page is a **quest entry**: its row in the category list shows how far along it is. When an
objective or the whole quest is done, the book can [celebrate](celebrations.md) it.

```json title="A quest in order, with a choice and a surprise"
{
  "type": "quest",
  "title": "Next Steps",
  "in_order": true,
  "objectives": [
    { "read": "basics/first_steps" },
    { "any": [{ "obtained": "minecraft:emerald" }, { "obtained": "minecraft:diamond" }] },
    { "carried": "minecraft:bread", "goal": 3, "hidden": true }
  ]
}
```

## Server quests

A quest written in a book is followed by the client, so it can show progress but can't be trusted to give anything. A
**server quest** is written in a data pack instead: the server follows it, checks it itself, and gives its rewards. A
book shows it with a quest page naming it.

```json title="data/mymod/folios/quests/first_supplies.json"
{
  "book": "mymod:handbook",
  "objectives": [
    { "read": "basics/first_steps", "label": "Read the First Steps entry" },
    { "obtained": "#minecraft:logs", "label": "Chop down a tree for a log" },
    { "carried": "minecraft:cobblestone", "goal": 4, "label": "Carry four cobblestone" }
  ],
  "rewards": {
    "items": ["minecraft:diamond*3", "minecraft:bread*8"],
    "random": [{ "item": "minecraft:emerald*4", "weight": 3 }, "minecraft:gold_ingot*8"],
    "random_rolls": 1
  }
}
```

```json title="assets/mymod/folios/handbook/entries/basics/supplies.json"
{
  "name": "Supplies",
  "icon": "minecraft:chest",
  "pages": [
    {
      "type": "quest",
      "title": "Supplies",
      "quest": "mymod:first_supplies",
      "text": "Gather what you need for the road."
    }
  ]
}
```

The quest's id is its file's path: `data/mymod/folios/quests/first_supplies.json` is `mymod:first_supplies`.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `objectives` | list of 1–32 | required | What it asks for: [conditions](#conditions), the ones the server can check (see below). |
| `book` | id | none | The book its `read` objectives are read in. |
| `requires` | condition, or list of them | none | What [opens it](#opening-a-quest). |
| `in_order` | boolean | `false` | Each objective counts only once those before it are done. |
| `rewards` | object | none | What it gives: [rewards](#rewards). |

The server can check `advancement`, `read`, `quest`, `flag`, `carried`, `obtained`, `source` and `any`. It doesn't have
the books, so `read_category` and `read_book` can't be used; a quest that does is skipped, with a message in the log.

- `read` names an entry of the quest's `book`, or of another book written before it: `"mymod:handbook|basics/first_steps"`.
- `quest` names another server quest by its id.
- `flag` is one of the player's own flags (set with `/folios quest flag` or [`FoliosQuests.setFlag`](api/quests.md)), or
  one a mod works out with `FoliosQuests.registerFlag`.
- `source` follows a value a mod gives the server with [`FoliosQuests.registerValue`](api/quests.md).
- `obtained` is noted by the server, as the item is in the player's inventory.

The server checks each player's quests about once a second, and straight away after an advancement, a flag or an
entry read. A quest once done stays done.

### Rewards

```json
"rewards": {
  "items": ["minecraft:diamond*3", { "id": "minecraft:potion", "components": { ... } }],
  "random": [{ "item": "minecraft:emerald*4", "weight": 3 }, "minecraft:gold_ingot*8"],
  "random_rolls": 1
}
```

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `items` | list of item stacks | none | Given every time: `"id"`, `"id*count"`, or a stack object. |
| `random` | list | none | A random gift drawn from these: an item stack, or `{"item": ..., "weight": 3}` to be picked 3 times as often. |
| `random_rolls` | integer | `1` | How many random gifts are drawn. |

The quest page shows "Rewards:" over a grid of slots: each item with its count, and a wrapped gift with a question mark
for the random ones (hover it for what it could be). Once the quest is done the panel is framed in the book's colour
and says "Press to claim": pressing it asks the server, which gives the reward once, into the inventory or at the
player's feet when it is full. It then shows as claimed.

### Opening a quest

`requires` keeps a server quest shut until what it names is done: one condition, or a list that must all be. Until
then none of its objectives count, so it can't be finished early, and its page says "Not yet open", or the page's own
`locked` text. Once open it stays open.

```json title="data/mymod/folios/quests/next_trip.json"
{
  "book": "mymod:handbook",
  "requires": { "quest": "mymod:first_supplies" },
  "objectives": [{ "obtained": "minecraft:iron_ingot" }],
  "rewards": { "items": ["minecraft:emerald*8"] }
}
```

```json title="Its page, with text of its own while it is shut"
{ "type": "quest", "quest": "mymod:next_trip", "locked": "*Finish Supplies first.*" }
```

The `quest` condition in a book's [locks](#locks) can also name a server quest, by its id, to hide a page or an entry
until it is done: `"requires": {"quest": "mymod:first_supplies"}`.

### What the server checks

Everything about a server quest is the server's: what it asks for, when it is done, and what it gives. A resource pack
can only change how the book shows it. Reading is the one thing that happens only on the player's screen, so the server
counts an entry read only when:

- the player has the book with them, or the server opened it for them (by the book item, or `FoliosBooks.open`);
- the entry stayed open long enough (2 seconds by default);
- they haven't read more entries in the last minute than allowed (20 by default).

What it turns down is noted in the server log. For rewards that must not be cheated at all, turn on `strict`: rewarded
quests that ask for reading are then skipped.

| Setting | Default | What it does |
| --- | --- | --- |
| `quests.strict` | `false` | Rewarded quests may not ask for entries to be read. |
| `quests.readSeconds` | `2.0` | How long an entry must be open to count as read. |
| `quests.readsPerMinute` | `20` | The most entries counted read in a minute. |
| `quests.needsBook` | `true` | Reading counts only in a book the player has with them, or one the server opened. |

They are in `config/folios-server.toml`.

### Commands

Operators can look at and change players' server quests:

| Command | What it does |
| --- | --- |
| `/folios quest list` | The server quests there are. |
| `/folios quest status <players> <quest>` | How far along each player is, and whether they claimed it. |
| `/folios quest complete <players> <quest>` | Finishes it for them. |
| `/folios quest reset <players> <quest>` | Takes it back, and its claim: they can do it, and claim it, again. |
| `/folios quest flag <players> <flag> <true\|false>` | Sets or clears one of their flags. |

## Conditions

Each condition is an object naming one thing to follow:

| Condition | Done when | Default label |
| --- | --- | --- |
| `{"advancement": "minecraft:story/mine_stone"}` | The player has the advancement. | The advancement's title. |
| `{"read": "basics/first_steps"}` | That entry of this book has been read. | "Read *entry name*" |
| `{"read_category": "basics"}` | Every open entry in that category has been read. Shows a bar. | "Read all of *category name*" |
| `{"read_book": true}` | Every open entry in the book has been read. Shows a bar. | "Read the whole book" |
| `{"quest": "basics/first_quest"}` | Every objective of that entry's quests is done; or, written with its namespace (`"mymod:first_quest"`), that [server quest](#server-quests) is. | "Finish *entry name*" |
| `{"carried": "minecraft:diamond", "goal": 5}` | The player carries that many now. | "Collect *item name*", with a count. |
| `{"obtained": "minecraft:diamond"}` | The item has ever been in the player's inventory, whatever became of it after. | "Obtain *item name*" |
| `{"flag": "mymod:met_the_wizard"}` | The flag, set by a mod with [`FoliosGuides.setFlag`](api/progress.md#flags), is on. | The flag's name. |
| `{"source": "mymod:mana", "goal": 100}` | A value a mod reports reaches the goal. | The source's id, with a count. |
| `{"any": [{...}, {...}]}` | Any one of the conditions in it is done. | Their labels, joined with "or". |

Every condition also takes:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `label` | text | its own | Shown in place of the label it makes for itself. A label too wide for its line is drawn smaller, then over up to three lines; one still too long is cut short with "…" and shown whole when hovered. |
| `hidden` | boolean | `false` | Shown as `???` until it is done. |
| `goal` | integer | `1` | For `carried` and `source`: how many, or how much, it takes. |

Details:

- `carried` and `obtained` take an item, a `#tag` or a list, as for [icons](categories-and-entries.md#icons). For
  `carried`, every matching item in the inventory counts; for `obtained`, any one of them having been there is enough.
- What has been `obtained` is noted the moment it is in the inventory, and kept with the player's
  [reading progress](categories-and-entries.md#reading-progress), so it stays done after the item is used up, and after
  a restart.
- A flag starting with `!` is reversed: done while the flag is off.
- `source` values come from mods, registered with [`FoliosObjectives.register`](api/progress.md#objective-sources). A
  source no mod registered stays at 0.
- An object names one kind. If it has several of the keys above, the first in the table's order is used.

## Locks

Categories, entries and single pages can be locked until the player has earned them, with `requires`: one condition,
or a list of them that must all be done. `secret` hides the thing entirely until it opens.

```json title="entries/advanced/second_quest.json"
{
  "icon": "minecraft:iron_pickaxe",
  "requires": [
    { "quest": "getting_started/first_quest" },
    { "advancement": "minecraft:story/smelt_iron" }
  ],
  "pages": [
    {
      "type": "quest",
      "objectives": [
        { "carried": "minecraft:iron_pickaxe" },
        { "advancement": "minecraft:story/mine_diamond" }
      ]
    }
  ]
}
```

| Key | Type | What it does |
| --- | --- | --- |
| `requires` | condition, or list of them | Locked until it holds, or until every one in the list holds. |
| `secret` | boolean | Hidden entirely until it opens. |

`label`, `hidden` and `goal` work in a lock as in a quest. See [Locks](categories-and-entries.md#locks) for what a
locked category, entry or page looks like.

## Mastery

When a book has quests, its front page shows its **mastery**: a trophy and "Mastery", with "Progression:" and how far
along it is in percent under them, over a gilt bar split into quarters. The bar counts every objective of every quest
in the book. Once all are done the percentage is ticked, the bar turns green, and the trophy glints: every few seconds
a rainbow runs across it diagonally for a moment, and a sheen sweeps over it.

## Celebrations

Finishing an objective, a quest or the whole book (and reading an entry or a category through) can be celebrated with a
toast in the book's look, a sound, sparkles and, for mastery, fireworks. It is off unless the book turns it on: see
[Celebrations](celebrations.md).
