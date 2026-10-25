# Flags and objectives

Books lock content and set quests with [conditions](../quests.md#conditions). Two of them take their answer from your
mod: flags, which are on or off, and objective sources, which are numbers.

## Flags

A flag lets your code unlock content. A lock or a quest objective names it (`{"flag": "mymod:hard_mode"}`), and you say
whether it is on:

```java
import net.ixdarklord.folios.api.client.FoliosGuides;

FoliosGuides.setFlag("mymod:hard_mode", () -> MyConfig.HARD_MODE.get());
```

```json title="An entry that opens in hard mode"
{
  "icon": "minecraft:wither_skeleton_skull",
  "requires": { "flag": "mymod:hard_mode" },
  "pages": ["Hard mode changes how the boss fights."]
}
```

The supplier is asked whenever a book is laid out. If its answer changes while a book may be open, call
`FoliosGuides.refresh()` so books are laid out again. Set flags on the client, for example while your client side
initialises. A flag no mod has set is off; `"!mymod:hard_mode"` is on while the flag is off.

Flags also choose text inside a page: `{flag:mymod:hard_mode|on text|off text}` (see
[Text formatting](../text-formatting.md#tags)).

## Objective sources

A quest objective `{"source": "mymod:mana", "goal": 100}` follows a value your mod reports for the local player:

```java
import net.ixdarklord.folios.api.client.FoliosObjectives;

FoliosObjectives.register(MyMod.id("mana"), player -> ManaClient.current());
FoliosObjectives.register(MyMod.id("spells_known"), player -> ClientSpells.known().size());
```

The value is asked whenever the objective is drawn or checked, so read it from what the client already has. A source
no mod registered stays at 0.

A `source` objective can lock content too, like any other condition:

```json
"requires": { "source": "mymod:spells_known", "goal": 10 }
```

## Reading conditions in code

`FoliosObjective.read(json)` reads one condition as a book writes it, and `FoliosObjective.readAll(json)` one or a list.
A category's, entry's or page's lock is a `FoliosLock` (`book`, `requires`, `secret`), with `isOpen()`.
