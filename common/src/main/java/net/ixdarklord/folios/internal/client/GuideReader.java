package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.GuideReveal;
import net.ixdarklord.folios.api.client.GuideReveals;
import net.ixdarklord.folios.api.client.GuideCategory;
import net.ixdarklord.folios.api.client.GuideEntry;
import net.ixdarklord.folios.api.client.GuideObjective;
import net.ixdarklord.folios.api.client.page.GuideIcon;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideText;
import net.ixdarklord.folios.internal.client.page.QuestPageElement;
import net.ixdarklord.folios.internal.client.page.Shapes;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

// Reading a book a spread at a time, as both kinds of book do: what it is open at, where the reader has been, and
// the leaves in the air while a spread changes. Its pages are the book's own (entries, laid out once per session) and
// pages it makes for itself: a title page with the contents, each category's list of entries, search results and
// bookmarks. Each part of the book starts on a fresh spread.
public class GuideReader implements AbstractPainter.Navigator {
    public static final float FLIP_MILLIS = 420;
    private static final int ROW = 18;

    public final GuideBook book;
    public GuideSession session;
    public final GuideMemory memory;
    private final long openedAt = Util.getMillis();
    private final Map<String, List<GuidePage.Laid>> chapters = new HashMap<>();
    private final Deque<Spot> history = new ArrayDeque<>();

    /** A place in the book: a part of it ("home", "category:<id>", "entry:<id>", "search:<words>", "bookmarks", "index") and the left page of a spread. */
    public record Spot(String chapter, int page) {}

    /** Leaves in the air: what was open and what is opening (their pages and spreads), and which way they turn. */
    public record Flip(GuidePage.@Nullable Laid oldLeft, GuidePage.@Nullable Laid oldRight, GuidePage.@Nullable Laid newLeft,
                       GuidePage.@Nullable Laid newRight, boolean forward, long start, Spot from, Spot to) {
        /** The spread whose left page lies to the left while the leaf turns, and whose right page lies to the right. */
        public Spot staticLeft() {
            return forward ? from : to;
        }

        public Spot staticRight() {
            return forward ? to : from;
        }

        /** The spread the leaf's face (a right page) belongs to, and its back's (a left page). */
        public Spot face() {
            return forward ? from : to;
        }

        public Spot back() {
            return forward ? to : from;
        }
    }

    public Spot spot;
    /** How the book's ribbon tabs move. */
    public final TabMotion tabs = new TabMotion();
    public @Nullable Flip flip;

    public GuideReader(GuideBook book, GuideSession session, GuideBook.@Nullable Location at) {
        this(book, session, at, null);
    }

    /**
     * A reader of a book opened from a copy in {@code hand} (null for none: a command, a link, from code): a book that
     * keeps its own progress reads and keeps that copy's.
     */
    public GuideReader(GuideBook book, GuideSession session, GuideBook.@Nullable Location at, net.minecraft.world.@Nullable InteractionHand hand) {
        this.book = book;
        this.session = session;
        this.memory = GuideMemory.forReading(book, hand);
        READ_BEFORE.computeIfAbsent(book.id(), id -> {
            java.util.Set<String> read = new java.util.HashSet<>();
            for (GuideEntry entry : book.entries().values()) if (memory.isRead(entry.id())) read.add(entry.id());
            return read;
        });
        GuideBook.Location start = at != null ? at : memory.last();
        GuideEntry entry = start != null ? book.entry(start.entry()) : null;
        this.spot = new Spot("home", 0);
        if (entry != null && entry.isOpen()) {
            history.push(spot);
            this.spot = new Spot("entry:" + entry.id(), even(session.pageOf(start)));
            memory.markRead(entry.id());
        } else if (at == null && this.resumable(memory.lastChapter())) {
            // Left on a spread that isn't an entry's: back there, if it is still in the book.
            String chapter = memory.lastChapter();
            int page = even(Math.max(0, memory.lastPage()));
            if (page >= this.chapter(chapter).size()) page = 0;
            if (!chapter.equals("home")) history.push(spot);
            this.spot = new Spot(chapter, page);
        }
    }

    /**
     * Reads on from pages laid out to another size (the screen's GUI scale or window changed): at the same place, an
     * entry's page found again by the page of its JSON it came from, any other part at the same page as far as it goes.
     */
    public void useSession(GuideSession next) {
        if (next == session) return;
        GuideBook.Location here = this.here();
        session = next;
        chapters.clear();
        flip = null;
        int page = here != null ? even(session.pageOf(here)) : spot.page;
        spot = new Spot(spot.chapter, Math.max(0, Math.min(even(page), even(Math.max(0, this.chapter(spot.chapter).size() - 1)))));
    }

    // Whether a spread the reader left on can be gone back to: the front, the index, the bookmarks, or a category the
    // book still has open to them (not a search, made from what was typed).
    private boolean resumable(@Nullable String chapter) {
        if (chapter == null) return false;
        if (chapter.equals("home") || chapter.equals("index") || chapter.equals("bookmarks")) return true;
        if (!chapter.startsWith("category:")) return false;
        GuideCategory category = book.category(chapter.substring(9));
        return category != null && category.isOpen();
    }

    private static int even(int page) {
        return page - page % 2;
    }

    public float seconds() {
        return (Util.getMillis() - openedAt) / 1000.0F;
    }

    // ---- Revealing a spread's content ----

    // When the spread now open started showing its content.
    private long revealAt = Util.getMillis();

    /** Whether the open spread's reveal waits to start: its pages aren't shown yet. */
    protected boolean revealHeld() {
        return false;
    }

    /** Starts the open spread's reveal again from the beginning (as a handheld book comes open). */
    public void reveal() {
        revealAt = Util.getMillis();
    }

    /** The reveal a spread's pages use: its entry's own, or the book's. */
    public GuideReveal.Use revealOf(Spot at) {
        if (at.chapter.startsWith("entry:")) {
            GuideEntry entry = book.entry(at.chapter.substring(6));
            if (entry != null && entry.reveal() != null) return entry.reveal();
        }
        return book.reveal();
    }

    /**
     * The painter to draw a page's content through, for a page of the spread at {@code at}: while a leaf turns, the
     * spread it brings is hidden (its effect at the start), and once it is down its content shows as the effect has
     * it. The page's own painter when there is nothing to reveal.
     */
    public GuidePainter revealing(GuidePainter painter, Spot at, boolean right) {
        GuidePage.Laid page = this.page(at, right ? 1 : 0);
        float progress = this.revealProgress(at, page, right);
        return progress >= 1 ? painter : net.ixdarklord.folios.internal.client.paint.RevealPainter.of(painter, GuideReveals.get(this.revealOf(at).effect()),
                progress, this.revealX(page, right));
    }

    // Where a page is across what is revealed together: a spread's halves across the spread, a plain page on its own.
    private float revealX(GuidePage.@Nullable Laid page, boolean right) {
        return right && page instanceof GuidePage.SpreadHalf ? session.layout().pageWidth() : 0;
    }

    /**
     * Draws a page of the spread at {@code at} (its right page, or its left) as its reveal has it now, with what its
     * effect draws over it.
     */
    public void drawRevealed(GuidePainter painter, GuidePage.Laid page, Spot at, boolean right) {
        float progress = this.revealProgress(at, page, right);
        if (progress >= 1) page.draw(painter);
        else net.ixdarklord.folios.internal.client.paint.RevealPainter.draw(painter, page, GuideReveals.get(this.revealOf(at).effect()), progress,
                this.revealX(page, right));
    }

    // How far the reveal of a page of a spread has gone: 1 when there is nothing (more) to reveal. A spread page's
    // halves reveal together; plain pages one after the other, the left then the right, each over the effect's length.
    private float revealProgress(Spot at, GuidePage.@Nullable Laid page, boolean right) {
        GuideReveal.Use use = this.revealOf(at);
        if (use.seconds() <= 0 || !net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings.transitions()
                || use.once() && this.seen(at) || use.unread() && memory.isSeen(this.spreadKey(at))) {
            // Nothing to reveal: the spread open now shows in full at once.
            if (at.equals(spot) && flip == null && !this.revealHeld()) this.shown(at);
            return 1;
        }
        if (flip != null) return at.equals(flip.to()) ? 0 : 1;
        // Not yet shown (a handheld book's cover still opening): its reveal is still to come.
        if (this.revealHeld() && at.equals(spot)) return 0;
        if (!at.equals(spot)) return 1;
        float elapsed = (Util.getMillis() - revealAt) / (use.seconds() * 1000.0F);
        boolean together = page instanceof GuidePage.SpreadHalf;
        // Shown in full, the whole spread: it has been seen, for reveals that play once (this game) or on unread pages.
        if (elapsed >= (together ? 1 : 2)) {
            SEEN.add(this.seenKey(at));
            this.shown(at);
        }
        return Math.max(0, Math.min(1, together || !right ? elapsed : elapsed - 1));
    }

    // The spreads seen this game, by book, part and page; and for each book, the entries read before it was first
    // opened this game (their pages count as seen).
    private static final java.util.Set<String> SEEN = new java.util.HashSet<>();
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, java.util.Set<String>> READ_BEFORE = new java.util.HashMap<>();

    // A spread as the reader's memory knows it (for reveals that play only on unread pages): "<chapter>|<page>".
    private String spreadKey(Spot at) {
        return at.chapter + "|" + at.page;
    }

    // A spread shown in full: kept as read, unless it is made from what was typed (a search), which can't come again.
    private void shown(Spot at) {
        if (at.chapter.equals("home") || at.chapter.equals("index") || at.chapter.equals("bookmarks")
                || at.chapter.startsWith("category:") || at.chapter.startsWith("entry:")) memory.markSeen(this.spreadKey(at));
    }

    private String seenKey(Spot at) {
        return book.id() + "|" + at.chapter + "|" + at.page;
    }

    private boolean seen(Spot at) {
        if (SEEN.contains(this.seenKey(at))) return true;
        if (!at.chapter.startsWith("entry:")) return false;
        java.util.Set<String> before = READ_BEFORE.get(book.id());
        return before != null && before.contains(at.chapter.substring(6));
    }

    /** Whether the reader can move about now (a handheld book can't while it is being raised or shut). */
    protected boolean canMove() {
        return true;
    }

    // ---- The parts of the book ----

    public List<GuidePage.Laid> chapter(String key) {
        List<GuidePage.Laid> pages = chapters.get(key);
        if (pages != null) return pages;
        if (key.equals("home")) pages = this.homePages();
        else if (key.startsWith("category:")) pages = this.categoryPages(key.substring(9));
        else if (key.startsWith("entry:")) pages = session.entry(key.substring(6)).pages();
        else if (key.startsWith("search:")) pages = this.searchPages(key.substring(7));
        else if (key.equals("bookmarks")) pages = this.bookmarkPages();
        else if (key.equals("index")) pages = this.indexPages();
        else pages = List.of();
        if (pages.isEmpty()) pages = List.of(painter -> {});
        // What changes as the reader reads is made again each time it is opened.
        if (!key.startsWith("search:") && !key.equals("bookmarks") && !key.equals("index")) chapters.put(key, pages);
        return pages;
    }

    public GuidePage.@Nullable Laid page(Spot at, int offset) {
        List<GuidePage.Laid> pages = this.chapter(at.chapter);
        int index = at.page + offset;
        return index >= 0 && index < pages.size() ? pages.get(index) : null;
    }

    private List<GuidePage.Laid> homePages() {
        List<GuidePage.Laid> pages = new ArrayList<>();
        GuideLayout layout = session.layout();
        // Mastery, when the book has quests: how many of all their objectives are done, under its name.
        List<GuideObjective> all = new ArrayList<>();
        for (GuideCategory category : book.visibleCategories()) {
            for (GuideEntry entry : book.entries(category.id())) all.addAll(entry.objectives());
        }
        boolean mastery = !all.isEmpty();
        int titleBottom = mastery ? 134 : 96;
        List<GuideText> landing = book.landing().isEmpty() ? List.of()
                : layout.text(book.landing()).split(layout.contentBottom() - titleBottom, layout.contentBottom() - GuideLayout.CONTENT_TOP);
        GuideText first = landing.isEmpty() ? null : landing.get(0);
        pages.add(painter -> {
            GuidePalette palette = painter.palette();
            int middle = painter.pageWidth() / 2;
            medallion(painter, middle - 22, 12, 44);
            painter.push();
            painter.translate(middle - 16, 18);
            painter.scale(2);
            book.icon().draw(painter, 0, 0);
            painter.pop();
            int contentWidth = painter.contentRight() - GuideLayout.CONTENT_LEFT;
            List<FormattedCharSequence> title = painter.font().split(book.title().copy().withStyle(ChatFormatting.BOLD), contentWidth);
            int y = 62;
            for (FormattedCharSequence line : title.subList(0, Math.min(2, title.size()))) {
                painter.text(line, middle - painter.font().width(line) / 2, y, palette.heading());
                y += 10;
            }
            if (!book.subtitle().isEmpty()) {
                // On one line, whole: made smaller as far as it must be to fit.
                FormattedCharSequence subtitle = GuideText.inline(book.subtitle()).copy().withStyle(ChatFormatting.ITALIC).getVisualOrderText();
                int wide = painter.font().width(subtitle);
                float fit = Math.min(1, contentWidth / (float) Math.max(1, wide));
                painter.push();
                painter.translate(middle - wide * fit / 2, y + 1 + (1 - fit) * 4);
                painter.scale(fit);
                painter.text(subtitle, 0, 0, palette.muted());
                painter.pop();
            }
            if (mastery) this.mastery(painter, middle, titleBottom - 45, all);
            flourish(painter, middle, titleBottom - 6);
            if (first != null && !first.isEmpty()) first.draw(painter, GuideLayout.CONTENT_LEFT, titleBottom);
        });
        for (int index = 1; index < landing.size(); index++) {
            GuideText piece = landing.get(index);
            pages.add(painter -> piece.draw(painter, GuideLayout.CONTENT_LEFT, GuideLayout.CONTENT_TOP));
        }
        List<Row> rows = new ArrayList<>();
        for (GuideCategory category : book.visibleCategories()) {
            rows.add(new Row(category.isOpen() ? category.title() : Component.literal("???"), category.isOpen() ? category.icon() : GuideIcon.NONE,
                    category.isOpen() ? "category:" + category.id() : null, () -> category.isOpen() && this.unread(category.id()), category.id()));
        }
        pages.addAll(this.rows(Component.translatableWithFallback("folios.categories", "Categories"), null, rows));
        return pages;
    }

    // A framed slot for the book's icon on its first page: a rounded rim, a ring of the book's colour just inside it,
    // and the face, each with the same soft corners so the rings run evenly round them; a faint shade along the face's
    // top inside, as if it were sunk a little.
    private static void medallion(GuidePainter painter, int x, int y, int size) {
        GuidePalette palette = painter.palette();
        int rim = GuidePalette.mix(palette.line(), palette.text(), 0.2F);
        int ring = GuidePalette.mix(palette.accent(), palette.line(), 0.2F);
        int face = GuidePalette.mix(palette.panel(), palette.paper(), 0.3F);
        rounded(painter, x, y, x + size, y + size, rim);
        rounded(painter, x + 1, y + 1, x + size - 1, y + size - 1, ring);
        rounded(painter, x + 3, y + 3, x + size - 3, y + size - 3, rim);
        rounded(painter, x + 4, y + 4, x + size - 4, y + size - 4, face);
        painter.fill(x + 6, y + 4, x + size - 6, y + 5, GuidePalette.mix(face, palette.line(), 0.35F));
    }

    // A rectangle with its corners rounded off by three pixels.
    private static void rounded(GuidePainter painter, int x0, int y0, int x1, int y1, int color) {
        painter.fill(x0 + 3, y0, x1 - 3, y1, color);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, color);
        painter.fill(x0, y0 + 3, x1, y1 - 3, color);
    }

    // The mastery bar: a trophy and "Mastery" over it, under them "Progression:" with how far along it is in percent (a
    // tick once all is done), then the bar of all the book's objectives, centred under the book's name.
    private void mastery(GuidePainter painter, int middle, int y, List<GuideObjective> all) {
        GuidePalette palette = painter.palette();
        int done = 0;
        for (GuideObjective objective : all) if (objective.isDone(book)) done++;
        boolean complete = done == all.size();
        float progress = GuideObjective.progress(book, all);
        Component title = Component.translatableWithFallback("folios.mastery.title", "Mastery").withStyle(ChatFormatting.BOLD);
        int titleWidth = 9 + 3 + painter.font().width(title);
        int left = middle - titleWidth / 2;
        int cup = complete ? 0xFFE0B23A : GuidePalette.mix(palette.muted(), palette.accent(), 0.5F);
        // Mastered, the trophy cycles through a rainbow; until then it is quiet, a highlight down its cup's left.
        if (complete) {
            TROPHY.rainbow(painter, left, y - 1, painter.time(), 0xFFE0B23A);
        } else {
            net.ixdarklord.folios.internal.client.screen.GuideBar.pixels(painter, TROPHY, left, y - 1, 9, cup);
            painter.fill(left + 2, y, left + 3, y + 3, GuidePalette.mix(cup, 0xFFFFFFFF, 0.45F));
        }
        if (complete) {
            int frame = TROPHY_SHEEN.sweepFrame(painter.time(), 2.4F, 0.5F);
            if (frame >= 0) TROPHY_SHEEN.draw(painter, left, y - 1, frame, 0xFFFFFFFF);
        }
        painter.text(title, left + 12, y, palette.heading());
        Component label = Component.translatableWithFallback("folios.mastery.progression", "Progression:");
        Component percent = Component.literal(complete ? "100% \u2714" : Math.round(100 * progress) + "%");
        int labelWidth = painter.font().width(label);
        int line = labelWidth + 4 + painter.font().width(percent);
        int lineLeft = middle - line / 2;
        painter.text(label, lineLeft, y + 11, palette.muted());
        painter.text(percent, lineLeft + labelWidth + 4, y + 11, complete ? 0xFF3E8E41 : palette.heading());
        int bar = 96;
        QuestPageElement.gilded(painter, middle - bar / 2, y + 22, bar, progress);
        if (painter.hovered(middle - bar / 2 - 1, y - 2, bar + 2, 32)) {
            painter.tooltip(this.masteryTooltip(done, all));
        }
    }

    // The mastery bar's tooltip: how far along overall, how many quests are done, and each quest with its own count.
    private List<Component> masteryTooltip(int done, List<GuideObjective> all) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatableWithFallback("folios.mastery.title", "Mastery").withStyle(ChatFormatting.BOLD));
        int percent = Math.round(100 * GuideObjective.progress(book, all));
        lines.add(Component.translatableWithFallback("folios.mastery.objectives", "%s of %s objectives done (%s%%)", done, all.size(), percent));
        List<GuideEntry> quests = new ArrayList<>();
        for (GuideCategory category : book.visibleCategories()) {
            for (GuideEntry entry : book.entries(category.id())) if (entry.isQuest()) quests.add(entry);
        }
        int finished = 0;
        for (GuideEntry quest : quests) if (quest.objectives().stream().allMatch(objective -> objective.isDone(book))) finished++;
        lines.add(Component.translatableWithFallback("folios.mastery.quests", "%s of %s quests complete", finished, quests.size()));
        int shown = 0;
        for (GuideEntry quest : quests) {
            if (shown++ == 6) {
                lines.add(Component.translatableWithFallback("folios.mastery.more", "and %s more", quests.size() - 6).withStyle(ChatFormatting.ITALIC));
                break;
            }
            int ticked = 0;
            for (GuideObjective objective : quest.objectives()) if (objective.isDone(book)) ticked++;
            boolean questDone = ticked == quest.objectives().size();
            Component name = quest.isOpen() ? quest.title() : Component.literal("???");
            lines.add(Component.literal(questDone ? " \u2714 " : " \u2022 ").withStyle(style -> style.withColor(questDone ? 0xFF6FCF73 : 0xFFA89A86))
                    .append(name.copy().withStyle(style -> style.withColor(questDone ? 0xFFA89A86 : 0xFFE0D4BC)))
                    .append(Component.literal(questDone ? "" : "  " + ticked + "/" + quest.objectives().size()).withStyle(style -> style.withColor(0xFF8E8270))));
        }
        if (quests.isEmpty() || finished < quests.size()) {
            lines.add(Component.translatableWithFallback("folios.mastery.hint", "Finish every quest to master the book").withStyle(ChatFormatting.ITALIC));
        } else {
            lines.add(Component.translatableWithFallback("folios.mastery.done", "Mastered").withStyle(ChatFormatting.ITALIC).withStyle(style -> style.withColor(0xFFE0B23A)));
        }
        return lines;
    }

    // A small trophy, every pixel solid: a cup with a rim, loop handles, a stem and a foot.
    private static final net.ixdarklord.folios.internal.client.paint.PixelIcon TROPHY = net.ixdarklord.folios.internal.client.paint.PixelIcon.of("trophy", 9, 9);
    /** A band of light sweeping across the trophy once the book is mastered: a flipbook over it. */
    private static final net.ixdarklord.folios.internal.client.paint.PixelIcon TROPHY_SHEEN = net.ixdarklord.folios.internal.client.paint.PixelIcon.of("trophy_sheen", 9, 9, 10);

    // A divider under the book's name: two rules running out from a diamond, each ending in a dot.
    private static void flourish(GuidePainter painter, int middle, int y) {
        GuidePalette palette = painter.palette();
        int line = GuidePalette.mix(palette.line(), palette.accent(), 0.35F);
        painter.fill(middle - 34, y, middle - 5, y + 1, line);
        painter.fill(middle + 5, y, middle + 34, y + 1, line);
        painter.fill(middle - 37, y - 1, middle - 35, y + 2, line);
        painter.fill(middle + 35, y - 1, middle + 37, y + 2, line);
        int mark = palette.accent();
        painter.fill(middle - 1, y - 2, middle + 1, y + 3, mark);
        painter.fill(middle - 3, y, middle + 3, y + 1, mark);
        painter.fill(middle - 2, y - 1, middle + 2, y + 2, mark);
    }

    private List<GuidePage.Laid> categoryPages(String id) {
        GuideCategory category = book.category(id);
        if (category == null) return List.of();
        List<Row> rows = new ArrayList<>();
        for (GuideEntry entry : book.entries(id)) rows.add(this.entryRow(entry));
        return this.rows(category.title(), category.description().isEmpty() ? null : category.description(), rows);
    }

    private List<GuidePage.Laid> searchPages(String query) {
        List<Row> rows = new ArrayList<>();
        for (GuideEntry entry : session.search(query)) rows.add(this.entryRow(entry));
        String heading = rows.isEmpty() ? "Nothing matches \"" + query + "\"." : null;
        return this.rows(Component.translatableWithFallback("folios.search.results", "Search"), heading, rows);
    }

    private List<GuidePage.Laid> bookmarkPages() {
        List<Row> rows = new ArrayList<>();
        for (GuideBook.Location location : memory.bookmarks()) {
            GuideEntry entry = book.entry(location.entry());
            if (entry == null || !entry.isOpen()) continue;
            rows.add(new Row(entry.title(), entry.icon(), "bookmark:" + location.entry() + "#" + location.page(), () -> false, null));
        }
        return this.rows(Component.translatableWithFallback("folios.bookmarks", "Bookmarks"),
                rows.isEmpty() ? "Nothing bookmarked yet." : null, rows);
    }

    // Every entry of the book, A to Z, wherever it is: those not yet open as "???" at the end.
    private List<GuidePage.Laid> indexPages() {
        List<GuideEntry> entries = new ArrayList<>();
        for (GuideCategory category : book.visibleCategories()) {
            if (category.isOpen()) entries.addAll(book.entries(category.id()));
        }
        entries.sort(java.util.Comparator.comparing((GuideEntry entry) -> !entry.isOpen()).thenComparing(entry -> entry.title().getString().toLowerCase(java.util.Locale.ROOT)));
        List<Row> rows = new ArrayList<>();
        for (GuideEntry entry : entries) rows.add(this.entryRow(entry));
        return this.rows(Component.translatableWithFallback("folios.index", "Index"), rows.isEmpty() ? "Nothing to list yet." : null, rows);
    }

    private Row entryRow(GuideEntry entry) {
        Row row = new Row(entry.isOpen() ? entry.title() : Component.literal("???"), entry.isOpen() ? entry.icon() : GuideIcon.NONE,
                entry.isOpen() ? "entry:" + entry.id() : null, () -> entry.isOpen() && !memory.isRead(entry.id()), null);
        // A quest shows how far along it is.
        return entry.isOpen() && entry.isQuest() ? row.withQuest(entry.objectives()) : row;
    }

    private boolean unread(String category) {
        return book.entries(category).stream().anyMatch(entry -> entry.isOpen() && !memory.isRead(entry.id()));
    }

    /**
     * A row of a list: what it says, its icon, where it goes (null for locked), whether to mark it as new, the
     * category it opens (its read progress is shown), and the objectives of the quest it opens.
     */
    private record Row(Component label, GuideIcon icon, @Nullable String link, BooleanSupplier fresh, @Nullable String category,
                       List<GuideObjective> quest) {
        Row(Component label, GuideIcon icon, @Nullable String link, BooleanSupplier fresh, @Nullable String category) {
            this(label, icon, link, fresh, category, List.of());
        }

        Row withQuest(List<GuideObjective> objectives) {
            return new Row(label, icon, link, fresh, category, objectives);
        }
    }

    // A titled list, with text over it, as many pages as it takes.
    private List<GuidePage.Laid> rows(Component title, @Nullable String text, List<Row> rows) {
        GuideLayout layout = session.layout();
        List<GuidePage.Laid> pages = new ArrayList<>();
        GuideText description = text == null ? null : layout.text(text);
        int top = GuideLayout.CONTENT_TOP + GuideLayout.TITLE_HEIGHT + (description != null ? description.height() + 8 : 0);
        int index = 0;
        boolean firstPage = true;
        do {
            int y = firstPage ? top : GuideLayout.CONTENT_TOP;
            int fits = Math.max(1, (layout.contentBottom() - y) / ROW);
            List<Row> on = rows.subList(index, Math.min(rows.size(), index + fits));
            index += on.size();
            boolean titled = firstPage;
            pages.add(painter -> {
                if (titled) {
                    GuideLayout.title(painter, title, GuideLayout.CONTENT_TOP);
                    if (description != null) description.draw(painter, GuideLayout.CONTENT_LEFT, GuideLayout.CONTENT_TOP + GuideLayout.TITLE_HEIGHT);
                }
                int rowY = titled ? top : GuideLayout.CONTENT_TOP;
                for (Row row : on) {
                    this.row(painter, row, rowY);
                    rowY += ROW;
                }
            });
            firstPage = false;
        } while (index < rows.size());
        return pages;
    }

    private void row(GuidePainter painter, Row row, int y) {
        GuidePalette palette = painter.palette();
        int left = GuideLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        boolean over = row.link != null && painter.action(left - 2, y, right - left + 4, ROW - 1, null, () -> this.follow(row.link));
        if (over) painter.fill(left - 2, y, right + 2, y + ROW - 1, GuidePalette.alpha(palette.accent(), 0x2A));
        if (!row.icon.isEmpty()) {
            painter.push();
            painter.translate(left, y + 1);
            row.icon.draw(painter, 0, 0);
            painter.pop();
        } else {
            painter.centeredText(Component.literal("?"), left + 8, y + 5, palette.muted());
        }
        boolean fresh = row.fresh.getAsBoolean();
        int textRight = right - (fresh ? 10 : 0);
        // How far along it is: a count at the right and a bar along its foot, for a category's reading or a quest.
        float progress = -1;
        int steps = 1;
        Component count = null;
        if (row.category != null && book.showProgress() && row.link != null) {
            int[] read = this.progress(row.category);
            count = Component.literal(read[0] + "/" + read[1]);
            progress = read[1] == 0 ? 0 : read[0] / (float) read[1];
            steps = read[1];
        } else if (!row.quest.isEmpty()) {
            int done = 0;
            for (GuideObjective objective : row.quest) if (objective.isDone(book)) done++;
            progress = GuideObjective.progress(book, row.quest);
            steps = row.quest.size();
            count = done == row.quest.size() ? Component.literal("✔") : Component.literal(done + "/" + row.quest.size());
        }
        boolean complete = progress >= 1;
        if (count != null) {
            int width = painter.font().width(count);
            painter.text(count, textRight - width, y + 5, complete ? 0xFF3E8E41 : palette.muted());
            textRight -= width + 4;
        }
        FormattedCharSequence shown = painter.font().split(row.label, Math.max(10, textRight - left - 20)).stream().findFirst().orElse(FormattedCharSequence.EMPTY);
        painter.text(shown, left + 20, y + 5, row.link == null ? palette.muted() : over ? palette.heading() : palette.text());
        // Unread: a little four-pointed sparkle at the row's end, blinking now and then (each row at its own moment).
        if (fresh) net.ixdarklord.folios.internal.client.screen.GuideBar.sparkle(painter, right - 7, y + (ROW - 1 - 7) / 2, y * 0.037F,
                palette.accent() | 0xFF000000);
        if (progress >= 0) {
            QuestPageElement.stepped(painter, left + 20, y + ROW - 5, right - left - 20, progress, steps, complete ? 0xFF4CAF50 : palette.accent());
        } else {
            painter.fill(left + 20, y + ROW - 2, right, y + ROW - 1, GuidePalette.alpha(palette.line(), 0x60));
        }
    }

    /**
     * What is printed round a page's content: its number, the clasp in its lower outer corner that turns back (on a
     * left page) or on (on a right page), and on a right page of a book that can be shut, the clasp at its head that
     * shuts it. They belong to the page, so they turn with it; they are pressable only on a settled spread. Where the
     * book's layout puts them ({@link GuideBook.Layout}).
     *
     * @param shut what the shut clasp does; null for a book with none
     */
    public void chrome(GuidePainter painter, Spot spot, boolean right, boolean pressable, @Nullable Runnable shut) {
        GuidePalette palette = painter.palette();
        GuideBook.Layout layout = book.layout();
        int width = painter.pageWidth();
        int size = Shapes.BUTTON;
        // The shut clasp is the book's, there whether or not the spread has a right-hand page.
        if (right && shut != null) {
            int x = width - layout.closeX() - size;
            int top = layout.closeY();
            boolean over = pressable && painter.action(x, top - 1, size, size + 1, Component.translatableWithFallback("folios.shut", "Close the book"), shut);
            Shapes.button(painter, x, top, Shapes.Icon.CLOSE, over);
        }
        GuidePage.Laid laid = this.page(spot, right ? 1 : 0);
        if (laid == null) return;
        int bottom = painter.pageHeight();
        if (laid instanceof GuidePage.SpreadHalf) {
            // One number for the spread, at the fold, each page printing its own part of it.
            Shapes.acrossFold(painter, Component.literal(String.valueOf(spot.page + 1)).getVisualOrderText(), bottom - layout.numberY(), palette.muted(), right);
        } else painter.centeredText(Component.literal(String.valueOf(spot.page + (right ? 2 : 1))), width / 2 + layout.numberX(), bottom - layout.numberY(), palette.muted());
        int y = bottom - layout.buttonY();
        if (!right && (spot.page > 0 || !history.isEmpty())) {
            boolean back = spot.page == 0;
            int x = layout.buttonX();
            Component tooltip = back ? Component.translatableWithFallback("folios.back", "Back")
                    : Component.translatableWithFallback("folios.previous", "Previous page");
            boolean over = pressable && painter.action(x, y - 1, size, size + 1, tooltip, () -> this.turn(-1));
            if (over) painter.tooltip(net.ixdarklord.folios.internal.client.GuideTooltips.of(tooltip, back ? Component.translatableWithFallback("folios.tip.back", "Backspace or the side mouse button") : Component.translatableWithFallback("folios.tip.turn", "Arrow keys or the mouse wheel")));
            Shapes.button(painter, x, y, back ? Shapes.Icon.BACK : Shapes.Icon.PREVIOUS, over);
        } else if (right && spot.page + 2 < this.chapter(spot.chapter).size()) {
            int x = width - layout.buttonX() - size;
            Component next = Component.translatableWithFallback("folios.next", "Next page");
            boolean over = pressable && painter.action(x, y - 1, size, size + 1, next, () -> this.turn(1));
            if (over) painter.tooltip(net.ixdarklord.folios.internal.client.GuideTooltips.of(next, Component.translatableWithFallback("folios.tip.turn", "Arrow keys or the mouse wheel")));
            Shapes.button(painter, x, y, Shapes.Icon.NEXT, over);
        }
    }

    /**
     * A tab's colour: its category's own (or the book's accent, for the front, bookmarks and categories without one),
     * full where the reader is, a little deeper under the pointer, and deeper still towards the cover otherwise.
     */
    public int tabColor(@Nullable GuideCategory category, GuidePalette palette, boolean here, boolean hovered) {
        return this.tabColor(category, palette, here ? 1 : hovered ? 0.55F : 0);
    }

    /** A tab's colour as it moves: from its resting colour at 0 to its full one at 1 ({@link TabMotion#out}). */
    public int tabColor(@Nullable GuideCategory category, GuidePalette palette, float out) {
        int base = category != null && category.color() >= 0 ? 0xFF000000 | category.color() : palette.accent() | 0xFF000000;
        // Past 1 as it overshoots it glows a little brighter; knocked in below 0 it stays at its resting colour.
        float t = Math.max(0, out);
        if (t > 1) return GuidePalette.mix(base, 0xFFFFFFFF, 0.3F * (t - 1));
        return GuidePalette.mix(base, 0xFF000000 | book.cover(), 0.4F * (1 - t));
    }

    /** What a tab's tooltip says under its name: how much of a category has been read, what the others hold. */
    public @Nullable Component tabDetail(@Nullable GuideCategory category, String part) {
        if (category != null) {
            int[] read = this.progress(category.id());
            return read[1] == 0 ? null : read[0] == read[1] ? Component.translatableWithFallback("folios.tip.all_read", "All read")
                    : Component.translatableWithFallback("folios.tip.read", "%s of %s read", read[0], read[1]);
        }
        return switch (part) {
            case "index" -> Component.translatableWithFallback("folios.tip.index", "Every entry, A to Z");
            case "bookmarks" -> Component.translatableWithFallback("folios.tip.bookmarks", "%s marked", memory.bookmarks().size());
            default -> Component.translatableWithFallback("folios.tip.front", "The front of the book");
        };
    }

    /** The key a tab moves by: its category, or the part of the book it opens ("home", "bookmarks", "index"). */
    public static String tabKey(@Nullable GuideCategory category, String part) {
        return category != null ? "category:" + category.id() : part;
    }

    /** Entries read and entries open, in a category or (for null) the whole book. */
    public int[] progress(@Nullable String category) {
        int read = 0;
        int total = 0;
        for (GuideEntry entry : book.entries().values()) {
            if (category != null && !entry.category().equals(category) || !entry.isOpen()) continue;
            GuideCategory owner = book.category(entry.category());
            if (owner == null || !owner.isOpen()) continue;
            total++;
            if (memory.isRead(entry.id())) read++;
        }
        return new int[]{read, total};
    }

    // ---- Moving about ----

    public boolean canTurn(int direction) {
        if (direction < 0) return spot.page > 0 || !history.isEmpty();
        return spot.page + 2 < this.chapter(spot.chapter).size();
    }

    public void turn(int direction) {
        if (!this.canMove()) return;
        if (direction < 0 && spot.page == 0) {
            this.back();
            return;
        }
        int page = spot.page + direction * 2;
        if (page < 0 || page >= this.chapter(spot.chapter).size()) return;
        this.moveTo(new Spot(spot.chapter, page), direction > 0);
    }

    /** Goes to a spread of the open part, by its left page. */
    public void turnTo(int page) {
        if (!this.canMove() || page == spot.page || page < 0 || page >= this.chapter(spot.chapter).size()) return;
        this.moveTo(new Spot(spot.chapter, even(page)), page > spot.page);
    }

    public void back() {
        if (history.isEmpty() || !this.canMove()) return;
        this.moveTo(history.pop(), false);
    }

    public void go(Spot next) {
        if (next.equals(spot) || !this.canMove()) return;
        // A new search replaces the last rather than piling up.
        if (!(spot.chapter.startsWith("search:") && next.chapter.startsWith("search:"))) history.push(spot);
        if (history.size() > 64) history.removeLast();
        this.moveTo(next, true);
    }

    private void moveTo(Spot next, boolean forward) {
        this.leaf(next, forward);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    private void leaf(Spot next, boolean forward) {
        Spot from = spot;
        spot = next;
        if (next.chapter.startsWith("entry:")) memory.markRead(next.chapter.substring(6));
        flip = new Flip(this.page(from, 0), this.page(from, 1), this.page(next, 0), this.page(next, 1), forward, Util.getMillis(), from, next);
    }

    /**
     * Goes where someone this book is read along with has gone, turning a leaf the way theirs turned: onwards through
     * the part they are in, back within it, onwards to another. Quiet: the sound is theirs, heard where they are.
     */
    public void moveAlong(Spot next) {
        if (next.equals(spot)) return;
        this.leaf(next, !next.chapter.equals(spot.chapter) || next.page > spot.page);
    }

    /** How far the leaf in the air has turned, 0 to 1; 1 when none is. */
    public float flipProgress() {
        if (flip == null) return 1;
        float progress = (Util.getMillis() - flip.start()) / FLIP_MILLIS;
        if (progress >= 1) {
            // The leaf is down: the spread it brought starts showing its content.
            revealAt = flip.start() + (long) FLIP_MILLIS;
            flip = null;
            return 1;
        }
        return progress;
    }

    public void home() {
        this.go(new Spot("home", 0));
    }

    public void category(String id) {
        this.go(new Spot("category:" + id, 0));
    }

    /**
     * Shows what a search finds. The leaf turns once, as the results first open; while they are open, a changed
     * search changes them where they lie.
     */
    public void search(String query) {
        Spot next = new Spot("search:" + query.strip(), 0);
        if (spot.chapter.startsWith("search:")) {
            if (!next.equals(spot)) {
                spot = next;
                this.reveal();
            }
            return;
        }
        this.go(next);
    }

    public void bookmarks() {
        this.go(new Spot("bookmarks", 0));
    }

    public void index() {
        this.go(new Spot("index", 0));
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    @Override
    public void follow(String link) {
        if (link.startsWith("bookmark:")) {
            int hash = link.lastIndexOf('#');
            this.open(new GuideBook.Location(link.substring(9, hash), Integer.parseInt(link.substring(hash + 1))));
            return;
        }
        GuideSession.Target target = session.resolve(link);
        if (target == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (target.url() != null) {
            ConfirmLinkScreen.confirmLinkNow(target.url(), minecraft.screen, true);
        } else if (target.category() != null) {
            this.category(target.category());
        } else if (target.entry() != null) {
            this.go(new Spot("entry:" + target.entry(), even(target.page())));
        }
    }

    @Override
    public void open(GuideBook.Location location) {
        this.go(new Spot("entry:" + location.entry(), even(session.pageOf(location))));
    }

    @Override
    public GuideBook.@Nullable Location here() {
        return spot.chapter.startsWith("entry:") ? session.location(spot.chapter.substring(6), spot.page) : null;
    }

    /** The entry open, if an entry is. */
    public @Nullable GuideEntry entry() {
        return spot.chapter.startsWith("entry:") ? book.entry(spot.chapter.substring(6)) : null;
    }

    /** The category the reader is in: open, or the one of the entry open. */
    public @Nullable GuideCategory category() {
        if (spot.chapter.startsWith("category:")) return book.category(spot.chapter.substring(9));
        GuideEntry entry = this.entry();
        return entry != null ? book.category(entry.category()) : null;
    }

    /** Remembers where the reader left off. */
    public void save() {
        GuideBook.Location here = this.here();
        // An entry is remembered as where in it the reader was; any other spread (but a search) by its chapter.
        if (here != null) memory.setLast(here);
        boolean other = here == null && !spot.chapter.startsWith("search:");
        memory.setLastSpread(other ? spot.chapter : null, other ? spot.page : 0);
        if (here == null && other) memory.setLast(null);
        memory.save();
    }
}
