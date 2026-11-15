package net.ixdarklord.folios.internal.client.viewer.rei;

import me.shedaniel.math.Point;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.ixdarklord.folios.internal.client.viewer.ViewerBridge;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ReiBridge implements ViewerBridge {
    static final String NAME = "rei";

    private Map<Identifier, Display> index = Map.of();
    private int indexed = -1;
    private final Map<Identifier, ReiRecipe<?>> made = new HashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ItemStack hovered() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return ItemStack.EMPTY;
        int x = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        int y = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        EntryStack<?> focused = ScreenRegistry.getInstance().getFocusedStack(minecraft.screen, new Point(x, y));
        return item(focused);
    }

    static ItemStack item(@Nullable EntryStack<?> entry) {
        if (entry == null || entry.isEmpty() || entry.getType() != VanillaEntryTypes.ITEM) return ItemStack.EMPTY;
        return entry.<ItemStack>castValue();
    }

    @Override
    public @Nullable ViewerRecipe recipe(Identifier id) {
        DisplayRegistry registry = DisplayRegistry.getInstance();
        if (registry.size() != indexed) {
            Map<Identifier, Display> found = new HashMap<>();
            for (List<Display> displays : registry.getAll().values()) {
                for (Display display : displays) display.getDisplayLocation().ifPresent(location -> found.putIfAbsent(location, display));
            }
            index = found;
            indexed = registry.size();
            made.clear();
        }
        Display display = index.get(id);
        if (display == null) return null;
        return made.computeIfAbsent(id, key -> ReiRecipe.of(display));
    }
}
