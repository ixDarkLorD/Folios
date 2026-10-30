package net.ixdarklord.folios.api.client;

import com.google.gson.JsonObject;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What keeps a category, entry, page or element closed until the player has earned it. In JSON these are fields of the thing
 * locked:
 * <ul>
 *     <li>{@code "requires"}: a condition, written as a quest objective is ({@link FoliosObjective}), or a list of them
 *     all of which must hold: {@code "requires": {"advancement": "mymod:story/found_ore"}},
 *     {@code "requires": [{"read": "basics/first_steps"}, {"obtained": "minecraft:diamond"}]};</li>
 *     <li>{@code "secret": true}: left out until it opens, where it would otherwise be shown locked ({@code ???}).</li>
 *     <li>{@code "show_list": false}: a locked page or element shown without the list of what opens it.</li>
 *     <li>{@code "peek"}: a float, how plainly a locked page or element shows a glimpse of itself behind its seal, 1.0 by
 *     default: 0.5 half as plainly, 2.0 twice as plainly, 0.0 not at all.</li>
 * </ul>
 *
 * @param book the book it is in, whose reading its conditions ask about
 */
public record FoliosLock(@Nullable Identifier book, List<FoliosObjective> requires, boolean secret, boolean showUnlock, float peek) {
    public static final FoliosLock NONE = new FoliosLock(null, List.of(), false);

    /** A lock that shows what opens it, and gives a glimpse of what it keeps. */
    public FoliosLock(@Nullable Identifier book, List<FoliosObjective> requires, boolean secret) {
        this(book, requires, secret, true, 1.0F);
    }

    /** A lock that gives a glimpse of what it keeps. */
    public FoliosLock(@Nullable Identifier book, List<FoliosObjective> requires, boolean secret, boolean showUnlock) {
        this(book, requires, secret, showUnlock, 1.0F);
    }

    public FoliosLock {
        requires = List.copyOf(requires);
        peek = Math.max(0, peek);
    }

    /** The lock an object of a book's JSON (a category, an entry, a page or an element) has. */
    public static FoliosLock read(JsonObject json, @Nullable Identifier book) {
        List<FoliosObjective> requires = json.has("requires") ? FoliosObjective.readAll(json.get("requires")) : List.of();
        boolean secret = json.has("secret") && json.get("secret").getAsBoolean();
        String listKey = json.has("show_list") ? "show_list" : "show_unlock";
        boolean show = !json.has(listKey) || json.get(listKey).getAsBoolean();
        float peek = json.has("peek") ? json.get("peek").getAsFloat() : 1.0F;
        return requires.isEmpty() && !secret ? NONE : new FoliosLock(book, requires, secret, show, peek);
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
