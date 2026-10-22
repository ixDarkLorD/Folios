# For mod developers

A mod doesn't need any code to ship a book: put the book's JSON in your mod's `assets/<modid>/guides/` and it loads
like one from a resource pack. The Java API is for giving and opening books, unlocking content from code, and adding
your own page types, reveal effects, objectives and styles.

| Class | Side | For |
| --- | --- | --- |
| `net.ixdarklord.folios.api.GuideBooks` | both | Book items, and opening a book from the server. |
| `net.ixdarklord.folios.api.GuideBookItem` | both | An item of your own that opens a book. |
| `net.ixdarklord.folios.api.client.Guides` | client | The loaded books, opening them, read state, flags. |
| `net.ixdarklord.folios.api.client.page.GuidePageTypes` | client | New page types. |
| `net.ixdarklord.folios.api.client.GuideReveals` | client | New reveal effects. |
| `net.ixdarklord.folios.api.client.GuideObjectives` | client | Values for quest objectives. |
| `net.ixdarklord.folios.api.client.GuideStyles` | client | New ways to show a book. |

!!! note "1.21 and 1.20.1"
    The examples use `Identifier`, as on 26.1 and later. On 1.21 and 1.20.1 the same methods take a
    `ResourceLocation`.

## Setup

Folios is built on [CoolCatLib](https://modrinth.com/mod/coolcatlib) and needs it at runtime. Add both to your
development environment, and declare both as dependencies of your mod.

Both are on Modrinth, so the simplest way to depend on them is
[Modrinth's Maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven), which serves every Modrinth
project by its slug. Use the version number shown on each project's Versions page:

```groovy title="build.gradle"
repositories {
    exclusiveContent {
        forRepository { maven { url = "https://api.modrinth.com/maven" } }
        filter { includeGroup "maven.modrinth" }
    }
}

dependencies {
    // Fabric (Loom); on NeoForge or Forge use the loader's own configuration for mod dependencies.
    modImplementation "maven.modrinth:folios:<folios version>"
    modImplementation "maven.modrinth:coolcatlib:<coolcatlib version>"
}
```

Folios versions are written `<minecraft>+<mod version>`, such as `26.1.2+1.0`.

## Opening books

From the server, open a book on a player's screen, at its start or at an entry:

```java
import net.ixdarklord.folios.api.GuideBooks;

GuideBooks.open(player, MyMod.id("handbook"));
GuideBooks.open(player, MyMod.id("handbook"), "basics/first_steps");
```

This does nothing for a client without Folios.

On the client, `Guides` opens a book directly, in its own style. An entry can include an anchor:

```java
import net.ixdarklord.folios.api.client.Guides;

Guides.open(MyMod.id("handbook"));                              // where the player left off
Guides.open(MyMod.id("handbook"), "crafting/tools#pickaxe");     // false if missing or locked
boolean read = Guides.isRead(MyMod.id("handbook"), "basics/first_steps");
```

`Guides.book(id)` and `Guides.books()` give the loaded books. Books are read again whenever resources reload, so don't
keep a `GuideBook` past that.

## Book items

`folios:guide_book` opens whichever book its stack names. Make a stack for a book with `GuideBooks.createStack`:

```java
player.addItem(GuideBooks.createStack(MyMod.id("handbook")));
player.addItem(GuideBooks.createStack("mymod:handbook"));
```

For an item of your own, register a `GuideBookItem`. Given a book, it always opens that book and needs nothing on the
stack:

```java
import net.ixdarklord.folios.api.GuideBookItem;

ITEMS.register("handbook", () -> new GuideBookItem(properties, MyMod.id("handbook")));
```

A `GuideBookItem` takes the name of its book, unless the stack has a custom name. To put a book on your own item's
stack instead, use `GuideBooks.createStack(item, bookId)`. `GuideBooks.bookOf(stack)` reads which book a stack names.

!!! note "1.20.1"
    On 1.20.1 the book is stored in the stack's NBT under `folios_book`, not in a `folios:guide_book` component. The
    methods above work the same.

## Flags

Flags let your code unlock content. A category, entry, page or quest objective can name a flag
(`"flag": "mymod:hard_mode"`), and you say whether it is on:

```java
Guides.setFlag("mymod:hard_mode", () -> MyConfig.HARD_MODE.get());
```

The supplier is asked whenever a book is laid out. If its answer changes while a book may be open, call
`Guides.refresh()` so books are laid out again. Set flags on the client, for example while your client side
initialises.

See [Locks](categories-and-entries.md#locks) and [quest objectives](quests.md#objectives).

## Page types

A page type is a record implementing `GuidePage`, with a `MapCodec` that reads its JSON. Register it while the client
initialises, before resources load. Its id is what `"type"` names in JSON.

```java
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePageTypes;
import net.minecraft.network.chat.Component;

import java.util.List;

public record CounterPage(String title, int count) implements GuidePage {
    public static final MapCodec<CounterPage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(CounterPage::title),
            Codec.INT.fieldOf("count").forGetter(CounterPage::count)).apply(i, CounterPage::new));

    @Override
    public List<Laid> lay(GuideLayout layout) {
        // A title, 20 pixels kept for our own drawing, no text after it.
        return layout.flow(title, 20, null, (painter, y) ->
                painter.text(Component.literal("× " + count), 10, y, painter.palette().text()));
    }
}

GuidePageTypes.register(MyMod.id("counter"), CounterPage.CODEC);
```

```json
{ "type": "mymod:counter", "title": "Count", "count": 3 }
```

Pages draw through a `GuidePainter`, so the same page works on a screen book and a handheld book, and takes part in
reveal effects. `GuidePage` also has optional methods:

| Method | What it does |
| --- | --- |
| `spread()` | Return `true` to lay the page over both pages of the open book, like a [spread](pages.md#spread). |
| `searchText()` | Words the book's search should find the page by. |
| `items()` | Items the page is about: pressing one anywhere in the book opens this page. |
| `objectives()` | Quest objectives, making its entry a quest. |

## Reveal effects

A reveal effect is asked, for each thing drawn on a page, how it looks at a point in the reveal: how visible it is, how
far it is moved, a tint, and how much of its text shows as runes. Register it with an id books can name in `"reveal"`.

```java
import net.ixdarklord.folios.api.client.GuideReveal;
import net.ixdarklord.folios.api.client.GuideReveals;
import net.ixdarklord.folios.api.client.page.GuideLayout;

// Each line slides in from the left, the higher ones first.
GuideReveals.register(MyMod.id("slide"), (progress, x, y) -> {
    float local = GuideReveal.stagger(progress, y / GuideLayout.PAGE_HEIGHT, 0.5F);
    return new GuideReveal.Look(local, -(1 - GuideReveal.easeOut(local)) * 12, 0);
});
```

```json
"reveal": { "effect": "mymod:slide", "seconds": 0.6 }
```

`progress` runs from 0 as the page lands to 1 when it is done. `x` is across the open spread (0 at the left page's
outer edge, `GuideLayout.PAGE_WIDTH` at the fold), `y` down the page. `look` is called very often, so keep it cheap.

A `GuideReveal` can also override `overlay(...)`, to draw over the page while the reveal plays (sparks, smoke), and
`around(...)`, to draw about each letter, item or picture as it appears. The helpers `GuideReveal.stagger`,
`easeOut`, `smooth` and `noise` are there for writing effects.

## Objectives

A quest objective `{"source": "mymod:mana", "goal": 100}` follows a value your mod reports for the local player:

```java
import net.ixdarklord.folios.api.client.GuideObjectives;

GuideObjectives.register(MyMod.id("mana"), player -> ManaClient.current());
GuideObjectives.register(MyMod.id("spells_known"), player -> ClientSpells.known().size());
```

The value is asked whenever the objective is drawn or checked, so read it from what the client already has.

## Styles

A style decides how a book is shown. Register one, and books choose it with `"style": "mymod:scroll"`:

```java
import net.ixdarklord.folios.api.client.GuideStyles;

GuideStyles.register(MyMod.id("scroll"), (book, at, hand) -> {
    // book: the GuideBook to show
    // at:   where to open it, or null for where the player left off
    // hand: the hand holding the book item, or null when opened another way
    Minecraft.getInstance().setScreen(new ScrollScreen(book, at));
});
```

The built-in styles are `GuideStyles.SCREEN` (`folios:screen`) and `GuideStyles.HANDHELD` (`folios:handheld`).
