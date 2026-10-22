package net.ixdarklord.folios.internal.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.folios.internal.core.Folios;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// How a book celebrates the player finishing part of it, from "celebrations" in book.json; nothing unless it says so.
// true turns all of it on. An object turns it on but for what it says: the parts of a celebration ("toast", "sound",
// "particles", and "fireworks", which mastery alone has) for every event, and each event ("objective", "entry",
// "category", "quest", "mastery") false, true, or an object of its own parts. A part or event left out of an object
// is on.
public final class CelebrationSettings {
    public static final Set<String> EVENTS = Set.of("objective", "entry", "category", "quest", "mastery");
    public static final Set<String> PARTS = Set.of("toast", "sound", "particles", "fireworks");
    /** None of it: a book that says nothing of celebrations. */
    public static final CelebrationSettings OFF = new CelebrationSettings(false, Map.of(), Map.of());

    private final boolean enabled;
    private final Map<String, Boolean> parts;
    private final Map<String, Map<String, Boolean>> events;

    private CelebrationSettings(boolean enabled, Map<String, Boolean> parts, Map<String, Map<String, Boolean>> events) {
        this.enabled = enabled;
        this.parts = parts;
        this.events = events;
    }

    /** Whether a part of an event's celebration plays: {@code on("mastery", "fireworks")}. */
    public boolean on(String event, String part) {
        if (!enabled) return false;
        Map<String, Boolean> own = events.get(event);
        if (own != null && own.containsKey(part)) return own.get(part);
        return parts.getOrDefault(part, true);
    }

    /** Whether any part of an event's celebration plays. */
    public boolean any(String event) {
        for (String part : PARTS) if (this.on(event, part)) return true;
        return false;
    }

    /** Reads "celebrations"; {@link #OFF} for none, false, or what can't be read. */
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
                    // An event on or off as a whole.
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
