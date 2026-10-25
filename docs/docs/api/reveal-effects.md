# Reveal effects

A reveal effect is asked, for each thing drawn on a page, how it looks at a point in the reveal: how visible it is, how
far it is moved, a tint, and how much of its text shows as runes. Register it with an id books can name in
[`"reveal"`](../book.md#reveal).

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
