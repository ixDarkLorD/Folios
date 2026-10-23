# For mod developers

A mod doesn't need any code to ship a book: put the book's JSON in your mod's `assets/<modid>/folios/` and it loads
like one from a resource pack. The Java API is for giving and opening books, unlocking content from code, and adding
your own page elements, reveal effects, objectives and styles.

| Class | Side | For |
| --- | --- | --- |
| `net.ixdarklord.folios.api.FoliosBooks` | both | Book items, and opening a book from the server. |
| `net.ixdarklord.folios.api.FoliosBookItem` | both | An item of your own that opens a book. |
| `net.ixdarklord.folios.api.client.FoliosGuides` | client | The loaded books, opening them, read state, flags. |
| `net.ixdarklord.folios.api.client.page.FoliosPageElements` | client | New element types. |
| `net.ixdarklord.folios.api.client.FoliosReveals` | client | New reveal effects. |
| `net.ixdarklord.folios.api.client.FoliosObjectives` | client | Values for quest objectives. |
| `net.ixdarklord.folios.api.client.FoliosStyles` | client | New ways to show a book. |

!!! note "1.21 and 1.20.1"
    The examples use `Identifier`, as on 26.1 and later. On 1.21 and 1.20.1 the same methods take a
    `ResourceLocation`.

## Setup

Folios is built on [CoolCatLib](https://modrinth.com/mod/coolcatlib) and needs it at runtime. Add both to your
development environment, and declare both as dependencies of your mod.

Both are on the [ModResources Maven](https://ixdarklord.github.io/ModResources/), free to use with no token. Its page
lists every version, ready to copy:

```groovy title="build.gradle"
repositories {
    maven {
        name = "ModResources"
        url = "https://ixdarklord.github.io/ModResources/maven/"
    }
}

dependencies {
    compileOnly "net.ixdarklord.folios:folios-common:<folios version>"        // in the common module
    implementation "net.ixdarklord.folios:folios-fabric:<folios version>"     // in each loader's module
    implementation "net.ixdarklord.coolcatlib:coolcatlib-fabric:<coolcatlib version>"
}
```

Artifacts are `net.ixdarklord.folios:folios-<module>:<minecraft>+<mod version>`, such as `26.1.2+2.1.1`, the module
being `common`, `fabric`, `neoforge` or `forge`. On 1.21.1 and older, use `modImplementation` for the loader jars.

They are on Modrinth too, so [Modrinth's Maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven) works
as well, by project slug: `maven.modrinth:folios:<folios version>` and `maven.modrinth:coolcatlib:<coolcatlib version>`.

## Opening books

From the server, open a book on a player's screen, at its start or at an entry:

```java
import net.ixdarklord.folios.api.FoliosBooks;

FoliosBooks.open(player, MyMod.id("handbook"));
FoliosBooks.open(player, MyMod.id("handbook"), "basics/first_steps");
```

This does nothing for a client without Folios.

On the client, `FoliosGuides` opens a book directly, in its own style. An entry can include an anchor:

```java
import net.ixdarklord.folios.api.client.FoliosGuides;

FoliosGuides.open(MyMod.id("handbook"));                              // where the player left off
FoliosGuides.open(MyMod.id("handbook"), "crafting/tools#pickaxe");     // false if missing or locked
boolean read = FoliosGuides.isRead(MyMod.id("handbook"), "basics/first_steps");
```

`FoliosGuides.book(id)` and `FoliosGuides.books()` give the loaded books. Books are read again whenever resources
reload, so don't keep a `FoliosBook` past that.

## Book items

`folios:guide_book` opens whichever book its stack names. Make a stack for a book with `FoliosBooks.createStack`:

```java
player.addItem(FoliosBooks.createStack(MyMod.id("handbook")));
player.addItem(FoliosBooks.createStack("mymod:handbook"));
```

For an item of your own, register a `FoliosBookItem`. Given a book, it always opens that book and needs nothing on the
stack: a stack from the creative tab, `/give` or a recipe shows the book's cover and opens it as one made with
`createStack` does.

```java
import net.ixdarklord.folios.api.FoliosBookItem;

ITEMS.register("handbook", () -> new FoliosBookItem(properties, MyMod.id("handbook")));
```

A `FoliosBookItem` takes the name of its book, unless the stack has a custom name, and shows who the book is from under
it. To put a book on your own item's stack instead, use `FoliosBooks.createStack(item, bookId)`; a book named on the
stack wins over the item's own.

`FoliosBooks.bookOf(stack)` says which book a stack is: the one it names, or else, for a `FoliosBookItem` of your own,
that item's book. It is null for a stack that is neither.

!!! note "1.20.1"
    On 1.20.1 the book is stored in the stack's NBT under `folios_book`, not in a `folios:guide_book` component. The
    methods above work the same.

## Flags

Flags let your code unlock content. A category, entry, page or quest objective can name a flag
(`"flag": "mymod:hard_mode"`), and you say whether it is on:

```java
FoliosGuides.setFlag("mymod:hard_mode", () -> MyConfig.HARD_MODE.get());
```

The supplier is asked whenever a book is laid out. If its answer changes while a book may be open, call
`FoliosGuides.refresh()` so books are laid out again. Set flags on the client, for example while your client side
initialises.

See [Locks](categories-and-entries.md#locks) and [quest objectives](quests.md#objectives).

## Page elements

An element type is a record implementing `FoliosPageElement`, with a `MapCodec` that reads its JSON. Register it with
`FoliosPageElements` while the client initialises, before resources load. Its id is what `"type"` names in JSON.

```java
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPageElements;
import net.minecraft.network.chat.Component;

import java.util.List;

public record CounterElement(String title, int count) implements FoliosPageElement {
    public static final MapCodec<CounterElement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(CounterElement::title),
            Codec.INT.fieldOf("count").forGetter(CounterElement::count)).apply(i, CounterElement::new));

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        // A title, 20 pixels kept for our own drawing, no text after it.
        return layout.flow(title, 20, null, (painter, y) -> {
            painter.fill(FoliosLayout.CONTENT_LEFT, y, painter.contentRight(), y + 20, painter.palette().panel());
            painter.text(Component.literal("× " + count), FoliosLayout.CONTENT_LEFT + 4, y + 6, painter.palette().text());
        });
    }
}

FoliosPageElements.register(MyMod.id("counter"), CounterElement.CODEC);
```

```json
{ "type": "mymod:counter", "title": "Count", "count": 3 }
```

`lay` turns the element into the pages it takes; each `Laid` draws one of them. `FoliosLayout.flow` does the usual
shape for you: a title, a part of your own of a given height (`Part.draw(painter, y)`, `y` being where it starts), and
text after it that runs onto further pages when it doesn't fit.

### Page size

A page is `FoliosLayout.PAGE_WIDTH` by `PAGE_HEIGHT` (136 by 180) at the Auto GUI scale and in a handheld book. At a
smaller GUI scale the book keeps its size on the screen and its pages grow, up to 2.5 times, so more fits on each. Lay
out and draw to the page you are given rather than to the default one:

| Instead of | Use | What it is |
| --- | --- | --- |
| `PAGE_WIDTH`, `PAGE_HEIGHT` | `layout.pageWidth()`, `painter.pageWidth()`, and the same for height | The page's size. |
| `CONTENT_RIGHT` | `layout.contentRight()`, `painter.contentRight()` | Where content stops across. |
| `CONTENT_WIDTH` | `layout.contentWidth()` | The width content has. |
| `CONTENT_BOTTOM` | `layout.contentBottom()`, `painter.contentBottom()` | Where content stops down, above the page number and turn buttons. |

`CONTENT_LEFT` and `CONTENT_TOP` are the same on every page. Use the layout's while laying out (`lay`) and the
painter's while drawing. For a picture or scene that should keep its shape on a wider page, `layout.scaled(height)`
gives the height to use for one designed at the default width. Content keeps `MARGIN` (12) pixels in from the page's
sides, clear of the page frame.

### Drawing

Elements draw through a `FoliosPainter`, so the same element works on a screen book and a handheld book, and takes
part in reveal effects. It draws text, items, textures, fills and scenes, moves and scales (`push`, `translate`,
`scale`, `pop`), and marks places that can be pressed (`link`, `action`), dragged (`drag`) or scrolled (`scroll`), with
their tooltips. `painter.palette()` has the book's colours for the current light or dark mode.

### In a page's elements

A type that lays itself out through `FoliosLayout.flow`, as above, can also be one of a page's
[`elements`](pages.md#elements): Folios captures what it asks `flow` for (its title, its own part and that part's
height, its text) with `FoliosLayout.capture` and sets it among the page's other elements. A spread can't be an element.

`FoliosPageElement` also has optional methods:

| Method | What it does |
| --- | --- |
| `spread()` | Return `true` to lay the element over both pages of the open book, like a [spread](spread-and-empty.md#spread). |
| `searchText()` | Words the book's search should find the page by. |
| `items()` | Items the page is about: pressing one anywhere in the book opens this page. |
| `objectives()` | Quest objectives, making its entry a quest. |

## Reveal effects

A reveal effect is asked, for each thing drawn on a page, how it looks at a point in the reveal: how visible it is, how
far it is moved, a tint, and how much of its text shows as runes. Register it with an id books can name in `"reveal"`.

```java
import net.ixdarklord.folios.api.client.FoliosReveal;
import net.ixdarklord.folios.api.client.FoliosReveals;
import net.ixdarklord.folios.api.client.page.FoliosLayout;

// Each line slides in from the left, the higher ones first.
FoliosReveals.register(MyMod.id("slide"), (progress, x, y) -> {
    float local = FoliosReveal.stagger(progress, y / FoliosLayout.PAGE_HEIGHT, 0.5F);
    return new FoliosReveal.Look(local, -(1 - FoliosReveal.easeOut(local)) * 12, 0);
});
```

```json
"reveal": { "effect": "mymod:slide", "seconds": 0.6 }
```

`progress` runs from 0 as the page lands to 1 when it is done. `x` is across the open spread (0 at the left page's
outer edge, `FoliosLayout.PAGE_WIDTH` at the fold), `y` down the page, both measured on the default page whatever size
the pages are. `look` is called very often, so keep it cheap.

A `Look` is `(opacity, dx, dy)`, or `(opacity, dx, dy, tint, runes)` with an ARGB `tint` laid over the thing and
`runes` from 0 to 1, how much of its text shows as runes. `Look.SHOWN` and `Look.HIDDEN` are there for the ends.

A `FoliosReveal` can also override `overlay(...)`, to draw over the page while the reveal plays (sparks, smoke), and
`around(...)`, to draw about each letter, item or picture as it appears. The helpers `FoliosReveal.stagger`,
`easeOut`, `smooth` and `noise` are there for writing effects.

## Objectives

A quest objective `{"source": "mymod:mana", "goal": 100}` follows a value your mod reports for the local player:

```java
import net.ixdarklord.folios.api.client.FoliosObjectives;

FoliosObjectives.register(MyMod.id("mana"), player -> ManaClient.current());
FoliosObjectives.register(MyMod.id("spells_known"), player -> ClientSpells.known().size());
```

The value is asked whenever the objective is drawn or checked, so read it from what the client already has.

## Styles

A style decides how a book is shown. Register one, and books choose it with `"style": "mymod:scroll"`:

```java
import net.ixdarklord.folios.api.client.FoliosStyles;

FoliosStyles.register(MyMod.id("scroll"), (book, at, hand) -> {
    // book: the FoliosBook to show
    // at:   where to open it (a FoliosBook.Location), or null for where the player left off
    // hand: the hand holding the book item, or null when opened another way
    Minecraft.getInstance().setScreen(new ScrollScreen(book, at));
});
```

The built-in styles are `FoliosStyles.SCREEN` (`folios:screen`) and `FoliosStyles.HANDHELD` (`folios:handheld`).

## Coming from 2.x

Every API class was renamed from `Guide…` to `Folios…`, with no old names kept, so code written against 2.x needs its
imports and class names updated. The packages are the same.

| 2.x | Now |
| --- | --- |
| `GuidePageTypes` | `FoliosPageElements` |
| `GuidePage` | `FoliosPageElement` |
| `Guides` | `FoliosGuides` |
| `GuideBooks`, `GuideBookItem` | `FoliosBooks`, `FoliosBookItem` |
| `GuideBook`, `GuideCategory`, `GuideEntry`, `GuideLock` | `FoliosBook`, `FoliosCategory`, `FoliosEntry`, `FoliosLock` |
| `GuideObjective`, `GuideObjectives` | `FoliosObjective`, `FoliosObjectives` |
| `GuideReveal`, `GuideReveals`, `GuideStyles` | `FoliosReveal`, `FoliosReveals`, `FoliosStyles` |
| `GuideIcon`, `GuideLayout`, `GuidePainter`, `GuidePalette`, `GuideStack`, `GuideText` | `FoliosIcon`, `FoliosLayout`, `FoliosPainter`, `FoliosPalette`, `FoliosStack`, `FoliosText` |

Nested types keep their names (`FoliosPageElement.Laid`, `FoliosBook.Location`, `FoliosReveal.Look`). Books' JSON is
unchanged.
