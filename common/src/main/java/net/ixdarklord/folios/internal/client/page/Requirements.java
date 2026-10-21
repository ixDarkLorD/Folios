package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// What a build takes: one stack per kind of block, its count on it, the most needed first. Shown a number of slots at
// a time; when there are more kinds than that, clasps step through them a page at a time.
final class Requirements {
    /** How tall the list's heading is, and a slot. */
    static final int HEADING = 11;
    static final int SLOT = 18;

    private final List<ItemStack> items = new ArrayList<>();
    private final int total;
    private int page;

    Requirements(Map<BlockState, Integer> counts) {
        int sum = 0;
        for (Map.Entry<BlockState, Integer> entry : counts.entrySet()) {
            ItemStack stack = new ItemStack(entry.getKey().getBlock().asItem(), entry.getValue());
            if (stack.isEmpty()) continue;
            // Blocks that are the same item (a slab's halves, a door's) are counted together.
            ItemStack same = items.stream().filter(item -> ItemStack.isSameItem(item, stack)).findFirst().orElse(null);
            if (same != null) same.grow(stack.getCount());
            else items.add(stack);
            sum += stack.getCount();
        }
        items.sort(Comparator.comparingInt(ItemStack::getCount).reversed());
        this.total = sum;
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    int total() {
        return total;
    }

    int kinds() {
        return items.size();
    }

    /** How many rows a list of {@code columns} across takes, up to {@code maxRows}. */
    int rows(int columns, int maxRows) {
        return Math.min(maxRows, (items.size() + columns - 1) / columns);
    }

    /** Whether there are more kinds than {@code perPage} shown at once. */
    boolean paged(int perPage) {
        return items.size() > perPage;
    }

    /** The stacks on the page of the list shown now. */
    List<ItemStack> shown(int perPage) {
        int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        page = Math.floorMod(page, pages);
        return items.subList(page * perPage, Math.min(items.size(), (page + 1) * perPage));
    }

    /** Draws the heading, centred on {@code middle}: "Blocks needed · 37", and which page of how many when paged. */
    void heading(GuidePainter painter, int middle, int y, int perPage) {
        GuidePalette palette = painter.palette();
        int pages = (items.size() + perPage - 1) / perPage;
        Component text = Component.translatableWithFallback("folios.requires", "Blocks needed").append(" · " + total);
        if (pages > 1) text = text.copy().append("  (" + (page + 1) + "/" + pages + ")");
        painter.centeredText(text, middle, y, palette.muted());
    }

    /** A slot and its stack, at (x, y). */
    static void slot(GuidePainter painter, ItemStack stack, int x, int y) {
        Shapes.slot(painter, x, y);
        painter.item(stack, x + 1, y + 1);
    }

    /** A clasp that steps the list a page back or on. */
    void stepper(GuidePainter painter, int x, int y, boolean forward) {
        int size = Shapes.BUTTON;
        boolean over = painter.action(x, y - 1, size, size + 1, forward ? Component.translatableWithFallback("folios.next", "Next page")
                : Component.translatableWithFallback("folios.previous", "Previous page"), () -> page += forward ? 1 : -1);
        Shapes.button(painter, x, y, forward ? Shapes.Icon.NEXT : Shapes.Icon.PREVIOUS, over);
    }

    /**
     * Draws the list as a grid of {@code columns} by up to {@code maxRows} centred on {@code middle} under its heading,
     * with clasps at its sides when it is paged.
     */
    void grid(GuidePainter painter, int middle, int y, int columns, int maxRows) {
        int perPage = columns * maxRows;
        this.heading(painter, middle, y, perPage);
        List<ItemStack> shown = this.shown(perPage);
        int across = Math.min(columns, shown.size());
        int left = middle - across * SLOT / 2;
        int top = y + HEADING;
        for (int index = 0; index < shown.size(); index++) {
            slot(painter, shown.get(index), left + index % columns * SLOT, top + index / columns * SLOT);
        }
        if (this.paged(perPage)) {
            int rows = this.rows(columns, maxRows);
            int arrowY = top + rows * SLOT / 2 - Shapes.BUTTON / 2;
            this.stepper(painter, left - Shapes.BUTTON - 3, arrowY, false);
            this.stepper(painter, middle + columns * SLOT / 2 + 3, arrowY, true);
        }
    }
}
