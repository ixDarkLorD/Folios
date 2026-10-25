package net.ixdarklord.folios.api.client;

import com.google.gson.JsonObject;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What keeps a category, entry or page closed until the player has earned it. In JSON these are fields of the thing
 * locked:
 * <ul>
 *     <li>{@code "requires"}: a condition, written as a quest objective is ({@link FoliosObjective}), or a list of them
 *     all of which must hold: {@code "requires": {"advancement": "mymod:story/found_ore"}},
 *     {@code "requires": [{"read": "basics/first_steps"}, {"obtained": "minecraft:diamond"}]};</li>
 *     <li>{@code "secret": true}: left out until it opens, where it would otherwise be shown locked.</li>
 * </ul>
 *
 * @param book the book it is in, whose reading its conditions ask about
 */
public record FoliosLock(@Nullable Identifier book, List<FoliosObjective> requires, boolean secret) {
    public static final FoliosLock NONE = new FoliosLock(null, List.of(), false);

    public FoliosLock {
        requires = List.copyOf(requires);
    }

    /** The lock an object of a book's JSON (a category, an entry or a page) has. */
    public static FoliosLock read(JsonObject json, Identifier book) {
        List<FoliosObjective> requires = json.has("requires") ? FoliosObjective.readAll(json.get("requires")) : List.of();
        boolean secret = json.has("secret") && json.get("secret").getAsBoolean();
        return requires.isEmpty() && !secret ? NONE : new FoliosLock(book, requires, secret);
    }

    public boolean isOpen() {
        if (requires.isEmpty()) return true;
        FoliosBook in = book != null ? FoliosLibrary.book(book) : null;
        if (in == null) return false;
        for (FoliosObjective condition : requires) if (!condition.isDone(in)) return false;
        return true;
    }

    /** Whether it is shown at all: open, or locked but not secret. */
    public boolean isVisible() {
        return !secret || isOpen();
    }
}
