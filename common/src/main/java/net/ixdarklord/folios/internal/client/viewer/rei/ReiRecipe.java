package net.ixdarklord.folios.internal.client.viewer.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.widgets.Panel;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class ReiRecipe<T extends Display> implements ViewerRecipe {
    private final T display;
    private final CategoryRegistry.CategoryConfiguration<T> configuration;
    private final DisplayCategory<T> category;
    private final int width;
    private final int height;
    private List<Widget> widgets = List.of();
    private int placedX = Integer.MIN_VALUE;
    private int placedY = Integer.MIN_VALUE;

    private ReiRecipe(T display, CategoryRegistry.CategoryConfiguration<T> configuration) {
        this.display = display;
        this.configuration = configuration;
        this.category = configuration.getCategory();
        this.width = category.getDisplayWidth(display);
        this.height = category.getDisplayHeight();
    }

    private void place(int x, int y) {
        if (x == placedX && y == placedY) return;
        List<Widget> made = new ArrayList<>();
        for (Widget widget : category.setupDisplay(display, new Rectangle(x, y, width, height))) if (!(widget instanceof Panel)) made.add(widget);
        widgets = made;
        placedX = x;
        placedY = y;
    }

    @SuppressWarnings("unchecked")
    static @Nullable ReiRecipe<?> of(Display display) {
        return CategoryRegistry.getInstance().tryGet(display.getCategoryIdentifier())
                .map(configuration -> new ReiRecipe<>(display, (CategoryRegistry.CategoryConfiguration<Display>) configuration)).orElse(null);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.drawAt(graphics, 0, 0, mouseX, mouseY);
    }

    @Override
    public void drawAt(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        this.place(x, y);
        float delta = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        GuiGraphics raw = graphics.raw();
        Widget.pushMouse(new Point(mouseX, mouseY));
        try {
            for (Widget widget : widgets) widget.render(raw, mouseX, mouseY, delta);
        } finally {
            Widget.popMouse();
        }
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        for (Widget widget : widgets) {
            if (!(widget instanceof Slot slot) || !widget.containsMouse(mouseX, mouseY)) continue;
            EntryStack<?> entry = slot.getCurrentEntry();
            if (entry.isEmpty()) return false;
            return ViewSearchBuilder.builder().addRecipesFor(entry).open();
        }
        return false;
    }

    @Override
    public Component title() {
        return category.getTitle();
    }

    @Override
    public List<ItemStack> outputs() {
        return stacks(display.getOutputEntries());
    }

    @Override
    public List<ItemStack> stations() {
        List<ItemStack> stations = stacks(configuration.getWorkstations());
        return stations.size() > 4 ? stations.subList(0, 4) : stations;
    }

    private static List<ItemStack> stacks(List<EntryIngredient> ingredients) {
        List<ItemStack> stacks = new ArrayList<>();
        for (EntryIngredient ingredient : ingredients) {
            for (EntryStack<?> entry : ingredient) {
                ItemStack stack = ReiBridge.item(entry);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                    break;
                }
            }
        }
        return stacks;
    }
}
