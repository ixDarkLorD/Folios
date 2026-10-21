package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.internal.compat.RecipeDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// How recipes look, made from the recipes the server sent as the player joined, and kept for the rest of the visit.
public final class GuideRecipes {
    private static final Map<ResourceLocation, List<RecipeDisplay>> KNOWN = new HashMap<>();

    private GuideRecipes() {}

    /** A recipe's displays: empty when there is no such recipe; null while not in a world. */
    public static synchronized @Nullable List<RecipeDisplay> get(ResourceLocation recipe) {
        List<RecipeDisplay> displays = KNOWN.get(recipe);
        if (displays != null) return displays;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        displays = level.getRecipeManager().byKey(recipe)
                .map(holder -> RecipeDisplay.of(holder.value(), level.registryAccess()))
                .map(List::of)
                .orElse(List.of());
        KNOWN.put(recipe, displays);
        return displays;
    }

    public static synchronized void tick() {
    }

    public static synchronized void clear() {
        KNOWN.clear();
    }
}
