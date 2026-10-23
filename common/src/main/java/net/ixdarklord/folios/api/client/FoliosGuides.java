package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.internal.client.GuideClient;
import net.ixdarklord.folios.internal.client.GuideLibrary;
import net.ixdarklord.folios.internal.client.GuideMemory;
import net.ixdarklord.folios.internal.client.GuideProgress;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.BooleanSupplier;

/**
 * Guide books on the client: the books loaded from resource packs, and opening them.
 * <p>
 * A book is a folder of JSON in a resource pack, so resource packs can add, translate and replace them, and
 * {@code F3 + T} reloads them while you write:
 * <pre>
 * assets/mymod/folios/handbook/
 *     book.json                       name, subtitle, landing (the first page's text), style, accent, cover, icon, tilt, distance, page_turn,
 *                                     textures (page, frame, cover, tab, tab_plate, backdrop), layout
 *     categories/basics.json          name, description, icon, sort, color (its tab)
 *     entries/basics/first_steps.json name, category, icon, sort, featured, pages: [...]
 * </pre>
 * Text anywhere can be a translation key, and is written in a small Markdown
 * ({@link net.ixdarklord.folios.api.client.page.FoliosText}); the kinds of page are listed in
 * {@link net.ixdarklord.folios.api.client.page.FoliosPageElements}, and a book's {@code "style"} picks how it is
 * shown ({@link FoliosStyles}). Categories, entries and pages can be locked behind advancements or flags
 * ({@link FoliosLock}).
 * <pre>{@code
 * FoliosGuides.open(MyMod.id("handbook"));
 * FoliosGuides.open(MyMod.id("handbook"), "basics/first_steps");
 * FoliosGuides.setFlag("mymod:hard_mode", () -> MyConfig.HARD_MODE.get());
 * }</pre>
 * The server can open books too, and give them as items: {@link net.ixdarklord.folios.api.FoliosBooks}.
 */
public final class FoliosGuides {
    private FoliosGuides() {}

    public static @Nullable FoliosBook book(ResourceLocation id) {
        return GuideLibrary.book(id);
    }

    public static Collection<FoliosBook> books() {
        return GuideLibrary.books().values();
    }

    /** Opens a book where the player left off (or at its start), in its own style; false when there is no such book. */
    public static boolean open(ResourceLocation book) {
        return GuideClient.open(book, null, null);
    }

    /**
     * Opens a book at an entry ({@code "category/entry"}, optionally with {@code "#anchor"}).
     *
     * @return false when there is no such book or entry, or the entry is locked
     */
    public static boolean open(ResourceLocation book, String entry) {
        return GuideClient.open(book, entry, null);
    }

    /** Whether an entry of a book has been opened by the player (kept between games). */
    public static boolean isRead(ResourceLocation book, String entry) {
        return GuideMemory.of(book).isRead(entry);
    }

    /**
     * Sets a flag that entries can be locked behind ({@code "flag": "mymod:hard_mode"} in their JSON). It is asked
     * whenever a book is laid out; call {@link #refresh()} when its answer changes while a book may be open.
     */
    public static void setFlag(String flag, BooleanSupplier value) {
        GuideProgress.setFlag(flag, value);
    }

    /** Lays every book out again at its next drawing, for after something its locks depend on changed. */
    public static void refresh() {
        GuideProgress.changed();
    }
}
