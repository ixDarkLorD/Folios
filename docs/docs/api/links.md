# Links and popups

`[label](scheme:target)` in a book's text goes to the kind of link registered for `scheme`. Folios's own are
`entry:`, `category:`, `item:`, web addresses, and the [popups](../text-formatting.md#popups) `entity:`,
`structure:`, `build:` and `multiblock:`. A mod adds its own with `FoliosLinks`, so books can link to the mod's
screens, its wiki, its content, or show it in a popup window.

```java
import net.ixdarklord.folios.api.client.FoliosLinks;

// [the Fire spell](spell:mymod:fireball): its cost under the pointer, the mod's spell screen when pressed.
FoliosLinks.register("spell", new FoliosLinks.Kind() {
    @Override
    public List<Component> tooltip(FoliosBook book, String target) {
        Spell spell = Spells.get(target);
        return spell == null ? List.of() : List.of(spell.name(), Component.literal(spell.cost() + " mana"));
    }

    @Override
    public boolean press(FoliosBook book, String target) {
        Minecraft.getInstance().setScreen(new SpellScreen(target));
        return true;
    }
});
```

Register while the client initialises. A scheme can be registered once; Folios's own are taken.

| Method of `Kind` | Default | What it does |
| --- | --- | --- |
| `tooltip(book, target)` | nothing | The lines shown under the pointer. |
| `popup(layout, target)` | none | A [popup window](#popups) shown in place of a tooltip. |
| `press(book, target)` | `false` | What pressing the link does; `true` when it did something. `false` pins its popup, if it has one. |
| `isOpen(book, target)` | `true` | Whether the reader can follow it yet. A link that can't shows "Not yet discovered" and does nothing. |

`target` is the part after `scheme:`.

## Popups

A `FoliosPopup` is a small window over the book: under the pointer while it is over the link, and pinned in place once
the link is pressed, until the reader presses off it, presses Esc or turns the page. It has a title, centred along its
top, and a body of its own drawing under it, through a `FoliosPainter` as a page is drawn.

```java
@Override
public FoliosPopup popup(FoliosLayout layout, String target) {
    Spell spell = Spells.get(target);
    if (spell == null) return null;
    return new FoliosPopup() {
        public Component title() {
            return spell.name();
        }

        public void draw(FoliosPainter painter, boolean pinned) {
            painter.item(spell.icon(), 4, 4);
            painter.text(Component.literal(spell.cost() + " mana"), 24, 8, painter.palette().text());
        }
    };
}
```

| Method | Default | What it does |
| --- | --- | --- |
| `title()` | required | Its title. |
| `width()`, `height()` | `120`, `80` | Its body's size, in page pixels. |
| `draw(painter, pinned)` | required | Draws the body from the painter's 0, 0. While `pinned`, it can be pressed, dragged and scrolled like a page (`painter.action`, `drag`, `scroll`). |

A popup is made once for each link of a laid-out book and kept while the book is, so it can hold a scene:
`layout.viewport(width, height)` makes one, closed with the book's layout.
