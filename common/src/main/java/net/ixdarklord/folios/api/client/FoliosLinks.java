package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPopup;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kinds of link a book's text can make: {@code [label](scheme:target)} in any book goes to the kind registered for
 * {@code scheme}, which says what the link shows under the pointer and does when pressed.
 * <pre>{@code
 * // [the Fire spell](spell:mymod:fireball) shows the spell's cost and opens the mod's spell screen.
 * FoliosLinks.register("spell", new FoliosLinks.Kind() {
 *     public List<Component> tooltip(FoliosBook book, String target) {
 *         Spell spell = Spells.get(target);
 *         return spell == null ? List.of() : List.of(spell.name(), Component.literal(spell.cost() + " mana"));
 *     }
 *
 *     public boolean press(FoliosBook book, String target) {
 *         Minecraft.getInstance().setScreen(new SpellScreen(target));
 *         return true;
 *     }
 * });
 * }</pre>
 * A kind can show a {@link FoliosPopup} in place of a tooltip: pressing its link then pins it, unless {@link Kind#press}
 * does something else. Folios's own are {@code entry:}, {@code category:}, {@code item:} and web addresses, and the
 * popups {@code entity:}, {@code structure:}, {@code build:} and {@code multiblock:}; a scheme can't be registered twice.
 * Register while the client initialises.
 */
public final class FoliosLinks {
    private static final Map<String, Kind> KINDS = new ConcurrentHashMap<>();
    private static final List<String> OWN = List.of("entry", "category", "item", "bookmark", "http", "https");

    private FoliosLinks() {}

    /** What a kind of link does. Every method has a default, so a kind gives only what it needs. */
    public interface Kind {
        /** What the link says under the pointer: a title and lines under it; empty for nothing. */
        default List<Component> tooltip(FoliosBook book, String target) {
            return List.of();
        }

        /**
         * A window shown under the pointer in place of a tooltip, and pinned when the link is pressed; null for none.
         * Asked once for each link of a laid-out book; {@code layout} makes scenes for it ({@link FoliosLayout#viewport}).
         */
        default @Nullable FoliosPopup popup(FoliosLayout layout, String target) {
            return null;
        }

        /** The link pressed: true when it did something, false to pin its popup, if it has one. */
        default boolean press(FoliosBook book, String target) {
            return false;
        }

        /** Whether the reader can follow it now; a link that can't shows "Not yet discovered" and does nothing. */
        default boolean isOpen(FoliosBook book, String target) {
            return true;
        }
    }

    /** Adds a kind of link, {@code scheme:target}; a scheme already taken (Folios's own too) is refused. */
    public static void register(String scheme, Kind kind) {
        if (OWN.contains(scheme) || KINDS.putIfAbsent(scheme, kind) != null) {
            throw new IllegalArgumentException("The link scheme " + scheme + " is already taken");
        }
    }

    /** The kind a link's scheme names; null for Folios's built-in links and schemes no one registered. */
    public static @Nullable Kind get(String scheme) {
        return KINDS.get(scheme);
    }

    /** The kind a whole link ({@code scheme:target}) goes to, and its target; null when no kind takes it. */
    public static @Nullable Found find(String link) {
        int colon = link.indexOf(':');
        if (colon <= 0) return null;
        Kind kind = KINDS.get(link.substring(0, colon));
        return kind == null ? null : new Found(kind, link.substring(colon + 1));
    }

    /** A link's kind and the part of it after the scheme. */
    public record Found(Kind kind, String target) {}
}
