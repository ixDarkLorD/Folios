package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An entry of a book: a titled run of pages, in a category. Read from {@code entries/<id>.json} in the book's folder;
 * its id is that path ({@code "basics/first_steps"}).
 *
 * @param name     raw, as written: a translation key or text with marks ({@link #title()} reads it)
 * @param sort     lower comes first, then by name
 * @param featured shown first in its category, marked
 * @param listings its pages and elements that the book's index and search list as if each were an entry of its own
 */
public record FoliosEntry(String id, String category, String name, FoliosIcon icon, int sort, boolean featured, FoliosLock lock, List<Page> pages,
                         FoliosReveal.@Nullable Use reveal, List<Listing> listings) {
    public FoliosEntry {
        listings = List.copyOf(listings);
    }

    /** An entry with nothing of it listed on its own. */
    public FoliosEntry(String id, String category, String name, FoliosIcon icon, int sort, boolean featured, FoliosLock lock, List<Page> pages,
                       FoliosReveal.@Nullable Use reveal) {
        this(id, category, name, icon, sort, featured, lock, pages, reveal, List.of());
    }

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

    /**
     * A page of the entry, or an element of one, written with {@code "index"}: listed in the book's index and found by
     * its search as an entry is, under a name and an icon of its own, and opening the entry at it. In JSON,
     * {@code "index": true}, or {@code "index": {"name": ..., "icon": ...}} to name it and give it an icon.
     *
     * @param name    raw, as written; null to take its title, else the name of the item it shows, else its entry's
     * @param icon    its own; null to take the item it shows, else its entry's icon
     * @param page    which page of the entry it is (from 0)
     * @param element which element of that page it is (from 0), or -1 for the page itself
     * @param title   its title as written, or null
     * @param shown   what it shows: its items and its words
     * @param locks   what keeps it closed besides its entry: its page's lock, and its own as an element
     */
    public record Listing(@Nullable String name, @Nullable FoliosIcon icon, int page, int element, @Nullable String title, FoliosPageElement shown,
                          List<FoliosLock> locks) {
        public Listing {
            locks = List.copyOf(locks);
        }

        /** What it is listed as. */
        public Component title(FoliosEntry entry) {
            if (name != null) return net.ixdarklord.folios.api.client.page.FoliosText.inline(name);
            if (title != null && !title.isEmpty()) return net.ixdarklord.folios.api.client.page.FoliosText.inline(title);
            ItemStack item = this.item();
            return item.isEmpty() ? entry.title() : item.getHoverName();
        }

        /** The icon it is listed with. */
        public FoliosIcon icon(FoliosEntry entry) {
            if (icon != null) return icon;
            for (FoliosStack stack : shown.items()) {
                if (!stack.first().isEmpty()) return FoliosIcon.of(stack);
            }
            return entry.icon();
        }

        // The first item it shows, or none.
        private ItemStack item() {
            for (FoliosStack stack : shown.items()) {
                ItemStack first = stack.first();
                if (!first.isEmpty()) return first;
            }
            return ItemStack.EMPTY;
        }

        /** Where it is, as a link: {@code entry:<id>#@<page>} for a page, {@code entry:<id>#@<page>.<element>} for an element. */
        public String link(FoliosEntry entry) {
            return "entry:" + entry.id() + "#@" + page + (element >= 0 ? "." + element : "");
        }

        /** Whether it can be read: its entry open, and nothing of its own keeping it closed. */
        public boolean isOpen(FoliosEntry entry) {
            if (!entry.isOpen()) return false;
            for (FoliosLock lock : locks) if (!lock.isOpen()) return false;
            return true;
        }

        /** Whether it is listed at all: it and its entry not secret, or open. */
        public boolean isVisible(FoliosEntry entry) {
            if (!entry.isVisible()) return false;
            for (FoliosLock lock : locks) if (!lock.isVisible()) return false;
            return true;
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
