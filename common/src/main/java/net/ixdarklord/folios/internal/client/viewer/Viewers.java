package net.ixdarklord.folios.internal.client.viewer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Viewers {
    private static final List<ViewerBridge> LIVE = new CopyOnWriteArrayList<>();

    private Viewers() {}

    public static void add(ViewerBridge bridge) {
        LIVE.removeIf(live -> live.name().equals(bridge.name()));
        LIVE.add(bridge);
    }

    public static void remove(String name) {
        LIVE.removeIf(live -> live.name().equals(name));
    }

    public static boolean any() {
        return !LIVE.isEmpty();
    }

    public static ItemStack hovered() {
        for (ViewerBridge bridge : LIVE) {
            try {
                ItemStack stack = bridge.hovered();
                if (!stack.isEmpty()) return stack;
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean key(InputConstants.Key key, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (ViewerBridge bridge : LIVE) {
            try {
                if (bridge.key(key, stack)) return true;
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return false;
    }

    public static @Nullable ViewerRecipe recipe(Identifier id) {
        for (ViewerBridge bridge : LIVE) {
            ViewerRecipe recipe = bridge.recipe(id);
            if (recipe != null) return recipe;
        }
        return null;
    }
}
