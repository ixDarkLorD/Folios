package net.ixdarklord.folios.internal.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.folios.internal.core.Folios;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class CelebrationSettings {
    public static final Set<String> EVENTS = Set.of("objective", "entry", "category", "quest", "mastery");
    public static final Set<String> PARTS = Set.of("toast", "sound", "particles", "fireworks");
    public static final CelebrationSettings OFF = new CelebrationSettings(false, Map.of(), Map.of());

    private final boolean enabled;
    private final Map<String, Boolean> parts;
    private final Map<String, Map<String, Boolean>> events;

    private CelebrationSettings(boolean enabled, Map<String, Boolean> parts, Map<String, Map<String, Boolean>> events) {
        this.enabled = enabled;
        this.parts = parts;
        this.events = events;
    }

    public boolean on(String event, String part) {
        if (!enabled) return false;
        Map<String, Boolean> own = events.get(event);
        if (own != null && own.containsKey(part)) return own.get(part);
        return parts.getOrDefault(part, true);
    }

    public boolean any(String event) {
        for (String part : PARTS) if (this.on(event, part)) return true;
        return false;
    }

    public static CelebrationSettings read(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) return OFF;
        if (json.isJsonPrimitive()) return json.getAsBoolean() ? new CelebrationSettings(true, Map.of(), Map.of()) : OFF;
        if (!json.isJsonObject()) return OFF;
        JsonObject object = json.getAsJsonObject();
        Map<String, Boolean> parts = new HashMap<>();
        Map<String, Map<String, Boolean>> events = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (PARTS.contains(key) && value.isJsonPrimitive()) {
                parts.put(key, value.getAsBoolean());
            } else if (EVENTS.contains(key)) {
                Map<String, Boolean> own = new HashMap<>();
                if (value.isJsonPrimitive()) {
                    for (String part : PARTS) if (!value.getAsBoolean()) own.put(part, false);
                } else if (value.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> part : value.getAsJsonObject().entrySet()) {
                        if (PARTS.contains(part.getKey()) && part.getValue().isJsonPrimitive()) own.put(part.getKey(), part.getValue().getAsBoolean());
                        else Folios.LOGGER.warn("A book's celebrations name {} under {}, which isn't one of {}", part.getKey(), key, PARTS);
                    }
                }
                events.put(key, Map.copyOf(own));
            } else {
                Folios.LOGGER.warn("A book's celebrations name {}, which isn't one of {} or {}", key, PARTS, EVENTS);
            }
        }
        return new CelebrationSettings(true, Map.copyOf(parts), Map.copyOf(events));
    }
}
