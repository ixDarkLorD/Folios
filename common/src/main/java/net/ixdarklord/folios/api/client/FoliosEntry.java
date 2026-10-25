package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An entry of a book: a titled run of pages, in a category. Read from {@code entries/<id>.json} in the book's folder;
 * its id is that path ({@code "basics/first_steps"}).
 *
 * @param name     raw, as written: a translation key or text with marks ({@link #title()} reads it)
 * @param sort     lower comes first, then by name
 * @param featured shown first in its category, marked
 */
public record FoliosEntry(String id, String category, String name, FoliosIcon icon, int sort, boolean featured, FoliosLock lock, List<Page> pages,
                         FoliosReveal.@Nullable Use reveal) {
    /** An entry that reveals its pages as its book does. */
    public FoliosEntry(String id, String category, String name, FoliosIcon icon, int sort, boolean featured, FoliosLock lock, List<Page> pages) {
        this(id, category, name, icon, sort, featured, lock, pages, null);
    }

    /**
     * A page of the entry, with what applies to it whatever its kind.
     *
     * @param marker a name links can reach it by ({@code entry:<id>#<marker>}); null for none
     * @param icon   shown before its title: its own ({@code "icon"}; {@link FoliosIcon#NONE} for {@code "none"}), or null
     *               for the entry's on its first page and none on the others
     */
    public record Page(FoliosPageElement page, @Nullable String marker, FoliosLock lock, @Nullable FoliosIcon icon) {
        public Page(FoliosPageElement page, @Nullable String marker, FoliosLock lock) {
            this(page, marker, lock, null);
        }
    }

    public Component title() {
        return net.ixdarklord.folios.api.client.page.FoliosText.inline(name);
    }

    /** Everything its pages ask the player to do; empty for an entry that isn't a quest. */
    public List<FoliosObjective> objectives() {
        List<FoliosObjective> all = new java.util.ArrayList<>();
        for (Page page : pages) all.addAll(page.page().objectives());
        return all;
    }

    public boolean isQuest() {
        return pages.stream().anyMatch(page -> !page.page().objectives().isEmpty());
    }

    public boolean isOpen() {
        return lock.isOpen();
    }

    public boolean isVisible() {
        return lock.isVisible();
    }
}
