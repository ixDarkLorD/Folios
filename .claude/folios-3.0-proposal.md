# Folios 3.0: proposals for features 6 and 7 (waiting for confirmation)

Written 2026-10-25 at the start of the scheduled run. Nothing here is built yet.

## Feature 6: observable page elements

### What a book author writes (JSON, no Java)

Any element that has a `text` can take `"observe"`. Its text is then built from a live list of rows, one template per row:

```json
{
  "type": "text",
  "title": "Sleep modifiers",
  "observe": {
    "source": "whycantisleep:sleep_modifiers",
    "row": "- **{$name}** ({$points} points): {$description}",
    "sort": "name",
    "where": {"field": "points", "below": 0},
    "empty": "*No modifiers yet.*"
  },
  "text": "Everything that changes how well you sleep:"
}
```

- `source`: the id of the list to observe.
- `row`: Markdown with `{$field}` placeholders, filled from each row. `{$...}` is new and can't clash with the existing `{tag}` syntax. Every Markdown tag, link and `{lang:...}` works in it, and a field can itself be Markdown (a row's `name` can be `{lang:whycantisleep.modifier.dark}`).
- Optional: `header` / `footer` (Markdown around the rows), `separator` (default a new line), `sort` (a field name, `-` before it for descending; default the source's order), `where` (one simple filter: `field` plus `is`, `not`, `above` or `below`), `limit`, and `empty` (Markdown shown when there are no rows).
- The built text is appended after the element's own `text`, then laid out like any other text: it splits across pages, takes overflow and alignment (features 4 and 5), and is found by search.
- `"observe"` also works on a page itself (a page's `text`), and on `links` (one link per row, from a `{$entry}` field) as a second step if wanted.

### What a mod writes (Java)

A new API class, `FoliosSources`, in two halves:

```java
// Client: rows the client already knows (registries, client config, addon content).
FoliosSources.register(MyMod.id("spells"), () -> Spells.all().stream()
        .map(spell -> FoliosSources.row(spell.id().toString())
                .field("name", spell.nameKey())          // Markdown or a translation key
                .field("mana", spell.cost()))
        .toList());
FoliosSources.changed(MyMod.id("spells"));               // when the list changed (an addon registered one)

// Server: rows that live on the server (data-driven content), synced to every client by Folios.
FoliosServerSources.register(WhyCantISleep.id("sleep_modifiers"), server -> SleepModifiers.all().stream()
        .map(modifier -> FoliosSources.row(modifier.id().toString())
                .field("name", modifier.label())          // a Component is stored as its translation key or text
                .field("points", modifier.basePoints()))
        .toList());
FoliosServerSources.changed(server, WhyCantISleep.id("sleep_modifiers"));
```

- A row is an id plus a small map of named fields (strings, numbers, booleans, or Components turned into text).
- Server sources are re-sent automatically on join and after every data pack reload (`/reload`), and when the mod calls `changed`.

### Data packs only

A data pack can make or extend a source with no Java: `data/<ns>/folios/sources/<name>.json`:

```json
{"source": "whycantisleep:sleep_modifiers", "rows": [{"id": "mypack:campfire", "name": "Near a campfire", "points": 3}]}
```

Rows with the same `source` from every pack are joined (a row with a used id replaces it), on the server, and synced like a server source. This also covers mods that don't call Folios at all.

### Syncing, change detection and caching

- One clientbound payload, `folios:source` (source id, a version number, and the rows), sent per changed source, never per tick. Clients without Folios get nothing (the existing `canPlayerReceive` check).
- Every source has a version counter on the client, bumped when its rows change (by `changed`, or a payload whose rows differ).
- While an entry is laid out, `GuideSession` records which sources its pages read, and at which version. When the entry is shown again, it is laid out again only if one of those versions moved. Nothing is rebuilt per frame, and books that observe nothing pay nothing. Search for that entry is re-indexed at the same time.
- An unknown source shows `empty` (or nothing) and logs once per source.

### Why Can't I Sleep

- Register `whycantisleep:sleep_modifiers` (server side, from `SleepModifiers.all()`, so data-driven ones come along) with fields `name`, `description`, `points`, `id`.
- Register `whycantisleep:sleep_qualities` (the factor ids in `SleepQuality`, with their labels and ranges).
- Change `entries/quality/modifiers.json` (and the qualities page) to observe them instead of listing them by hand.
- WCIS has unrelated uncommitted work right now, so this would be described as changes rather than edited, unless it is clean by then.

## Feature 7: audit and proposed JSON changes

Marked **[breaking]** where a 3.0 book must change (the 2.0-to-3.0 upgrader and the web tool would rewrite every one of these, so 2.x books keep loading).

### book.json

1. **[breaking] Remove `item_model`.** It repeats `models.item`.
   Before: `"item_model": "mymod:item/guide"` · After: `"models": {"item": "mymod:item/guide"}`
2. **[breaking] Remove `colors.tooltip`.** It repeats `panel`.
   Before: `"colors": {"tooltip": {"background": "#16202E"}}` · After: `"panel": {"background": "#16202E"}`
3. **[breaking] Put the handheld-only settings in `handheld`.** `tilt`, `distance` and `cover_texture` only do anything for handheld books, and `handheld` already holds `look_around` and `walk`.
   Before: `"tilt": 10, "distance": 0.7, "cover_texture": "mymod:textures/cover.png"`
   After: `"handheld": {"tilt": 10, "distance": 0.7, "cover_texture": "mymod:textures/cover.png"}`

### Categories

4. **Names and descriptions from lang keys by default,** as book text does in feature 2: `folios.<ns>.<book>.category.<id>.name` and `.description`, used when the keys are left out. Not breaking. A category file can shrink to `{"icon": "minecraft:oak_sapling", "sort": 0}`.
5. **Sub-categories (optional, larger):** `"parent": "world"` shows a category inside another, as a row of its list. Not breaking. I'd suggest leaving this for after 3.0 unless you want it now.

### Entries

6. **Names from lang keys by default:** `folios.<ns>.<book>.entry.<id with / as .>.name`. Not breaking. Today's test books write `"name": "guide.foliostest.handbook.entry.crafting.tools.name"` in every entry; that line can go.
7. **[breaking] Remove `category`.** The folder already says it, and a second way to say it lets the two disagree.
8. **[breaking] Remove the entry-level `text` shortcut.** `"pages": ["Some text"]` already does the same.

### Pages and elements

9. **[breaking] One key per thing shown, singular, taking one or a list.** Today: `recipe` / `recipes`, `structure` / `structures`, `build` / `builds`, and `images` (plural, takes one or a list). After: `recipe`, `structure`, `build` and `image`, each one value or a list, the way `item` already works.
   Before: `{"type": "recipe", "recipes": ["minecraft:iron_pickaxe", "minecraft:wooden_pickaxe"]}`
   After: `{"type": "recipe", "recipe": ["minecraft:iron_pickaxe", "minecraft:wooden_pickaxe"]}`
10. **`marker`** (feature 1) and the text reader (feature 2) are already settled.

### Locks and conditions (one shared format)

11. **[breaking] Locks become `"requires"`, in the objective format.** Today a lock is loose keys on the thing locked (`advancement`, `flag`, `secret`), which also clash with element keys (a spotlight's `item`, a quest's `objectives`). After: `"requires"` takes one condition or a list (all must hold), written exactly as an objective, and `secret` stays as it is.
    Before: `{"name": "...", "advancement": "minecraft:story/mine_stone", "flag": "mymod:hard_mode", "secret": true}`
    After: `{"requires": [{"advancement": "minecraft:story/mine_stone"}, {"flag": "mymod:hard_mode"}], "secret": true}`
    This gives locks every condition objectives have: `read` (an entry that opens after reading another: real prerequisites), `category`, `book`, `item`, `obtained` (feature 3), `source`, and the new ones below.
12. **New conditions, for both:** `{"quest": "basics/first_quest"}` (a quest of this book is done, so a finished quest can open the next entry), and `{"any": [...]}` (one of several). Not breaking.

### Quests and objectives

13. **[breaking] `count` is removed; `goal` only.** Today `item` uses `count` and `source` uses `goal` for the same idea.
    Before: `{"item": "minecraft:oak_log", "count": 8}` · After: `{"item": "minecraft:oak_log", "goal": 8}`
14. **`"in_order": true` on a quest:** each objective shows as locked (and doesn't count) until the one before it is done. Not breaking.
15. **`"hidden": true` on an objective:** shown as `???` until done. Not breaking.

### Not proposed (looked at, left as is)

- A whole string that is a translation key keeps being translated (every 2.x book relies on it, and it costs nothing).
- `reveal`, `colors`, `layout`, `textures`, `models`, `celebrations`: already one shape each, defaults not written out.
- Quest rewards stay text: books are client-side, so Folios can't give items safely.
