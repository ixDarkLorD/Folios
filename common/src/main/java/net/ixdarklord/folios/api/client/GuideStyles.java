package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ways a book can be shown, named by its {@code book.json}'s {@code "style"}. Two are built in, and any page shows
 * in either:
 * <ul>
 *     <li>{@link #SCREEN} ({@code "screen"}, the default): a screen with the book's categories down the side, search,
 *     bookmarks, and an entry's pages two at a time.</li>
 *     <li>{@link #HANDHELD} ({@code "handheld"}): the book itself, held open in the player's hands in the world, its
 *     leaves turned by hand; the pointer is free to press what is on its pages.</li>
 * </ul>
 * A mod can add its own: {@code GuideStyles.register(MyMod.id("scroll"), (book, at, hand) -> ...)}.
 */
public final class GuideStyles {
    public static final ResourceLocation SCREEN = Folios.rl("screen");
    public static final ResourceLocation HANDHELD = Folios.rl("handheld");

    private static final Map<ResourceLocation, Style> STYLES = new ConcurrentHashMap<>();

    private GuideStyles() {}

    /** Shows a book. */
    @FunctionalInterface
    public interface Style {
        /**
         * Opens a book.
         *
         * @param at   where to open it; null for where the player left off (or its start)
         * @param hand the hand it is held in, when opened by using a book item; null when opened otherwise
         */
        void open(GuideBook book, GuideBook.@Nullable Location at, @Nullable InteractionHand hand);
    }

    public static void register(ResourceLocation id, Style style) {
        STYLES.put(id, style);
    }

    public static @Nullable Style get(ResourceLocation id) {
        return STYLES.get(id);
    }

    /** A style as written in a book: a bare name is CoolCatLib's. */
    public static ResourceLocation id(String written) {
        return written.indexOf(':') >= 0 ? ResourceLocation.parse(written) : Folios.rl(written);
    }
}
