# Recipe layouts

A mod's own recipes, such as its machines, can be drawn by the [recipe](../elements/recipe.md) element with no recipe
viewer installed. A recipe layout works like a recipe viewer's category:

- `lay` places the recipe's slots, and any of the book's own arrows, flames, gauges, text and pictures, on a canvas of
  its own size, as a category's `setRecipe` does. The book draws them in its own look.
- `draw` draws anything else with Minecraft's `GuiGraphicsExtractor`, every frame, as a category's `draw` does.
- `tooltip` gives lines for wherever the pointer is on the canvas.

Register a layout with `FoliosRecipeLayouts` while the client initialises, for the type of `RecipeDisplay` your
recipes give. The client knows a recipe only by the displays the server sends for it, so your recipe's `display()`
must return a display of your own type.

```java
import net.ixdarklord.folios.api.client.page.FoliosRecipeLayout;
import net.ixdarklord.folios.api.client.page.FoliosRecipeLayouts;
import net.minecraft.network.chat.Component;

FoliosRecipeLayouts.register(CrusherDisplay.class, (display, layout) -> {
    layout.size(96, 44);
    layout.input(0, 4, display.input());
    layout.flame(5, 26, display.burnTicks());
    layout.progress(22, 7, display.ticks());
    layout.output(40, 0, display.result());
    layout.output(70, 4, display.bonus()).chance(0.25F);
    layout.gauge(90, 0, 6, 26, display.energy() / 10000F, 0xFFD94A3A, Component.literal(display.energy() + " FE"));
    layout.text(29, 34, Component.literal(display.ticks() / 20F + "s"), FoliosRecipeLayout.Align.CENTER);
});
```

A layout that only places things can be a lambda, as above. One that draws for itself is a class:

```java
public final class CrusherLayout implements FoliosRecipeLayout<CrusherDisplay> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("mymod", "textures/gui/crusher.png");

    @Override
    public void lay(CrusherDisplay display, Builder layout) {
        layout.size(82, 44);
        layout.input(0, 13, display.input()).bare();
        layout.output(56, 9, display.result()).bare();
    }

    @Override
    public void draw(CrusherDisplay display, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0, 0, 82, 44, 128, 128);
        graphics.text(Minecraft.getInstance().font, display.energy() + " FE", 28, 36, 0xFF404040, false);
    }

    @Override
    public List<Component> tooltip(CrusherDisplay display, double mouseX, double mouseY) {
        boolean overArrow = mouseX >= 26 && mouseX < 48 && mouseY >= 15 && mouseY < 30;
        return overArrow ? List.of(Component.literal("Crushing")) : List.of();
    }
}

FoliosRecipeLayouts.register(CrusherDisplay.class, new CrusherLayout());
```

`lay` runs once for each recipe, the first time it is drawn. Coordinates are in the canvas's own pixels, from its
top-left corner. The canvas is centred in the recipe's frame (about 100 × 50 on a page) and drawn smaller when it is
larger, never larger. A layout that throws shows "This recipe couldn't be drawn" in its frame, and the error is logged.

!!! note mc-121 mc-120 "On 1.21 and 1.20.1"
    Layouts are registered for a recipe class instead of a display type, and get the recipe itself:
    `FoliosRecipeLayouts.register(CrusherRecipe.class, (recipe, layout) -> ...)`. Slots take an `Ingredient` or a list
    of `ItemStack`s, results an `ItemStack`, `station` a list of `ItemStack`s (the recipe's `getToastSymbol()` by
    default), and `draw` gets a `GuiGraphics`.

## What a layout can place

| Method | What it draws |
| --- | --- |
| `size(width, height)` | The canvas's size. Without it, the canvas reaches as far as what is placed on it. |
| `input(x, y, items)` | An ingredient slot, 18 × 18, with the item 1 pixel in. Takes a `SlotDisplay` or a list of `ItemStack`s. |
| `output(x, y, items)` | A result slot, 26 × 26, with the item 5 pixels in, amounts shown. |
| `arrow(x, y)` | An arrow pointing right, 14 × 11. |
| `progress(x, y, ticks)` | An arrow that fills over this many ticks, again and again. |
| `flame(x, y, ticks)` | A flame, 8 × 8, that burns down over this many ticks, again and again. |
| `gauge(x, y, width, height, fill, color, tooltip)` | A well filled from the bottom, for energy, fluid or heat. `fill` is 0 to 1. |
| `text(x, y, text, align)` | A line of text, `LEFT`, `CENTER` or `RIGHT` of its x, in the book's muted colour unless styled. |
| `texture(id, x, y, width, height)` | A whole texture, stretched to this size. |
| `texture(id, x, y, u, v, width, height, textureWidth, textureHeight)` | Part of a texture. |
| `tooltip(x, y, width, height, lines)` | Lines shown when this part of the canvas is hovered. |
| `station(items)` | The block the recipe is made at, shown as a badge on the frame's corner. The display's own `craftingStation()` is used otherwise. |
| `shapeless()` | Marks the recipe as shapeless. |
| `draw((painter, seconds) -> ...)` | Anything else, drawn with the book's [painter](page-elements.md) on the canvas, every frame. |

A slot's items cycle when there are several, with the mark of a tag (`#`) or a list in its corner and every item in
its tooltip, as in Folios's own recipes. `input` and `output` return the slot, to add to:

| Method | What it does |
| --- | --- |
| `tooltip(lines...)` | Adds lines under the item's own tooltip. |
| `chance(0.25F)` | The chance the item comes out: shown in the slot's corner and its tooltip. |
| `bare()` | Draws the item without the slot behind it. |

Things are drawn in the order they are placed, so place a background texture first.

## Drawing with GUI graphics

`draw` gets Minecraft's GUI graphics already moved and scaled to the canvas and clipped to it, so it draws in the
canvas's own pixels, as a viewer category's `draw` does, with the pointer's place on the canvas. It is drawn over what
`lay` placed (pictures, arrows, flames, gauges, text) and under the slots. A tooltip set on the graphics shows in the
book's own style, as do the lines `tooltip` gives.

Only screen books draw with GUI graphics. A book held in the hands is drawn into the world, so it draws what `lay`
placed (and the builder's own `draw`, which uses the book's painter) without calling `draw` or `tooltip`: place what
matters most through `lay`.

## Minecraft's own recipes

A layout registered for one of Minecraft's display types, such as `StonecutterRecipeDisplay`, replaces Folios's own
drawing of it.

## With a recipe viewer

Recipes are always drawn by the book, through layouts like these, even when JEI or REI is installed. With JEI, its
show-recipe and show-uses keys work over a layout's slots, and the bookmark button bookmarks the recipe in JEI.
