package net.ixdarklord.folios.api.client.page;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * How the {@code recipe} page element draws one kind of recipe, such as a mod's machine. It works like a recipe
 * viewer's category, with no recipe viewer needed:
 * <ul>
 *     <li>{@link #lay} places the recipe's slots, and any of the book's own arrows, flames, gauges, text and pictures,
 *     on a canvas of its own size, as a viewer category's {@code setRecipe} does. The book draws them in its own look,
 *     in screen books and books held in the hands.</li>
 *     <li>{@link #draw} draws anything else with Minecraft's {@link GuiGraphics}, every frame, as a viewer category's
 *     {@code draw} does: over the layout's pictures, arrows and text and under its slots.</li>
 *     <li>{@link #tooltip} gives lines for wherever the pointer is on the canvas.</li>
 * </ul>
 * Register one for a recipe class with {@link FoliosRecipeLayouts#register}, while the client initialises. A layout
 * registered for one of Minecraft's recipe classes replaces Folios's own drawing of it.
 * <pre>{@code
 * FoliosRecipeLayouts.register(CrusherRecipe.class, (recipe, layout) -> {
 *     layout.size(82, 44);
 *     layout.input(0, 13, recipe.input());
 *     layout.progress(27, 17, recipe.ticks());
 *     layout.output(56, 9, recipe.result());
 *     layout.text(41, 36, Component.literal(recipe.energy() + " FE"), FoliosRecipeLayout.Align.CENTER);
 * });
 * }</pre>
 * Or, as a class, drawing with {@code GuiGraphics}:
 * <pre>{@code
 * public final class CrusherLayout implements FoliosRecipeLayout<CrusherRecipe> {
 *     private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/crusher.png");
 *
 *     @Override
 *     public void lay(CrusherRecipe recipe, Builder layout) {
 *         layout.size(82, 44);
 *         layout.input(0, 13, recipe.input()).bare();
 *         layout.output(56, 9, recipe.result()).bare();
 *     }
 *
 *     @Override
 *     public void draw(CrusherRecipe recipe, GuiGraphics graphics, double mouseX, double mouseY) {
 *         graphics.blit(BACKGROUND, 0, 0, 0, 0, 82, 44, 128, 128);
 *         graphics.drawString(Minecraft.getInstance().font, recipe.energy() + " FE", 28, 36, 0xFF404040, false);
 *     }
 * }
 * }</pre>
 * Coordinates are in the canvas's own pixels, from its top-left corner. The canvas is centred in the recipe's frame
 * and drawn smaller when it is larger than the frame (about 100 × 50 on a page), never larger.
 *
 * @param <T> the recipe class this layout draws
 */
@FunctionalInterface
public interface FoliosRecipeLayout<T extends Recipe<?>> {
    /**
     * Lays out one recipe. Called once for each recipe, the first time it is drawn; what is laid out is kept and drawn
     * every frame after. Throwing shows "This recipe couldn't be drawn" in the recipe's frame and logs the error.
     */
    void lay(T recipe, Builder layout);

    /**
     * Draws the recipe with Minecraft's GUI drawing, every frame, in the canvas's own pixels: the graphics are moved
     * and scaled to the canvas and clipped to it. Drawn over the layout's pictures, arrows, flames, gauges and text,
     * and under its slots. A tooltip set on the graphics is shown in the book's own style.
     * <p>
     * Only screen books draw with GUI graphics; a book held in the hands draws what {@link #lay} placed, without this.
     *
     * @param mouseX where the pointer is on the canvas, or far outside it
     * @param mouseY where the pointer is on the canvas, or far outside it
     */
    default void draw(T recipe, GuiGraphics graphics, double mouseX, double mouseY) {}

    /**
     * Lines shown where the pointer is on the canvas, as a viewer category's tooltip; none by default. Called in
     * screen books only, every frame the pointer is over the canvas.
     */
    default List<Component> tooltip(T recipe, double mouseX, double mouseY) {
        return List.of();
    }

    /** Where a line of text sits against its x. */
    enum Align { LEFT, CENTER, RIGHT }

    /** Draws something of a layout's own on the canvas, every frame. */
    @FunctionalInterface
    interface Draw {
        /**
         * @param painter draws on the canvas, in its own pixels
         * @param seconds how long the page has been open, for animation
         */
        void draw(FoliosPainter painter, float seconds);
    }

    /** What a layout places on its canvas. Things are drawn in the order they are added. */
    interface Builder {
        /** The canvas's size. Without it, the canvas is as large as what is placed on it reaches. */
        Builder size(int width, int height);

        /**
         * An ingredient slot, 18 × 18 with the item 1 pixel in, drawn as the book's slots. Several items are shown in
         * turn, marked as a list, with all of them in the slot's tooltip.
         */
        Slot input(int x, int y, Ingredient items);

        /** An ingredient slot holding these items. */
        Slot input(int x, int y, List<ItemStack> items);

        /** A result slot, 26 × 26 with the item 5 pixels in, drawn as the book's result slots, with its amount shown. */
        Slot output(int x, int y, ItemStack item);

        /** A result slot holding these items, shown in turn. */
        Slot output(int x, int y, List<ItemStack> items);

        /** An arrow pointing right, 14 × 11, in the book's accent colour. */
        Builder arrow(int x, int y);

        /** An arrow, 14 × 11, that fills from the left over this many ticks, again and again, as a machine works. */
        Builder progress(int x, int y, int ticks);

        /** A flame, 8 × 8, that burns down over this many ticks, again and again, as fuel does. */
        Builder flame(int x, int y, int ticks);

        /**
         * A gauge, for energy, fluid or heat: a well filled from the bottom.
         *
         * @param fill    how full, 0 to 1
         * @param color   the fill's colour, ARGB
         * @param tooltip shown when the gauge is hovered, or {@code null}
         */
        Builder gauge(int x, int y, int width, int height, float fill, int color, @Nullable Component tooltip);

        /** A line of text, in the book's muted text colour unless the text has its own. */
        Builder text(int x, int y, Component text, Align align);

        /** A whole texture, stretched to this size. */
        Builder texture(ResourceLocation texture, int x, int y, int width, int height);

        /** Part of a texture: {@code width} × {@code height} pixels from ({@code u}, {@code v}) of a texture this large. */
        Builder texture(ResourceLocation texture, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight);

        /** Lines shown when this part of the canvas is hovered. */
        Builder tooltip(int x, int y, int width, int height, List<Component> lines);

        /**
         * The block the recipe is made at, shown as a badge on the frame's corner. The recipe's own
         * {@code getToastSymbol()} is used when this isn't called; an empty list shows none.
         */
        Builder station(List<ItemStack> items);

        /** Marks the recipe as shapeless: its ingredients can go anywhere. */
        Builder shapeless();

        /**
         * Something of the layout's own, drawn with the book's painter on the canvas, in the order placed. Unlike
         * {@link FoliosRecipeLayout#draw}, this draws in books held in the hands too.
         */
        Builder draw(Draw draw);
    }

    /** A slot that was placed, to add to. */
    interface Slot {
        /** Adds lines to the slot's tooltip, under the item's own. */
        Slot tooltip(Component... lines);

        /** The chance the slot's item comes out, 0 to 1: shown in its corner and tooltip when below 1. */
        Slot chance(float chance);

        /** Draws the item alone, without the slot behind it. */
        Slot bare();
    }
}
