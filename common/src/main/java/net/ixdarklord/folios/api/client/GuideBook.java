package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.api.client.page.GuideIcon;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideStack;
import net.ixdarklord.folios.api.client.page.GuideText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A guide book, as read from {@code assets/<namespace>/folios/<path>/book.json} and the categories and entries beside
 * it. Books are read again whenever resources reload; don't keep one past that.
 *
 * @param name      raw, as written: a translation key or text ({@link #title()} reads it)
 * @param subtitle  raw; empty for none
 * @param landing   raw marked-up text of the book's first page
 * @param style     how the book is shown: {@link GuideStyles#SCREEN}, {@link GuideStyles#HANDHELD} or one a mod adds
 * @param accent    the book's colour, for marks, links and highlights (RGB)
 * @param cover     the colour of a handheld book's cover (RGB)
 * @param coverTexture a texture for a handheld book's cover, drawn over its colour; null for none
 * @param models    the models it is shown with: the item's, the open book's others see, a handheld book's cover
 * @param showProgress whether how much of it has been read is shown
 * @param tilt      how far a handheld book leans back from facing the reader, in degrees (0 faces them squarely)
 * @param distance  how far a handheld book is held from the reader's eyes, in blocks
 * @param pageTurn  how a handheld book's leaves turn
 * @param reveal    how a page's content shows once it is turned to ({@code "reveal"}; see {@link GuideReveal})
 * @param textures  the layers it is drawn with
 * @param layout    where the tabs, turn buttons and page numbers sit
 * @param clickOutsideCloses whether a click off the book closes it ({@code "close_on_click_outside"}; by default true for a handheld book, false for a screen one)
 * @param handling  what a reader can do while holding a handheld book open ({@code "handheld"})
 * @param colors    its colour scheme over the style's own: the page's colours and its tooltips' ({@code "colors"})
 * @param coverText where its name and subtitle are lettered on its cover, and how large ({@code "cover_text"})
 * @param progress  where what is read in it is kept: with each player, or on each copy of the book ({@code "progress"})
 */
public record GuideBook(ResourceLocation id, String name, String subtitle, String landing, ResourceLocation style, int accent, int cover,
                        @Nullable ResourceLocation coverTexture, GuideIcon icon, Models models, boolean showProgress, float tilt, float distance, PageTurn pageTurn, GuideReveal.Use reveal, Textures textures, Layout layout, boolean clickOutsideCloses, Handling handling, Colors colors, CoverText coverText,
                        Progress progress, List<GuideCategory> categories, Map<String, GuideEntry> entries, Map<Item, Location> itemIndex) {
    /** The item model the {@code folios:guide_book} item takes for this book; null for its own. */
    public @Nullable ResourceLocation itemModel() {
        return models.item();
    }

    /**
     * The models a book is shown with: {@code "models"} in book.json.
     * <pre>{@code
     * "models": {
     *   "item":  "mymod:handbook",              // the item model of the book's item (assets/mymod/items/handbook.json)
     *   "open":  "mymod:handbook_open",         // the item model while its holder reads it, as others see them
     *   "cover": "mymod:guide/handbook_cover",  // what sits on a handheld book's boards (assets/mymod/models/...); "none" for nothing
     *   "hands": true,                          // whether the reader's hands are drawn holding a handheld book
     *   "gui": "flat"                           // in inventory slots: "flat" for the flat icon, "model" for the book's model
     * }
     * }</pre>
     * The open model and the cover are in the format of block and item models. A cover model is laid on each board's
     * outside, mirrored on the left one: x from 0 at the spine to 16 at the board's outer edge, y from 0 at its foot to
     * 16 at its head, z from 0 on the board outwards in the same units as x; faces with a {@code "tintindex"} take the
     * book's cover colour. {@code "item_model"} beside {@code "models"} still works, for the item's.
     *
     * @param item  null for CoolCatLib's own book
     * @param open  null for CoolCatLib's own open book
     * @param cover null for none
     * @param guiModel whether CoolCatLib's book item shows its model in inventory slots rather than its flat icon
     */
    public record Models(@Nullable ResourceLocation item, @Nullable ResourceLocation open, @Nullable ResourceLocation cover, boolean hands, boolean guiModel) {
        public static final ResourceLocation DEFAULT_COVER = new ResourceLocation("folios", "guide/handheld_cover");
        public static final Models DEFAULT = new Models(null, null, DEFAULT_COVER, true, false);
    }

    /**
     * The texture layers a book is drawn with, in both styles: {@code "textures"} in book.json, each a texture path.
     * They are grey or white and tinted by the book's colours (paper by the paper's colour in light or dark mode, the
     * cover by {@code "cover"}, the frame by the page's lines, tabs by their state), so one set suits any book.
     * Folios's own are at {@code folios:textures/book/}: a resource pack replacing them changes every book
     * that doesn't name its own.
     *
     * @param page     the paper of each page, stretched to it (136 by 180)
     * @param frame    drawn over the paper, under what is on the page; its alpha is how strong each part is
     * @param cover    the cover around the pages, and a handheld book's boards
     * @param tab      a ribbon tab, its tip at the right; stretched to the tab's length, tinted by its category's colour
     * @param tabPlate drawn on a tab under its icon, tinted with the paper's colour, so the icon reads on any ribbon
     * @param backdrop drawn over the whole screen behind a screen book; null for the world, dimmed
     */
    public record Textures(ResourceLocation page, ResourceLocation frame, ResourceLocation cover, ResourceLocation tab, ResourceLocation tabPlate, @Nullable ResourceLocation backdrop) {
        public static final Textures DEFAULT = new Textures(rl("page"), rl("page_frame"), rl("cover"), rl("ribbon"), rl("ribbon_plate"), null);

        private static ResourceLocation rl(String name) {
            return new ResourceLocation("folios", "textures/book/" + name + ".png");
        }
    }

    /**
     * Where the things around a page's content sit, in page pixels, in both styles: {@code "layout"} in book.json.
     * <pre>{@code
     * "layout": {
     *   "tabs":         {"x": 0, "y": 6, "spacing": 2},  // out from the book's edge; down from the pages' top; between tabs
     *   "turn_buttons": {"x": 11, "y": 21},              // in from the page's outer edge; up from its bottom
     *   "page_numbers": {"x": 0, "y": 17},               // across from the page's middle; up from its bottom
     *   "close_button": {"x": 14, "y": 11}                // a page close clasp, where one is drawn (both styles close from their bar)
     * }
     * }</pre>
     * Any value left out keeps its default.
     */
    public record Layout(int tabX, int tabY, int tabSpacing, int buttonX, int buttonY, int numberX, int numberY, int closeX, int closeY) {
        public static final Layout DEFAULT = new Layout(0, 6, 2, 8, 19, 0, 17, 14, 11);
    }

    /**
     * What a reader can do while holding a handheld book open: {@code "handheld"} in book.json.
     * <pre>{@code
     * "handheld": {
     *   "look_around": true,  // holding the right mouse button turns the reader's head, the book with it
     *   "walk": true          // the movement keys walk, jump, sneak and sprint (the arrow keys still turn the leaves)
     * }
     * }</pre>
     */
    public record Handling(boolean lookAround, boolean walk) {
        public static final Handling DEFAULT = new Handling(true, true);
    }

    /**
     * A book's colour scheme: {@code "colors"} in book.json, each colour {@code "#RRGGBB"} (or {@code "#AARRGGBB"}).
     * <pre>{@code
     * "colors": {
     *   "paper": "#F1E7D0", "text": "#3B3026", "muted": "#8B7B66", "heading": "#2E241B",
     *   "link": "#5A3A8A", "panel": "#E6D8BA", "line": "#C4B08C",        // the page, in light mode (and a handheld book)
     *   "dark": {"paper": "#1F222C", "text": "#D5D8E2"},                // the same for a screen book's dark mode
     *   "tooltip": {"background": "#281E18", "frame": "#FFE28C", "title": "#F2E6CC", "detail": "#A89A86"}
     * }
     * }</pre>
     * Any colour left out keeps the style's own; the marks and rules are {@code "accent"}. A tooltip left as it is
     * takes on the book: its background from the cover, its frame gilt warmed by the accent.
     *
     * @param light   page colours by name, over the light (and handheld) palette
     * @param dark    page colours by name, over a screen book's dark palette
     * @param tooltip tooltip colours by name: background, frame, title, detail
     */
    public record Colors(Map<String, Integer> light, Map<String, Integer> dark, Map<String, Integer> tooltip) {
        public static final Colors DEFAULT = new Colors(Map.of(), Map.of(), Map.of());

        /** A palette with this scheme's colours for it laid over it. */
        public GuidePalette over(GuidePalette palette, boolean darkMode) {
            Map<String, Integer> colors = darkMode ? dark : light;
            if (colors.isEmpty()) return palette;
            return new GuidePalette(colors.getOrDefault("paper", palette.paper()), colors.getOrDefault("text", palette.text()),
                    colors.getOrDefault("muted", palette.muted()), colors.getOrDefault("heading", palette.heading()),
                    colors.getOrDefault("accent", palette.accent()), colors.getOrDefault("link", palette.link()),
                    colors.getOrDefault("panel", palette.panel()), colors.getOrDefault("line", palette.line()));
        }
    }

    /**
     * Where a book's name and subtitle are lettered on its cover, on the book item and a handheld book alike:
     * {@code "cover_text"} in book.json. Places are in the item model's units, the cover running from 3 to 13 across
     * and 1 (its foot) to 15 (its head) up, as the default model's front board does.
     * <pre>{@code
     * "cover_text": {
     *   "title":    {"x": 8, "y": 11.6, "size": 1.15, "width": 6.4},  // its middle; how large (1: a line 1.6 units tall); the room across
     *   "subtitle": {"x": 8, "y": 4.05, "size": 0.85, "width": 6.4}
     * }
     * }</pre>
     * A line longer than its room shrinks to fit. Anything left out keeps its default; a book that says nothing keeps
     * where its item model's {@code folios:guide_cover_text} puts them.
     *
     * With a {@code "summary"}, the name and the subtitle under it, smaller, share the title plate, and the summary
     * (up to two lines) has the lower plate.
     *
     * @param title    null for the model's own place
     * @param subtitle null for the model's own place (or, with a summary, under the title)
     * @param summary  where the summary goes; null for the model's own place
     * @param summaryText raw; empty for none
     */
    public record CoverText(@Nullable Line title, @Nullable Line subtitle, @Nullable Line summary, String summaryText) {
        public static final Line TITLE = new Line(8, 11.6F, 1.15F, 6.4F);
        public static final Line SUBTITLE = new Line(8, 4.05F, 0.85F, 6.4F);
        public static final CoverText DEFAULT = new CoverText(null, null, null, "");

        public CoverText(@Nullable Line title, @Nullable Line subtitle) {
            this(title, subtitle, null, "");
        }

        /** A line of lettering: its middle (x, y), how large, and how much room it has across. */
        public record Line(float x, float y, float size, float width) {}

        public Line titleOr(Line fallback) {
            return title != null ? title : fallback;
        }

        public Line subtitleOr(Line fallback) {
            return subtitle != null ? subtitle : fallback;
        }
    }

    /**
     * Where what is read in a book is kept ({@code "progress"} in book.json): the entries read, the pages shown, the
     * bookmarks and where the reader left off.
     */
    public enum Progress {
        /** With each player, on the server: theirs in any copy of the book, from any computer. */
        PLAYER,
        /**
         * On each copy of the book, like a notebook's: whoever reads that copy finds its bookmarks and read pages, and
         * a new copy starts fresh. Read pages still count for the player too (quests, celebrations). A book opened
         * without a copy in hand (by a command or from code) uses the player's.
         */
        BOOK
    }

    /** How a handheld book's leaves turn ({@code "page_turn"} in book.json). */
    public enum PageTurn {
        /** A stiff leaf swinging over the spine. */
        FLAT,
        /** A leaf that bends as it goes, its free edge trailing as it lifts and following as it lands. */
        BEND
    }

    /** A place in a book: an entry, and a page of it (0 for its first). */
    public record Location(String entry, int page) {}

    public GuideBook(ResourceLocation id, String name, String subtitle, String landing, ResourceLocation style, int accent, int cover,
                     @Nullable ResourceLocation coverTexture, GuideIcon icon, Models models, boolean showProgress, float tilt, float distance, PageTurn pageTurn, GuideReveal.Use reveal, Textures textures, Layout layout, boolean clickOutsideCloses, Handling handling, Colors colors, CoverText coverText,
                     Progress progress, List<GuideCategory> categories, List<GuideEntry> entries) {
        this(id, name, subtitle, landing, style, accent, cover, coverTexture, icon, models, showProgress, tilt, distance, pageTurn, reveal, textures, layout, clickOutsideCloses, handling, colors, coverText,
                progress, sortCategories(categories), sortEntries(entries), new HashMap<>());
    }

    private static List<GuideCategory> sortCategories(List<GuideCategory> categories) {
        List<GuideCategory> sorted = new ArrayList<>(categories);
        sorted.sort(Comparator.comparingInt(GuideCategory::sort).thenComparing(category -> category.title().getString()));
        return List.copyOf(sorted);
    }

    private static Map<String, GuideEntry> sortEntries(List<GuideEntry> entries) {
        List<GuideEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing((GuideEntry entry) -> !entry.featured()).thenComparingInt(GuideEntry::sort)
                .thenComparing(entry -> entry.title().getString()));
        Map<String, GuideEntry> byId = new LinkedHashMap<>();
        for (GuideEntry entry : sorted) byId.put(entry.id(), entry);
        return byId;
    }

    public Component title() {
        return GuideText.inline(name);
    }

    public @Nullable GuideCategory category(String id) {
        for (GuideCategory category : categories) {
            if (category.id().equals(id)) return category;
        }
        return null;
    }

    public @Nullable GuideEntry entry(String id) {
        return entries.get(id);
    }

    /** The categories the player can see, in order. */
    public List<GuideCategory> visibleCategories() {
        return categories.stream().filter(GuideCategory::isVisible).toList();
    }

    /** A category's entries the player can see, featured first. */
    public List<GuideEntry> entries(String category) {
        return entries.values().stream().filter(entry -> entry.category().equals(category) && entry.isVisible()).toList();
    }

    /** The page about an item, from the items its pages are about; null when none is. */
    public @Nullable Location about(ItemStack stack) {
        if (stack.isEmpty()) return null;
        synchronized (itemIndex) {
            if (itemIndex.isEmpty()) index();
            Location location = itemIndex.get(stack.getItem());
            return location != null && location.entry().isEmpty() ? null : location;
        }
    }

    // The index is built the first time it's needed in a world, when items can be resolved.
    private void index() {
        for (GuideEntry entry : entries.values()) {
            for (int page = 0; page < entry.pages().size(); page++) {
                for (GuideStack stack : entry.pages().get(page).page().items()) {
                    for (ItemStack item : stack.stacks()) {
                        if (!item.isEmpty()) itemIndex.putIfAbsent(item.getItem(), new Location(entry.id(), page));
                    }
                }
            }
        }
        // Remembers that the index was built, even when nothing is in it.
        itemIndex.putIfAbsent(net.minecraft.world.item.Items.AIR, new Location("", 0));
    }
}
