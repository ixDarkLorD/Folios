# Live lists

A page can [observe](../live-lists.md) a **source**: a list of rows your mod gives, written out as a line of markup each.
The page is written again whenever the list changes, so content added by your mod, its addons or data packs shows in
the book without anyone editing the book.

A source is an id and its rows. A row is an id and named fields, each kept as text: markup, a translation key, a number
or anything else a page might show.

```java
import net.ixdarklord.folios.api.FoliosSources;

FoliosSources.Row row = FoliosSources.row("mymod:fireball")
        .field("name", "spell.mymod.fireball")   // a translation key: the page shows its translation
        .field("mana", 12)                         // kept as "12", sorted as a number
        .field("school", "Fire")
        .build();
```

Sources are given on the client or on the server, as suits where the content is known.

## Client sources

For what the client knows: registries, client config, what addons register in code. The supplier is asked each time a
page observing the source is laid out.

```java
FoliosSources.register(MyMod.id("spells"), () -> Spells.all().stream()
        .map(spell -> FoliosSources.row(spell.id().toString())
                .field("name", spell.nameKey())
                .field("mana", spell.cost())
                .field("school", spell.school().displayName())
                .build())
        .toList());
```

When the list changes (an addon registered a spell, a config reloaded), tell open books to write their pages again:

```java
FoliosSources.changed(MyMod.id("spells"));
```

Register while your client side initialises.

## Server sources

For what lives on the server: data-driven content, recipes your mod adds, anything loaded from data packs. Folios sends
every server source to each player as they join and again after every data pack reload.

```java
FoliosSources.registerServer(MyMod.id("rituals"), server -> Rituals.all(server).stream()
        .map(ritual -> FoliosSources.row(ritual.id().toString())
                .field("name", ritual.titleKey())
                .field("cost", ritual.cost())
                .build())
        .toList());
```

When it changes at another time, send it to everyone again:

```java
FoliosSources.changedServer(server, MyMod.id("rituals"));
```

Register while your mod initialises, on both sides (the dedicated server too).

## Rows from data packs

Any source, yours or another mod's, takes rows from data packs, with no code:
`data/<namespace>/folios/sources/<name>.json`. A row whose id is already in the source takes its fields over the ones
it had; a new id adds a row. See [Live lists](../live-lists.md#sources-from-data-packs).

## How the rows are put together

On the client, a source's rows are, in order: those Folios gives itself for its own sources (`folios:items`, ...),
those the server sent (its server source and data packs' rows), then those of a client source with the same id, each
row whose id is already there taking its place; then the rows data packs [removed](../live-lists.md#removing-rows) are
taken out. A source no one gives is empty, and a page observing it shows its `empty` text and logs a warning once.

Pages observing a source are written again when Folios's progress changes, which `changed` and `changedServer` do, as
do the player's advancements and reading. An open book lays itself out again at the same place.

| Method | Side | What it does |
| --- | --- | --- |
| `FoliosSources.row(id)` | both | Starts a row: `.field(name, value)` for each field, then `.build()`. |
| `register(id, supplier)` | client | Adds a client source. |
| `changed(id)` | client | Writes pages observing a client source again. |
| `rows(id)`, `rows(id, of)` | client | The rows of a source as pages see them; `of` narrows one of Folios's own. |
| `exists(id)` | client | Whether anyone has given the source. |
| `registerServer(id, function)` | server | Adds a server source. |
| `changedServer(server, id)` | server | Sends a server source to every player again. |
| `serverRows(server, id)` | server | A server source's rows now, with data packs' added. |

A source carries up to 4,096 rows of up to 64 fields each.
