package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosRecipeLayout;
import net.ixdarklord.folios.internal.client.paint.DrawsAbove;
import net.ixdarklord.folios.internal.client.paint.GuiSurface;
import net.ixdarklord.folios.internal.client.paint.TooltipMore;
import net.ixdarklord.folios.internal.client.viewer.ViewerTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

final class RecipeCanvas implements FoliosRecipeLayout.Builder {
    private static final Map<RecipeDisplay, RecipeCanvas> CANVASES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int PAD = 5;
    private static final int BADGE_ROOM = 6;

    private final List<Part> parts = new ArrayList<>();
    private final List<Part> slots = new ArrayList<>();
    private final RecipeDisplay display;
    private final FoliosRecipeLayout<RecipeDisplay> layout;
    private final boolean drawsGui;
    private int width = -1;
    private int height = -1;
    private int reachX;
    private int reachY;
    private @Nullable SlotDisplay station;
    private boolean shapeless;

    @FunctionalInterface
    private interface Part {
        void draw(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved);
    }

    @SuppressWarnings("unchecked")
    private RecipeCanvas(RecipeDisplay display, FoliosRecipeLayout<?> layout) {
        this.display = display;
        this.layout = (FoliosRecipeLayout<RecipeDisplay>) layout;
        this.drawsGui = overrides(layout, "draw", RecipeDisplay.class, GuiGraphicsExtractor.class, double.class, double.class)
                || overrides(layout, "tooltip", RecipeDisplay.class, double.class, double.class);
    }

    private static boolean overrides(Object layout, String name, Class<?>... parameters) {
        try {
            return layout.getClass().getMethod(name, parameters).getDeclaringClass() != FoliosRecipeLayout.class;
        } catch (NoSuchMethodException failure) {
            return false;
        }
    }

    static <T extends RecipeDisplay> RecipeCanvas of(T display, FoliosRecipeLayout<T> layout) {
        RecipeCanvas canvas = CANVASES.get(display);
        if (canvas != null) return canvas;
        canvas = new RecipeCanvas(display, layout);
        layout.lay(display, canvas);
        if (canvas.station == null) canvas.station = display.craftingStation();
        CANVASES.put(display, canvas);
        return canvas;
    }

    void draw(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved, int left, int y, int right, int box) {
        int canvasWidth = Math.max(1, width > 0 ? width : reachX);
        int canvasHeight = Math.max(1, height > 0 ? height : reachY);
        List<ItemStack> stations = station == null ? List.of() : RecipePageElement.stacks(resolved, station);
        int innerWidth = right - left - PAD * 2;
        int top = y + PAD;
        int innerHeight = box - PAD * 2;
        float scale = Math.min(1, Math.min(innerWidth / (float) canvasWidth, innerHeight / (float) canvasHeight));
        int x = left + PAD + Math.round((innerWidth - canvasWidth * scale) / 2);
        int canvasY = top + Math.round((innerHeight - canvasHeight * scale) / 2);
        if (!stations.isEmpty() && x + canvasWidth * scale > right - 18 && canvasY < y + BADGE_ROOM + PAD) {
            top += BADGE_ROOM;
            innerHeight -= BADGE_ROOM;
            scale = Math.min(1, Math.min(innerWidth / (float) canvasWidth, innerHeight / (float) canvasHeight));
            x = left + PAD + Math.round((innerWidth - canvasWidth * scale) / 2);
            canvasY = top + Math.round((innerHeight - canvasHeight * scale) / 2);
        }
        painter.push();
        painter.translate(x, canvasY);
        painter.scale(scale);
        for (Part part : parts) part.draw(painter, resolved);
        int width = canvasWidth;
        int height = canvasHeight;
        if (drawsGui) GuiSurface.gui(painter, 0, 0, canvasWidth, canvasHeight, (graphics, mouseX, mouseY) -> {
            ViewerTheme.begin(painter);
            try {
                layout.draw(display, graphics, mouseX, mouseY);
                if (mouseX >= 0 && mouseY >= 0 && mouseX < width && mouseY < height) {
                    List<Component> lines = layout.tooltip(display, mouseX, mouseY);
                    if (!lines.isEmpty()) painter.tooltip(lines);
                }
            } finally {
                ViewerTheme.end();
            }
        });
        for (Part slot : slots) slot.draw(painter, resolved);
        if (shapeless) painter.text(Component.literal("~"), canvasWidth - 6, 0, painter.palette().muted());
        painter.pop();
        RecipePageElement.station(painter, stations, right, y);
    }

    private void reach(int x, int y) {
        reachX = Math.max(reachX, x);
        reachY = Math.max(reachY, y);
    }

    private FoliosRecipeLayout.Slot slot(int x, int y, Function<Map<SlotDisplay, List<ItemStack>>, List<ItemStack>> items, @Nullable String tag, boolean output) {
        SlotPart slot = new SlotPart(x, y, items, tag, output);
        slots.add(slot);
        int size = output ? 26 : 18;
        reach(x + size, y + size);
        return slot;
    }

    @Override
    public FoliosRecipeLayout.Builder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    @Override
    public FoliosRecipeLayout.Slot input(int x, int y, SlotDisplay items) {
        return this.slot(x, y, resolved -> RecipePageElement.stacks(resolved, items), RecipePageElement.tag(items), false);
    }

    @Override
    public FoliosRecipeLayout.Slot input(int x, int y, List<ItemStack> items) {
        List<ItemStack> copy = List.copyOf(items);
        return this.slot(x, y, resolved -> copy, null, false);
    }

    @Override
    public FoliosRecipeLayout.Slot output(int x, int y, SlotDisplay items) {
        return this.slot(x, y, resolved -> RecipePageElement.stacks(resolved, items), RecipePageElement.tag(items), true);
    }

    @Override
    public FoliosRecipeLayout.Slot output(int x, int y, List<ItemStack> items) {
        List<ItemStack> copy = List.copyOf(items);
        return this.slot(x, y, resolved -> copy, null, true);
    }

    @Override
    public FoliosRecipeLayout.Builder arrow(int x, int y) {
        parts.add((painter, resolved) -> RecipePageElement.arrow(painter, x + 7, y + 5.5F, painter.palette().accent(), 1));
        reach(x + 14, y + 11);
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder progress(int x, int y, int ticks) {
        float seconds = Math.max(1, ticks) / 20.0F;
        parts.add((painter, resolved) -> {
            FoliosPalette palette = painter.palette();
            RecipePageElement.arrow(painter, x + 7, y + 5.5F, FoliosPalette.mix(palette.panel(), palette.line(), 0.75F), 1);
            RecipePageElement.arrow(painter, x + 7, y + 5.5F, palette.accent(), (painter.time() % seconds) / seconds);
        });
        reach(x + 14, y + 11);
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder flame(int x, int y, int ticks) {
        float seconds = Math.max(1, ticks) / 20.0F;
        parts.add((painter, resolved) -> {
            RecipePageElement.flame(painter, x, y, false);
            int burnt = Math.round(8 * ((painter.time() % seconds) / seconds));
            if (burnt < 8) painter.clip(x, y + burnt, 8, 8 - burnt, () -> RecipePageElement.flame(painter, x, y, true));
        });
        reach(x + 8, y + 8);
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder gauge(int x, int y, int width, int height, float fill, int color, @Nullable Component tooltip) {
        float shown = Math.max(0, Math.min(1, fill));
        parts.add((painter, resolved) -> {
            FoliosPalette palette = painter.palette();
            painter.fill(x, y, x + width, y + height, FoliosPalette.mix(palette.line(), palette.text(), 0.45F));
            painter.fill(x + 1, y + 1, x + width - 1, y + height - 1, FoliosPalette.mix(palette.panel(), palette.line(), 0.45F));
            int level = Math.round((height - 2) * shown);
            if (level > 0) painter.fill(x + 1, y + height - 1 - level, x + width - 1, y + height - 1, color);
            if (tooltip != null && painter.hovered(x, y, width, height)) painter.tooltip(tooltip);
        });
        reach(x + width, y + height);
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder text(int x, int y, Component text, FoliosRecipeLayout.Align align) {
        parts.add((painter, resolved) -> {
            int textWidth = painter.font().width(text);
            int at = switch (align) {
                case LEFT -> x;
                case CENTER -> x - textWidth / 2;
                case RIGHT -> x - textWidth;
            };
            painter.text(text, at, y, painter.palette().muted());
        });
        reach(x, y + 9);
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder texture(Identifier texture, int x, int y, int width, int height) {
        parts.add((painter, resolved) -> painter.texture(texture, x, y, width, height, 0, 0, 1, 1, -1));
        reach(x + width, y + height);
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder texture(Identifier texture, int x, int y, int u, int v, int width, int height, int textureWidth, int textureHeight) {
        float u0 = u / (float) textureWidth;
        float v0 = v / (float) textureHeight;
        float u1 = (u + width) / (float) textureWidth;
        float v1 = (v + height) / (float) textureHeight;
        parts.add((painter, resolved) -> painter.texture(texture, x, y, width, height, u0, v0, u1, v1, -1));
        reach(x + width, y + height);
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder tooltip(int x, int y, int width, int height, List<Component> lines) {
        List<Component> copy = List.copyOf(lines);
        parts.add((painter, resolved) -> {
            if (painter.hovered(x, y, width, height)) painter.tooltip(copy);
        });
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder station(SlotDisplay items) {
        this.station = items;
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder shapeless() {
        this.shapeless = true;
        return this;
    }

    @Override
    public FoliosRecipeLayout.Builder draw(FoliosRecipeLayout.Draw draw) {
        parts.add((painter, resolved) -> draw.draw(painter, painter.time()));
        return this;
    }

    private static final class SlotPart implements Part, FoliosRecipeLayout.Slot {
        private final int x;
        private final int y;
        private final Function<Map<SlotDisplay, List<ItemStack>>, List<ItemStack>> items;
        private final @Nullable String tag;
        private final boolean output;
        private final List<Component> lines = new ArrayList<>();
        private float chance = 1;
        private boolean bare;

        private SlotPart(int x, int y, Function<Map<SlotDisplay, List<ItemStack>>, List<ItemStack>> items, @Nullable String tag, boolean output) {
            this.x = x;
            this.y = y;
            this.items = items;
            this.tag = tag;
            this.output = output;
        }

        @Override
        public void draw(FoliosPainter painter, Map<SlotDisplay, List<ItemStack>> resolved) {
            if (!bare) {
                if (output) Shapes.resultSlot(painter, x, y, 26);
                else Shapes.slot(painter, x, y);
            }
            int inset = output ? 4 : 0;
            boolean hovered = RecipePageElement.items(painter, items.apply(resolved), tag, x + inset, y + inset, true);
            if (chance < 1) {
                Component percent = Component.literal(String.format(Locale.ROOT, "%d%%", Math.round(chance * 100)));
                DrawsAbove.above(painter, () -> {
                    painter.push();
                    painter.translate(x + inset + 1, y + inset + 1);
                    painter.scale(0.5F);
                    int at = 32 - painter.font().width(percent);
                    painter.text(percent, at + 1, 1, 0xFF2B2B2B);
                    painter.text(percent, at, 0, 0xFFFFFFFF);
                    painter.pop();
                });
            }
            if (!hovered) return;
            List<Component> more = new ArrayList<>(lines);
            if (chance < 1) {
                more.add(Component.translatableWithFallback("folios.recipe.chance", "%s%% chance", Math.round(chance * 100)).withStyle(ChatFormatting.GOLD));
            }
            if (!more.isEmpty()) TooltipMore.more(painter, more);
        }

        @Override
        public FoliosRecipeLayout.Slot tooltip(Component... lines) {
            this.lines.addAll(List.of(lines));
            return this;
        }

        @Override
        public FoliosRecipeLayout.Slot chance(float chance) {
            this.chance = Math.max(0, Math.min(1, chance));
            return this;
        }

        @Override
        public FoliosRecipeLayout.Slot bare() {
            this.bare = true;
            return this;
        }
    }
}
