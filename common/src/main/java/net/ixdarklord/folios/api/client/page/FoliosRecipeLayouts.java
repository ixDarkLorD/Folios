package net.ixdarklord.folios.api.client.page;

import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link FoliosRecipeLayout}s the {@code recipe} page element draws recipes with, by the type of their
 * {@link RecipeDisplay}. Recipes without one are drawn by Folios itself: crafting, cooking, stonecutting and smithing
 * as such, and any other as its ingredients, an arrow and its result.
 */
public final class FoliosRecipeLayouts {
    private static final Map<Class<?>, FoliosRecipeLayout<?>> LAYOUTS = new ConcurrentHashMap<>();

    private FoliosRecipeLayouts() {}

    /**
     * Draws recipes whose display is of this type (or a subtype without a layout of its own) with this layout.
     * Register while the client initialises; registering a type again replaces its layout.
     */
    public static <T extends RecipeDisplay> void register(Class<T> type, FoliosRecipeLayout<T> layout) {
        LAYOUTS.put(type, layout);
    }

    /** The layout a display is drawn with, or {@code null} when Folios draws it itself. */
    @SuppressWarnings("unchecked")
    public static <T extends RecipeDisplay> @Nullable FoliosRecipeLayout<T> get(T display) {
        for (Class<?> type = display.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            FoliosRecipeLayout<?> layout = LAYOUTS.get(type);
            if (layout != null) return (FoliosRecipeLayout<T>) layout;
        }
        return null;
    }
}
