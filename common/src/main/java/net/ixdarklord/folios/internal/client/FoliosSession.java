package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.page.ErrorPage;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// A book as laid out for the reader's language and progress: each entry's pages are set out the first time they're
// shown and kept until something they depend on changes (a reload, the language, an unlock).
public final class FoliosSession {
    /** An entry's pages, ready to draw: markers and the pages of its JSON point into them. */
    public record EntryPages(List<FoliosPageElement.Laid> pages, Map<String, Integer> markers, int[] starts) {
        /** Where a page of the entry's JSON begins among the laid pages. */
        public int start(int page) {
            return page < 0 || page >= starts.length ? 0 : starts[page];
        }

        /** Which page of the entry's JSON a laid page came from. */
        public int source(int laid) {
            int source = 0;
            for (int index = 0; index < starts.length; index++) {
                if (starts[index] <= laid) source = index;
            }
            return source;
        }
    }

    /** Where a link goes. */
    public record Target(@Nullable String entry, int page, @Nullable String category, @Nullable String url) {}

    private final FoliosBook book;
    private final FoliosLayout layout;
    private final String language;
    private final int progress;
    private final Map<String, EntryPages> entries = new HashMap<>();
    private @Nullable Map<String, String> searchIndex;

    FoliosSession(FoliosBook book) {
        this(book, FoliosLayout.PAGE_WIDTH, FoliosLayout.PAGE_HEIGHT);
    }

    /** Laid out to pages {@code pageWidth} by {@code pageHeight}. */
    FoliosSession(FoliosBook book, int pageWidth, int pageHeight) {
        this.book = book;
        this.layout = new FoliosLayout(book, Minecraft.getInstance().font, pageWidth, pageHeight);
        this.language = Minecraft.getInstance().getLanguageManager().getSelected();
        this.progress = FoliosProgress.version();
    }

    public FoliosBook book() {
        return book;
    }

    public FoliosLayout layout() {
        return layout;
    }

    boolean isCurrent() {
        return language.equals(Minecraft.getInstance().getLanguageManager().getSelected()) && progress == FoliosProgress.version();
    }

    void close() {
        layout.close();
    }

    public EntryPages entry(String id) {
        EntryPages pages = entries.get(id);
        if (pages == null) {
            pages = FoliosText.in(book, () -> this.lay(id));
            entries.put(id, pages);
        }
        return pages;
    }

    private EntryPages lay(String id) {
        FoliosEntry entry = book.entry(id);
        List<FoliosPageElement.Laid> pages = new ArrayList<>();
        Map<String, Integer> markers = new HashMap<>();
        if (entry == null) return new EntryPages(pages, markers, new int[0]);
        int[] starts = new int[entry.pages().size()];
        for (int index = 0; index < entry.pages().size(); index++) {
            FoliosEntry.Page page = entry.pages().get(index);
            // A page over both pages starts on a left one.
            if (page.lock().isOpen() && page.page().spread() && pages.size() % 2 == 1) pages.add(painter -> {});
            starts[index] = pages.size();
            if (!page.lock().isOpen()) continue;
            if (page.marker() != null) markers.put(page.marker(), pages.size());
            try {
                // The entry's icon heads its first page; a later page shows one only if it names its own.
                layout.titleIcon(page.icon() != null ? page.icon() : index == 0 ? entry.icon() : net.ixdarklord.folios.api.client.page.FoliosIcon.NONE);
                pages.addAll(page.page().lay(layout));
            } catch (RuntimeException e) {
                Folios.LOGGER.error("Couldn't lay out page {} of the entry {} of the guide {}", index + 1, id, book.id(), e);
                pages.addAll(new ErrorPage("This page couldn't be shown: " + e).lay(layout));
            }
        }
        layout.titleIcon(net.ixdarklord.folios.api.client.page.FoliosIcon.NONE);
        if (pages.isEmpty()) pages.add(painter -> {});
        return new EntryPages(List.copyOf(pages), Map.copyOf(markers), starts);
    }

    /** A laid page of an entry as a place in the book. */
    public FoliosBook.Location location(String entry, int laid) {
        return new FoliosBook.Location(entry, entry(entry).source(laid));
    }

    /** Which laid page of its entry a place is. */
    public int pageOf(FoliosBook.Location location) {
        return entry(location.entry()).start(location.page());
    }

    // ---- Links ----

    public @Nullable Target resolve(String link) {
        if (link.startsWith("http://") || link.startsWith("https://")) return new Target(null, 0, null, link);
        if (link.startsWith("category:")) {
            String id = link.substring(9);
            FoliosCategory category = book.category(id);
            return category != null && category.isOpen() ? new Target(null, 0, id, null) : null;
        }
        String entry = link.startsWith("entry:") ? link.substring(6) : link;
        String marker = null;
        int hash = entry.indexOf('#');
        if (hash >= 0) {
            marker = entry.substring(hash + 1);
            entry = entry.substring(0, hash);
        }
        FoliosEntry found = book.entry(entry);
        if (found == null || !found.isOpen()) return null;
        int page = 0;
        if (marker != null) {
            Integer at = entry(entry).markers().get(marker);
            if (at != null) page = at;
        }
        return new Target(entry, page, null, null);
    }

    /** What a link's tooltip says: where it goes. */
    // Each link's popup, made once while the book is laid out this way (so its scene is kept).
    private final Map<String, java.util.Optional<net.ixdarklord.folios.api.client.page.FoliosPopup>> popups = new HashMap<>();

    /** The popup window a link shows ({@link net.ixdarklord.folios.api.client.FoliosLinks}); null for none, or while it is locked. */
    public net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup popup(String link) {
        net.ixdarklord.folios.api.client.FoliosLinks.Found found = net.ixdarklord.folios.api.client.FoliosLinks.find(link);
        if (found == null || !found.kind().isOpen(book, found.target())) return null;
        return popups.computeIfAbsent(link, key -> java.util.Optional.ofNullable(found.kind().popup(layout, found.target()))).orElse(null);
    }

    /** What a link says under the pointer: a kind of a mod's own as it says, else where it goes. */
    public java.util.List<Component> tooltip(String link) {
        net.ixdarklord.folios.api.client.FoliosLinks.Found found = net.ixdarklord.folios.api.client.FoliosLinks.find(link);
        if (found == null) return java.util.List.of(this.describe(link));
        if (!found.kind().isOpen(book, found.target())) return java.util.List.of(Component.translatableWithFallback("folios.link.locked", "Not yet discovered"));
        return found.kind().tooltip(book, found.target());
    }

    public Component describe(String link) {
        Target target = this.resolve(link);
        if (target == null) return Component.translatableWithFallback("folios.link.locked", "Not yet discovered");
        if (target.url() != null) return Component.literal(target.url());
        if (target.category() != null) {
            FoliosCategory category = book.category(target.category());
            return category != null ? category.title() : Component.literal(target.category());
        }
        FoliosEntry entry = book.entry(target.entry());
        return entry != null ? entry.title() : Component.literal(String.valueOf(target.entry()));
    }

    // ---- Search ----

    /** The open entries whose name or words contain every word of a query, best matches (by name) first. */
    public List<FoliosEntry> search(String query) {
        String[] words = query.toLowerCase(Locale.ROOT).strip().split("\\s+");
        if (searchIndex == null) {
            searchIndex = FoliosText.in(book, () -> {
                Map<String, String> index = new HashMap<>();
                for (FoliosEntry entry : book.entries().values()) {
                    StringBuilder text = new StringBuilder();
                    for (FoliosEntry.Page page : entry.pages()) text.append(' ').append(FoliosText.plain(page.page().searchText()));
                    index.put(entry.id(), text.toString().toLowerCase(Locale.ROOT));
                }
                return index;
            });
        }
        List<FoliosEntry> named = new ArrayList<>();
        List<FoliosEntry> mentioned = new ArrayList<>();
        for (FoliosEntry entry : book.entries().values()) {
            if (!entry.isOpen()) continue;
            FoliosCategory category = book.category(entry.category());
            if (category == null || !category.isOpen()) continue;
            String name = entry.title().getString().toLowerCase(Locale.ROOT);
            String body = searchIndex.getOrDefault(entry.id(), "");
            boolean inName = true;
            boolean anywhere = true;
            for (String word : words) {
                if (word.isEmpty()) continue;
                inName &= name.contains(word);
                anywhere &= name.contains(word) || body.contains(word);
            }
            if (inName) named.add(entry);
            else if (anywhere) mentioned.add(entry);
        }
        named.addAll(mentioned);
        return named;
    }
}
