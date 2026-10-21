package net.ixdarklord.folios.api.client;

import com.google.gson.JsonObject;
import net.ixdarklord.folios.internal.client.GuideProgress;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What keeps a category, entry or page closed until the player has earned it. In JSON these are fields of the thing
 * locked:
 * <ul>
 *     <li>{@code "advancement": "mymod:story/found_ore"}: open once that advancement is done;</li>
 *     <li>{@code "flag": "mymod:hard_mode"}: open while that flag, set by code ({@link Guides#setFlag}), is on; a
 *     leading {@code !} reverses it;</li>
 *     <li>{@code "secret": true}: left out until it opens, where it would otherwise be shown locked.</li>
 * </ul>
 */
public record GuideLock(@Nullable Identifier advancement, @Nullable String flag, boolean secret) {
    public static final GuideLock NONE = new GuideLock(null, null, false);

    public static GuideLock read(JsonObject json) {
        Identifier advancement = json.has("advancement") ? Identifier.parse(json.get("advancement").getAsString()) : null;
        String flag = json.has("flag") ? json.get("flag").getAsString() : null;
        boolean secret = json.has("secret") && json.get("secret").getAsBoolean();
        return advancement == null && flag == null && !secret ? NONE : new GuideLock(advancement, flag, secret);
    }

    public boolean isOpen() {
        if (advancement != null && !GuideProgress.isDone(advancement)) return false;
        return flag == null || GuideProgress.flag(flag);
    }

    /** Whether it is shown at all: open, or locked but not secret. */
    public boolean isVisible() {
        return !secret || isOpen();
    }
}
