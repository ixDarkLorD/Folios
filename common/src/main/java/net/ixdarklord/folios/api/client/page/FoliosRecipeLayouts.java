package net.ixdarklord.folios.api.client.page;

import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link FoliosRecipeLayout}s the {@code recipe} page element draws recipes with, by recipe class. Recipes without
 * one are drawn by Folios itself: crafting, cooking, stonecutting and smithing as such, and any other as its
 * ingredients, an arrow and its result.
 */
public final class FoliosRecipeLayouts {
    private static final Map<Class<?>, FoliosRecipeLayout<?>> LAYOUTS = new ConcurrentHashMap<>();

    private FoliosRecipeLayouts() {}

    /**
     * Draws recipes of this class (or a subclass without a layout of its own) with this layout. Register while the
     * client initialises; registering a class again replaces its layout.
     */
    public static <T extends Recipe<?>> void register(Class<T> type, FoliosRecipeLayout<T> layout) {
        LAYOUTS.put(type, layout);
    }

    /** The layout a recipe is drawn with, or {@code null} when Folios draws it itself. */
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> @Nullable FoliosRecipeLayout<T> get(T recipe) {
        for (Class<?> type = recipe.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            FoliosRecipeLayout<?> layout = LAYOUTS.get(type);
            if (layout != null) return (FoliosRecipeLayout<T>) layout;
        }
        return null;
    }
}
