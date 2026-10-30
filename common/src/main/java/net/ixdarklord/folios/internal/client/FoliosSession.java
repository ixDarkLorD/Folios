package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.page.ErrorPage;
import net.ixdarklord.folios.internal.client.page.Locked;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FoliosSession {
    public record EntryPages(List<FoliosPageElement.Laid> pages, Map<String, Integer> markers, int[] starts, Map<String, FoliosLayout.Mark> places) {
        public EntryPages(List<FoliosPageElement.Laid> pages, Map<String, Integer> markers, int[] starts) {
            this(pages, markers, starts, Map.of());
        }

        public int start(int page) {
            return page < 0 || page >= starts.length ? 0 : starts[page];
        }

        public int source(int laid) {
            int source = 0;
            for (int index = 0; index < starts.length; index++) {
                if (starts[index] <= laid) source = index;
            }
            return source;
        }
    }

    public record Target(@Nullable String entry, int page, @Nullable String category, @Nullable String url, @Nullable String marker) {
        public Target(@Nullable String entry, int page, @Nullable String category, @Nullable String url) {
            this(entry, page, category, url, null);
        }
    }

    private final FoliosBook book;
    private final FoliosLayout layout;
    private final String language;
    private final int progress;
    private final Map<String, EntryPages> entries = new HashMap<>();
    private @Nullable Map<String, String> searchIndex;

    FoliosSession(FoliosBook book) {
        this(book, FoliosLayout.PAGE_WIDTH, FoliosLayout.PAGE_HEIGHT);
    }

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

    private FoliosPageElement.@org.jetbrains.annotations.Nullable Laid under(FoliosEntry.Page page, net.ixdarklord.folios.api.client.page.@org.jetbrains.annotations.Nullable FoliosIcon first) {
        try {
            layout.titleIcon(page.icon() != null ? page.icon() : first != null ? first : net.ixdarklord.folios.api.client.page.FoliosIcon.NONE);
            layout.takeMarks();
            List<FoliosPageElement.Laid> laid = page.page().lay(layout);
            layout.takeMarks();
            return laid.isEmpty() ? null : laid.getFirst();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private EntryPages lay(String id) {
        FoliosEntry entry = book.entry(id);
        List<FoliosPageElement.Laid> pages = new ArrayList<>();
        Map<String, Integer> markers = new HashMap<>();
        Map<String, FoliosLayout.Mark> places = new HashMap<>();
        if (entry == null) return new EntryPages(pages, markers, new int[0]);
        int[] starts = new int[entry.pages().size()];
        for (int index = 0; index < entry.pages().size(); index++) {
            FoliosEntry.Page page = entry.pages().get(index);
            if (page.lock().isOpen() && page.page().spread() && pages.size() % 2 == 1) pages.add(painter -> {});
            starts[index] = pages.size();
            if (!page.lock().isVisible()) continue;
            markers.put("@" + index, pages.size());
            if (page.marker() != null) markers.put(page.marker(), pages.size());
            if (!page.lock().isOpen()) {
                pages.add(Locked.page(page.lock(), (id + "#" + index).hashCode(), page.lock().peek() > 0 ? under(page, index == 0 ? entry.icon() : null) : null));
                continue;
            }
            int base = pages.size();
            try {
                layout.titleIcon(page.icon() != null ? page.icon() : index == 0 ? entry.icon() : net.ixdarklord.folios.api.client.page.FoliosIcon.NONE);
                layout.takeMarks();
                pages.addAll(page.page().lay(layout));
                for (Map.Entry<String, FoliosLayout.Mark> mark : layout.takeMarks().entrySet()) {
                    String name = mark.getKey().startsWith("@") ? "@" + index + "." + mark.getKey().substring(1) : mark.getKey();
                    FoliosLayout.Mark at = mark.getValue();
                    if (markers.putIfAbsent(name, base + at.page()) == null) places.put(name, new FoliosLayout.Mark(base + at.page(), at.top(), at.bottom()));
                }
            } catch (RuntimeException e) {
                Folios.LOGGER.error("Couldn't lay out page {} of the entry {} of the guide {}", index + 1, id, book.id(), e);
                pages.addAll(new ErrorPage("This page couldn't be shown: " + e).lay(layout));
            }
        }
        layout.titleIcon(net.ixdarklord.folios.api.client.page.FoliosIcon.NONE);
        if (pages.isEmpty()) pages.add(painter -> {});
        return new EntryPages(List.copyOf(pages), Map.copyOf(markers), starts, Map.copyOf(places));
    }

    public FoliosBook.Location location(String entry, int laid) {
        return new FoliosBook.Location(entry, entry(entry).source(laid));
    }

    public int pageOf(FoliosBook.Location location) {
        EntryPages pages = entry(location.entry());
        if (location.element() >= 0) {
            Integer at = pages.markers().get(elementMarker(location));
            if (at != null) return at;
        }
        return pages.start(location.page());
    }

    public static String elementMarker(FoliosBook.Location location) {
        return "@" + location.page() + "." + location.element();
    }

    public FoliosLayout.@Nullable Mark place(String entry, String marker) {
        return entry(entry).places().get(marker);
    }

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
        return new Target(entry, page, null, null, marker);
    }

    private final Map<String, java.util.Optional<net.ixdarklord.folios.api.client.page.FoliosPopup>> popups = new HashMap<>();

    public net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup popup(String link) {
        net.ixdarklord.folios.api.client.FoliosLinks.Found found = net.ixdarklord.folios.api.client.FoliosLinks.find(link);
        if (found == null || !found.kind().isOpen(book, found.target())) return null;
        return popups.computeIfAbsent(link, key -> java.util.Optional.ofNullable(found.kind().popup(layout, found.target()))).orElse(null);
    }

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

    public record Found(FoliosEntry entry, FoliosEntry.@Nullable Listing listing) {}

    public List<Found> search(String query) {
        String[] words = query.toLowerCase(Locale.ROOT).strip().split("\\s+");
        if (searchIndex == null) {
            searchIndex = FoliosText.in(book, () -> {
                Map<String, String> index = new HashMap<>();
                for (FoliosEntry entry : book.entries().values()) {
                    StringBuilder text = new StringBuilder();
                    for (FoliosEntry.Page page : entry.pages()) {
                        if (page.lock().isOpen()) text.append(' ').append(FoliosText.plain(page.page().searchText()));
                    }
                    index.put(entry.id(), text.toString().toLowerCase(Locale.ROOT));
                    for (FoliosEntry.Listing listing : entry.listings()) {
                        index.put(key(entry, listing), FoliosText.plain(listing.shown().searchText()).toLowerCase(Locale.ROOT));
                    }
                }
                return index;
            });
        }
        List<Found> named = new ArrayList<>();
        List<Found> mentioned = new ArrayList<>();
        for (FoliosEntry entry : book.entries().values()) {
            if (!entry.isOpen()) continue;
            FoliosCategory category = book.category(entry.category());
            if (category == null || !category.isOpen()) continue;
            match(words, entry.title(), searchIndex.getOrDefault(entry.id(), ""), new Found(entry, null), named, mentioned);
            for (FoliosEntry.Listing listing : entry.listings()) {
                if (!listing.isOpen(entry)) continue;
                Component title = FoliosText.in(book, () -> listing.title(entry));
                match(words, title, searchIndex.getOrDefault(key(entry, listing), ""), new Found(entry, listing), named, mentioned);
            }
        }
        named.addAll(mentioned);
        return named;
    }

    private static String key(FoliosEntry entry, FoliosEntry.Listing listing) {
        return entry.id() + "#@" + listing.page() + "." + listing.element();
    }

    private static void match(String[] words, Component title, String body, Found found, List<Found> named, List<Found> mentioned) {
        String name = title.getString().toLowerCase(Locale.ROOT);
        boolean inName = true;
        boolean anywhere = true;
        for (String word : words) {
            if (word.isEmpty()) continue;
            inName &= name.contains(word);
            anywhere &= name.contains(word) || body.contains(word);
        }
        if (inName) named.add(found);
        else if (anywhere) mentioned.add(found);
    }
}
