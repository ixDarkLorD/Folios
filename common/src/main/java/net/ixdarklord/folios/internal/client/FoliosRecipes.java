package net.ixdarklord.folios.internal.client;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FoliosRecipes {
    private static final Map<Identifier, List<RecipeDisplay>> KNOWN = new HashMap<>();
    private static final Set<Identifier> ASKED = new LinkedHashSet<>();
    private static final Set<Identifier> TO_ASK = new LinkedHashSet<>();
    private static int connectedTicks;

    private FoliosRecipes() {}

    public static synchronized @Nullable List<RecipeDisplay> get(Identifier recipe) {
        List<RecipeDisplay> displays = KNOWN.get(recipe);
        if (displays == null && ASKED.add(recipe)) TO_ASK.add(recipe);
        return displays;
    }

    public static synchronized void receive(FoliosPayloads.Recipe payload) {
        KNOWN.put(payload.id(), payload.displays());
    }

    public static synchronized void tick() {
        if (Minecraft.getInstance().getConnection() == null) return;
        connectedTicks++;
        if (TO_ASK.isEmpty()) return;
        if (!FoliosProgress.serverHasGuides()) {
            if (connectedTicks < 100) return;
            TO_ASK.forEach(id -> KNOWN.put(id, List.of()));
            TO_ASK.clear();
            return;
        }
        List<Identifier> ids = new ArrayList<>(TO_ASK);
        TO_ASK.clear();
        for (int start = 0; start < ids.size(); start += 64) {
            Network.sendToServer(new FoliosPayloads.RecipeRequest(ids.subList(start, Math.min(ids.size(), start + 64))));
        }
    }

    public static synchronized void clear() {
        connectedTicks = 0;
        KNOWN.clear();
        ASKED.clear();
        TO_ASK.clear();
    }
}
