package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Requirements {
    static final int HEADING = 11;
    static final int SLOT = 18;

    record Need(BlockChoice choice, int count) {
        ItemStack stack(float time) {
            return choice.stack(time, count);
        }
    }

    private final List<Need> needs = new ArrayList<>();
    private final int total;
    private int page;
    private boolean open;
    private int scroll;
    private static final int PAD = 3;
    private static final int BAR = 3;

    Requirements(Collection<BlockChoice> blocks) {
        Map<Object, BlockChoice> kinds = new LinkedHashMap<>();
        Map<Object, Integer> counts = new LinkedHashMap<>();
        for (BlockChoice block : blocks) {
            int items = block.items();
            if (items <= 0 || block.stack(0, 1).isEmpty()) continue;
            Object key = block.isTag() ? block.key() : block.stack(0, 1).getItem();
            kinds.putIfAbsent(key, block);
            counts.merge(key, items, Integer::sum);
        }
        int sum = 0;
        for (Map.Entry<Object, Integer> count : counts.entrySet()) {
            needs.add(new Need(kinds.get(count.getKey()), count.getValue()));
            sum += count.getValue();
        }
        needs.sort(Comparator.comparingInt(Need::count).reversed());
        this.total = sum;
    }

    boolean isEmpty() {
        return needs.isEmpty();
    }

    int total() {
        return total;
    }

    boolean paged(int perPage) {
        return needs.size() > perPage;
    }

    List<Need> shown(int perPage) {
        int pages = Math.max(1, (needs.size() + perPage - 1) / perPage);
        page = Math.floorMod(page, pages);
        return needs.subList(page * perPage, Math.min(needs.size(), (page + 1) * perPage));
    }

    boolean isOpen() {
        return open;
    }

    void clasp(FoliosPainter painter, int x, int y) {
        Component tooltip = open ? Component.translatableWithFallback("folios.requires.hide", "Hide the requirement list")
                : Component.translatableWithFallback("folios.requires.show", "Requirement list · %s", total);
        boolean over = painter.action(x, y - 1, Shapes.BUTTON, Shapes.BUTTON + 1, tooltip, () -> open = !open);
        Shapes.button(painter, x, y, Shapes.Icon.BLOCKS, over || open);
    }

    void popup(FoliosPainter painter, int x, int y, int width, int height) {
        FoliosPalette palette = painter.palette();
        Shapes.panel(painter, x, y, x + width, y + height);
        int size = Shapes.BUTTON;
        int closeX = x + width - size - 2;
        boolean over = painter.action(closeX, y + 1, size, size + 1, Component.translatableWithFallback("folios.requires.hide", "Hide the requirement list"), () -> open = false);
        Shapes.button(painter, closeX, y + 2, Shapes.Icon.CLOSE, over);
        painter.text(Component.translatableWithFallback("folios.requires.list", "Requirement list:"), x + PAD + 1, y + 4, palette.heading());
        painter.fill(x + PAD, y + 15, x + width - PAD, y + 16, FoliosPalette.mix(palette.line(), palette.text(), 0.3F));
        int top = y + 18;
        int rows = Math.max(1, (y + height - PAD + 1 - top) / SLOT);
        int columns = Math.max(1, (width - 2) / SLOT);
        boolean scrolls = needs.size() > columns * rows;
        if (scrolls) columns = Math.max(1, (width - 2 - BAR - 1) / SLOT);
        int gridLeft = x + Math.max(1, (width - columns * SLOT - (scrolls ? BAR + 1 : 0)) / 2);
        int allRows = (needs.size() + columns - 1) / columns;
        int most = Math.max(0, allRows - rows);
        painter.scroll(x, top, width, rows * SLOT, notches -> scroll = Math.max(0, Math.min(most, scroll - (int) Math.signum(notches))));
        scroll = Math.max(0, Math.min(most, scroll));
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (scroll + row) * columns + column;
                if (index >= needs.size()) break;
                slot(painter, needs.get(index), gridLeft + column * SLOT, top + row * SLOT);
            }
        }
        if (most == 0) return;
        this.scrollBar(painter, gridLeft + columns * SLOT + 1, top, rows * SLOT, rows, allRows, most, painter.hovered(x, top, width, rows * SLOT));
    }

    private void scrollBar(FoliosPainter painter, int barX, int top, int height, int rows, int allRows, int most, boolean active) {
        FoliosPalette palette = painter.palette();
        int groove = FoliosPalette.mix(palette.panel(), palette.line(), 0.7F);
        int quiet = FoliosPalette.mix(palette.panel(), palette.line(), 0.95F);
        int lit = palette.accent();
        painter.fill(barX + 1, top, barX + 2, top + 1, scroll > 0 ? lit : quiet);
        painter.fill(barX, top + 1, barX + BAR, top + 2, scroll > 0 ? lit : quiet);
        painter.fill(barX, top + height - 2, barX + BAR, top + height - 1, scroll < most ? lit : quiet);
        painter.fill(barX + 1, top + height - 1, barX + 2, top + height, scroll < most ? lit : quiet);
        int trackTop = top + 3;
        int track = height - 6;
        painter.fill(barX, trackTop, barX + BAR, trackTop + track, groove);
        painter.fill(barX, trackTop, barX + 1, trackTop + track, FoliosPalette.mix(groove, 0xFF000000, 0.15F));
        int thumb = Math.max(7, track * rows / allRows);
        int thumbY = trackTop + (track - thumb) * scroll / most;
        int face = FoliosPalette.mix(palette.line(), palette.accent(), active ? 0.85F : 0.55F);
        painter.fill(barX, thumbY, barX + BAR, thumbY + thumb, face);
        painter.fill(barX, thumbY, barX + 1, thumbY + thumb, FoliosPalette.mix(face, 0xFFFFFFFF, 0.35F));
        painter.fill(barX + BAR - 1, thumbY, barX + BAR, thumbY + thumb, FoliosPalette.mix(face, 0xFF000000, 0.25F));
        painter.fill(barX, thumbY + thumb - 1, barX + BAR, thumbY + thumb, FoliosPalette.mix(face, 0xFF000000, 0.3F));
        int grip = FoliosPalette.mix(face, 0xFF000000, 0.35F);
        int middle = thumbY + thumb / 2;
        for (int line = -2; line <= 2; line += 2) painter.fill(barX, middle + line, barX + BAR, middle + line + 1, grip);
    }

    static void slot(FoliosPainter painter, Need need, int x, int y) {
        Shapes.slot(painter, x, y);
        painter.item(need.stack(painter.time()), x + 1, y + 1);
        if (need.choice().isTag() && painter.hovered(x + 1, y + 1, 16, 16)) {
            painter.tooltip(List.of(need.choice().name(painter.time()), Component.literal("×" + need.count()).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    void stepper(FoliosPainter painter, int x, int y, boolean forward) {
        int size = Shapes.BUTTON;
        boolean over = painter.action(x, y - 1, size, size + 1, forward ? Component.translatableWithFallback("folios.next", "Next page")
                : Component.translatableWithFallback("folios.previous", "Previous page"), () -> page += forward ? 1 : -1);
        Shapes.button(painter, x, y, forward ? Shapes.Icon.NEXT : Shapes.Icon.PREVIOUS, over);
    }
}
