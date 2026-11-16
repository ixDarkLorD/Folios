package net.ixdarklord.folios.internal.client.viewer.rei;

import com.mojang.blaze3d.platform.Window;
import me.shedaniel.math.Point;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.ixdarklord.folios.internal.client.viewer.ViewerBridge;
import net.ixdarklord.folios.internal.client.viewer.ViewerRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ReiBridge implements ViewerBridge {
    static final String NAME = "rei";

    private Map<ResourceLocation, Display> index = Map.of();
    private int indexed = -1;
    private final Map<ResourceLocation, ReiRecipe> made = new HashMap<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ItemStack hovered() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return ItemStack.EMPTY;
        Window window = minecraft.getWindow();
        int x = (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int y = (int) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        EntryStack<?> focused = ScreenRegistry.getInstance().getFocusedStack(minecraft.screen, new Point(x, y));
        return item(focused);
    }

    static ItemStack item(@Nullable EntryStack<?> entry) {
        if (entry == null || entry.isEmpty() || entry.getType() != VanillaEntryTypes.ITEM) return ItemStack.EMPTY;
        return entry.<ItemStack>castValue();
    }

    @Override
    public @Nullable ViewerRecipe recipe(ResourceLocation id) {
        DisplayRegistry registry = DisplayRegistry.getInstance();
        if (registry.displaySize() != indexed) {
            Map<ResourceLocation, Display> found = new HashMap<>();
            for (List<Display> displays : registry.getAll().values()) {
                for (Display display : displays) display.getDisplayLocation().ifPresent(location -> found.putIfAbsent(location, display));
            }
            index = found;
            indexed = registry.displaySize();
            made.clear();
        }
        Display display = index.get(id);
        if (display == null) return null;
        return made.computeIfAbsent(id, key -> ReiRecipe.of(display));
    }
}
