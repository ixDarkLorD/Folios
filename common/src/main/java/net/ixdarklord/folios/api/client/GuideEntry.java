package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.api.client.page.GuideIcon;
import net.ixdarklord.folios.api.client.page.GuidePage;
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
public record GuideEntry(String id, String category, String name, GuideIcon icon, int sort, boolean featured, GuideLock lock, List<Page> pages,
                         GuideReveal.@Nullable Use reveal) {
    /** An entry that reveals its pages as its book does. */
    public GuideEntry(String id, String category, String name, GuideIcon icon, int sort, boolean featured, GuideLock lock, List<Page> pages) {
        this(id, category, name, icon, sort, featured, lock, pages, null);
    }

    /**
     * A page of the entry, with what applies to it whatever its kind.
     *
     * @param anchor a name links can reach it by; null for none
     */
    public record Page(GuidePage page, @Nullable String anchor, GuideLock lock) {}

    public Component title() {
        return net.ixdarklord.folios.api.client.page.GuideText.inline(name);
    }

    /** Everything its pages ask the player to do; empty for an entry that isn't a quest. */
    public List<GuideObjective> objectives() {
        List<GuideObjective> all = new java.util.ArrayList<>();
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
