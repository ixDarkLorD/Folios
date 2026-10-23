package net.ixdarklord.folios.api.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Values a mod reports for books to follow: an objective {@code {"source": "mymod:mana", "goal": 100}} is as far along
 * as the source's value is towards its goal. The value is asked whenever the objective is drawn or checked, so keep it
 * cheap (read it from what the client already has).
 * <pre>{@code
 * FoliosObjectives.register(MyMod.id("mana"), player -> ManaClient.current());
 * FoliosObjectives.register(MyMod.id("spells_known"), player -> ClientSpells.known().size());
 * }</pre>
 */
public final class FoliosObjectives {
    private static final Map<ResourceLocation, Source> SOURCES = new ConcurrentHashMap<>();

    private FoliosObjectives() {}

    /** A value to follow, for the local player. */
    @FunctionalInterface
    public interface Source {
        float value(LocalPlayer player);
    }

    public static void register(ResourceLocation id, Source source) {
        SOURCES.put(id, source);
    }

    public static @Nullable Source get(ResourceLocation id) {
        return SOURCES.get(id);
    }
}
