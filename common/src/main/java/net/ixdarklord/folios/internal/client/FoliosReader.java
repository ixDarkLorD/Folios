package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosReveal;
import net.ixdarklord.folios.api.client.FoliosReveals;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.page.QuestPageElement;
import net.ixdarklord.folios.internal.client.page.Shapes;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
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
public class FoliosReader implements AbstractPainter.Navigator {
    public static final float FLIP_MILLIS = 420;
    private static final int ROW = 23;
    /** A row with a progress bar: 2 pixels of room over its name, and its line along the foot 6 pixels under the bar. */
    private static final int BAR_ROW = 27;

    public final FoliosBook book;
    public FoliosSession session;
    public final FoliosMemory memory;
    private final long openedAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
    private final Map<String, List<FoliosPageElement.Laid>> chapters = new HashMap<>();
    private final Deque<Spot> history = new ArrayDeque<>();

    /** A place in the book: a part of it ("home", "category:<id>", "entry:<id>", "search:<words>", "bookmarks", "index") and the left page of a spread. */
    public record Spot(String chapter, int page) {}

    /** Leaves in the air: what was open and what is opening (their pages and spreads), and which way they turn. */
    public record Flip(FoliosPageElement.@Nullable Laid oldLeft, FoliosPageElement.@Nullable Laid oldRight, FoliosPageElement.@Nullable Laid newLeft,
                       FoliosPageElement.@Nullable Laid newRight, boolean forward, long start, Spot from, Spot to) {
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

    public FoliosReader(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at) {
        this(book, session, at, null);
    }

    /**
     * A reader of a book opened from a copy in {@code hand} (null for none: a command, a link, from code): a book that
     * keeps its own progress reads and keeps that copy's.
     */
    public FoliosReader(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at, net.minecraft.world.@Nullable InteractionHand hand) {
        this.book = book;
        this.session = session;
        this.memory = FoliosMemory.forReading(book, hand);
        READ_BEFORE.computeIfAbsent(book.id(), id -> {
            java.util.Set<String> read = new java.util.HashSet<>();
            for (FoliosEntry entry : book.entries().values()) if (memory.isRead(entry.id())) read.add(entry.id());
            return read;
        });
        FoliosBook.Location start = at != null ? at : memory.last();
        FoliosEntry entry = start != null ? book.entry(start.entry()) : null;
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
     * Reads on from pages laid out again when what they were laid out from changed (a live list a page observes, the
     * reader's progress, the language), at the same place; not while a leaf is turning.
     */
    public void keepCurrent() {
        if (flip != null || session.isCurrent()) return;
        FoliosSession fresh = FoliosLibrary.session(book.id(), session.layout().pageWidth(), session.layout().pageHeight());
        if (fresh != null) this.useSession(fresh);
    }

    /**
     * Reads on from pages laid out to another size (the screen's GUI scale or window changed): at the same place, an
     * entry's page found again by the page of its JSON it came from, any other part at the same page as far as it goes.
     */
    public void useSession(FoliosSession next) {
        if (next == session) return;
        FoliosBook.Location here = this.here();
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
        FoliosCategory category = book.category(chapter.substring(9));
        return category != null && category.isOpen();
    }

    private static int even(int page) {
        return page - page % 2;
    }

    public float seconds() {
        return (net.ixdarklord.folios.internal.client.FoliosClock.millis() - openedAt) / 1000.0F;
    }

    // ---- Revealing a spread's content ----

    // When the spread now open started showing its content.
    private long revealAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();

    /** Whether the open spread's reveal waits to start: its pages aren't shown yet. */
    protected boolean revealHeld() {
        return false;
    }

    /** Starts the open spread's reveal again from the beginning (as a handheld book comes open). */
    public void reveal() {
        revealAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
    }

    /** The reveal a spread's pages use: its entry's own, or the book's. */
    public FoliosReveal.Use revealOf(Spot at) {
        if (at.chapter.startsWith("entry:")) {
            FoliosEntry entry = book.entry(at.chapter.substring(6));
            if (entry != null && entry.reveal() != null) return entry.reveal();
        }
        return book.reveal();
    }

    /**
     * The painter to draw a page's content through, for a page of the spread at {@code at}: while a leaf turns, the
     * spread it brings is hidden (its effect at the start), and once it is down its content shows as the effect has
     * it. The page's own painter when there is nothing to reveal.
     */
    public FoliosPainter revealing(FoliosPainter painter, Spot at, boolean right) {
        FoliosPageElement.Laid page = this.page(at, right ? 1 : 0);
        float progress = this.revealProgress(at, page, right);
        return progress >= 1 ? painter : net.ixdarklord.folios.internal.client.paint.RevealPainter.of(painter, FoliosReveals.get(this.revealOf(at).effect()),
                progress, this.revealX(page, right));
    }

    // Where a page is across what is revealed together: a spread's halves across the spread, a plain page on its own.
    private float revealX(FoliosPageElement.@Nullable Laid page, boolean right) {
        return right && page instanceof FoliosPageElement.SpreadHalf ? session.layout().pageWidth() : 0;
    }

    /**
     * Draws a page of the spread at {@code at} (its right page, or its left) as its reveal has it now, with what its
     * effect draws over it.
     */
    public void drawRevealed(FoliosPainter painter, FoliosPageElement.Laid page, Spot at, boolean right) {
        float progress = this.revealProgress(at, page, right);
        if (progress >= 1) page.draw(painter);
        else net.ixdarklord.folios.internal.client.paint.RevealPainter.draw(painter, page, FoliosReveals.get(this.revealOf(at).effect()), progress,
                this.revealX(page, right));
    }

    // How far the reveal of a page of a spread has gone: 1 when there is nothing (more) to reveal. A spread page's
    // halves reveal together; plain pages one after the other, the left then the right, each over the effect's length.
    private float revealProgress(Spot at, FoliosPageElement.@Nullable Laid page, boolean right) {
        FoliosReveal.Use use = this.revealOf(at);
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
        float elapsed = (net.ixdarklord.folios.internal.client.FoliosClock.millis() - revealAt) / (use.seconds() * 1000.0F);
        boolean together = page instanceof FoliosPageElement.SpreadHalf;
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

    public List<FoliosPageElement.Laid> chapter(String key) {
        List<FoliosPageElement.Laid> pages = chapters.get(key);
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

    public FoliosPageElement.@Nullable Laid page(Spot at, int offset) {
        List<FoliosPageElement.Laid> pages = this.chapter(at.chapter);
        int index = at.page + offset;
        return index >= 0 && index < pages.size() ? pages.get(index) : null;
    }

    private List<FoliosPageElement.Laid> homePages() {
        List<FoliosPageElement.Laid> pages = new ArrayList<>();
        FoliosLayout layout = session.layout();
        // Mastery, when the book has quests: how many of all their objectives are done, under its name.
        List<FoliosObjective> all = new ArrayList<>();
        for (FoliosCategory category : book.visibleCategories()) {
            for (FoliosEntry entry : book.entries(category.id())) all.addAll(entry.objectives());
        }
        boolean mastery = !all.isEmpty();
        // The book's name, smaller or over two lines when it is long; what is under it moves down as far as it takes.
        Component bookTitle = book.title().copy().withStyle(ChatFormatting.BOLD);
        Name title = fit(layout.font(), bookTitle, layout.contentWidth());
        int titleBottom = (mastery ? 134 : 96) + title.extra();
        List<FoliosText> landing = FoliosText.isBlank(book.landing()) ? List.of()
                : layout.text(book.landing()).split(layout.contentBottom() - titleBottom, layout.contentBottom() - FoliosLayout.CONTENT_TOP);
        FoliosText first = landing.isEmpty() ? null : landing.get(0);
        pages.add(painter -> {
            FoliosPalette palette = painter.palette();
            int middle = painter.pageWidth() / 2;
            medallion(painter, middle - 22, 12, 44);
            painter.push();
            painter.translate(middle - 16, 18);
            painter.scale(2);
            book.icon().draw(painter, 0, 0);
            painter.pop();
            int contentWidth = painter.contentRight() - FoliosLayout.CONTENT_LEFT;
            int y = 62;
            for (int line = 0; line < title.lines().size(); line++) {
                FormattedCharSequence shown = title.lines().get(line);
                float wide = painter.font().width(shown) * title.scale();
                painter.push();
                painter.translate(middle - wide / 2, y + (title.lines().size() == 1 ? (1 - title.scale()) * 4 : line * Name.STEP));
                painter.scale(title.scale());
                painter.text(shown, 0, 0, palette.heading());
                painter.pop();
            }
            if (title.cut() && painter.hovered(FoliosLayout.CONTENT_LEFT, y, contentWidth, title.height())) painter.tooltip(bookTitle);
            y += 10 + title.extra();
            if (!FoliosText.isBlank(book.subtitle())) {
                // On one line, whole: made smaller as far as it must be to fit.
                FormattedCharSequence subtitle = FoliosText.inline(book.subtitle()).copy().withStyle(ChatFormatting.ITALIC).getVisualOrderText();
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
            if (first != null && !first.isEmpty()) first.draw(painter, FoliosLayout.CONTENT_LEFT, titleBottom);
        });
        for (int index = 1; index < landing.size(); index++) {
            FoliosText piece = landing.get(index);
            pages.add(painter -> piece.draw(painter, FoliosLayout.CONTENT_LEFT, FoliosLayout.CONTENT_TOP));
        }
        List<Row> rows = new ArrayList<>();
        for (FoliosCategory category : book.visibleCategories()) {
            rows.add(new Row(category.isOpen() ? category.title() : Component.literal("???"), category.isOpen() ? category.icon() : FoliosIcon.NONE,
                    category.isOpen() ? "category:" + category.id() : null, () -> category.isOpen() && this.unread(category.id()), category.id()));
        }
        pages.addAll(this.rows(Component.translatableWithFallback("folios.categories", "Categories"), null, rows));
        return pages;
    }

    // A framed slot for the book's icon on its first page: a rounded rim, a ring of the book's colour just inside it,
    // and the face, each with the same soft corners so the rings run evenly round them; a faint shade along the face's
    // top inside, as if it were sunk a little.
    private static void medallion(FoliosPainter painter, int x, int y, int size) {
        FoliosPalette palette = painter.palette();
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.2F);
        int ring = FoliosPalette.mix(palette.accent(), palette.line(), 0.2F);
        int face = FoliosPalette.mix(palette.panel(), palette.paper(), 0.3F);
        rounded(painter, x, y, x + size, y + size, rim);
        rounded(painter, x + 1, y + 1, x + size - 1, y + size - 1, ring);
        rounded(painter, x + 3, y + 3, x + size - 3, y + size - 3, rim);
        rounded(painter, x + 4, y + 4, x + size - 4, y + size - 4, face);
        painter.fill(x + 6, y + 4, x + size - 6, y + 5, FoliosPalette.mix(face, palette.line(), 0.35F));
    }

    // A rectangle with its corners rounded off by three pixels.
    private static void rounded(FoliosPainter painter, int x0, int y0, int x1, int y1, int color) {
        painter.fill(x0 + 3, y0, x1 - 3, y1, color);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, color);
        painter.fill(x0, y0 + 3, x1, y1 - 3, color);
    }

    // The mastery bar: a trophy and "Mastery" over it, under them "Progression:" with how far along it is in percent (a
    // tick once all is done), then the bar of all the book's objectives, centred under the book's name.
    private void mastery(FoliosPainter painter, int middle, int y, List<FoliosObjective> all) {
        FoliosPalette palette = painter.palette();
        int done = 0;
        for (FoliosObjective objective : all) if (objective.isDone(book)) done++;
        boolean complete = done == all.size();
        float progress = FoliosObjective.progress(book, all);
        Component title = Component.translatableWithFallback("folios.mastery.title", "Mastery").withStyle(ChatFormatting.BOLD);
        int titleWidth = 9 + 3 + painter.font().width(title);
        int left = middle - titleWidth / 2;
        int cup = complete ? 0xFFE0B23A : FoliosPalette.mix(palette.muted(), palette.accent(), 0.5F);
        // Mastered, the trophy cycles through a rainbow; until then it is quiet, a highlight down its cup's left.
        if (complete) {
            TROPHY.rainbow(painter, left, y - 1, painter.time(), 0xFFE0B23A);
        } else {
            net.ixdarklord.folios.internal.client.screen.FoliosBar.pixels(painter, TROPHY, left, y - 1, 9, cup);
            painter.fill(left + 2, y, left + 3, y + 3, FoliosPalette.mix(cup, 0xFFFFFFFF, 0.45F));
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
    private List<Component> masteryTooltip(int done, List<FoliosObjective> all) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatableWithFallback("folios.mastery.title", "Mastery").withStyle(ChatFormatting.BOLD));
        int percent = Math.round(100 * FoliosObjective.progress(book, all));
        lines.add(Component.translatableWithFallback("folios.mastery.objectives", "%s of %s objectives done (%s%%)", done, all.size(), percent));
        List<FoliosEntry> quests = new ArrayList<>();
        for (FoliosCategory category : book.visibleCategories()) {
            for (FoliosEntry entry : book.entries(category.id())) if (entry.isQuest()) quests.add(entry);
        }
        int finished = 0;
        for (FoliosEntry quest : quests) if (quest.objectives().stream().allMatch(objective -> objective.isDone(book))) finished++;
        lines.add(Component.translatableWithFallback("folios.mastery.quests", "%s of %s quests complete", finished, quests.size()));
        int shown = 0;
        for (FoliosEntry quest : quests) {
            if (shown++ == 6) {
                lines.add(Component.translatableWithFallback("folios.mastery.more", "and %s more", quests.size() - 6).withStyle(ChatFormatting.ITALIC));
                break;
            }
            int ticked = 0;
            for (FoliosObjective objective : quest.objectives()) if (objective.isDone(book)) ticked++;
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
    private static void flourish(FoliosPainter painter, int middle, int y) {
        FoliosPalette palette = painter.palette();
        int line = FoliosPalette.mix(palette.line(), palette.accent(), 0.35F);
        painter.fill(middle - 34, y, middle - 5, y + 1, line);
        painter.fill(middle + 5, y, middle + 34, y + 1, line);
        painter.fill(middle - 37, y - 1, middle - 35, y + 2, line);
        painter.fill(middle + 35, y - 1, middle + 37, y + 2, line);
        int mark = palette.accent();
        painter.fill(middle - 1, y - 2, middle + 1, y + 3, mark);
        painter.fill(middle - 3, y, middle + 3, y + 1, mark);
        painter.fill(middle - 2, y - 1, middle + 2, y + 2, mark);
    }

    private List<FoliosPageElement.Laid> categoryPages(String id) {
        FoliosCategory category = book.category(id);
        if (category == null) return List.of();
        List<Row> rows = new ArrayList<>();
        for (FoliosEntry entry : book.entries(id)) rows.add(this.entryRow(entry));
        return this.rows(category.title(), category.description().isEmpty() ? null : category.description(), rows);
    }

    private List<FoliosPageElement.Laid> searchPages(String query) {
        List<Row> rows = new ArrayList<>();
        for (FoliosEntry entry : session.search(query)) rows.add(this.entryRow(entry));
        String heading = rows.isEmpty() ? "Nothing matches \"" + query + "\"." : null;
        return this.rows(Component.translatableWithFallback("folios.search.results", "Search"), heading, rows);
    }

    private List<FoliosPageElement.Laid> bookmarkPages() {
        List<Row> rows = new ArrayList<>();
        for (FoliosBook.Location location : memory.bookmarks()) {
            FoliosEntry entry = book.entry(location.entry());
            if (entry == null || !entry.isOpen()) continue;
            rows.add(new Row(entry.title(), entry.icon(), "bookmark:" + location.entry() + "#" + location.page(), () -> false, null));
        }
        return this.rows(Component.translatableWithFallback("folios.bookmarks", "Bookmarks"),
                rows.isEmpty() ? "Nothing bookmarked yet." : null, rows);
    }

    // Every entry of the book, A to Z, wherever it is: those not yet open as "???" at the end.
    private List<FoliosPageElement.Laid> indexPages() {
        List<FoliosEntry> entries = new ArrayList<>();
        for (FoliosCategory category : book.visibleCategories()) {
            if (category.isOpen()) entries.addAll(book.entries(category.id()));
        }
        entries.sort(java.util.Comparator.comparing((FoliosEntry entry) -> !entry.isOpen()).thenComparing(entry -> entry.title().getString().toLowerCase(java.util.Locale.ROOT)));
        List<Row> rows = new ArrayList<>();
        for (FoliosEntry entry : entries) rows.add(this.entryRow(entry));
        return this.rows(Component.translatableWithFallback("folios.index", "Index"), rows.isEmpty() ? "Nothing to list yet." : null, rows);
    }

    private Row entryRow(FoliosEntry entry) {
        Row row = new Row(entry.isOpen() ? entry.title() : Component.literal("???"), entry.isOpen() ? entry.icon() : FoliosIcon.NONE,
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
    private record Row(Component label, FoliosIcon icon, @Nullable String link, BooleanSupplier fresh, @Nullable String category,
                       List<FoliosObjective> quest) {
        Row(Component label, FoliosIcon icon, @Nullable String link, BooleanSupplier fresh, @Nullable String category) {
            this(label, icon, link, fresh, category, List.of());
        }

        Row withQuest(List<FoliosObjective> objectives) {
            return new Row(label, icon, link, fresh, category, objectives);
        }
    }

    // A titled list, with text over it, as many pages as it takes.
    private List<FoliosPageElement.Laid> rows(Component title, @Nullable String text, List<Row> rows) {
        FoliosLayout layout = session.layout();
        List<FoliosPageElement.Laid> pages = new ArrayList<>();
        FoliosText description = text == null ? null : layout.text(text);
        int top = FoliosLayout.CONTENT_TOP + FoliosLayout.TITLE_HEIGHT + (description != null ? description.height() + 8 : 0);
        int index = 0;
        boolean firstPage = true;
        do {
            int y = firstPage ? top : FoliosLayout.CONTENT_TOP;
            // As many rows as fit, each its own height; at least one.
            int fits = 0;
            for (int at = y; index + fits < rows.size(); fits++) {
                at += this.height(rows.get(index + fits));
                if (at > layout.contentBottom() && fits > 0) break;
            }
            List<Row> on = rows.subList(index, Math.min(rows.size(), index + Math.max(1, fits)));
            index += on.size();
            boolean titled = firstPage;
            pages.add(painter -> {
                if (titled) {
                    FoliosLayout.title(painter, title, FoliosLayout.CONTENT_TOP);
                    if (description != null) description.draw(painter, FoliosLayout.CONTENT_LEFT, FoliosLayout.CONTENT_TOP + FoliosLayout.TITLE_HEIGHT);
                }
                int rowY = titled ? top : FoliosLayout.CONTENT_TOP;
                for (Row row : on) {
                    this.row(painter, row, rowY);
                    rowY += this.height(row);
                }
            });
            firstPage = false;
        } while (index < rows.size());
        return pages;
    }

    // Whether a row shows a bar: a category's reading (when the book shows it) or a quest's.
    private boolean barred(Row row) {
        return row.category != null && book.showProgress() && row.link != null || !row.quest.isEmpty();
    }

    private int height(Row row) {
        return (this.barred(row) ? BAR_ROW : ROW) + this.name(row, session.layout().font(), session.layout().contentWidth()).extra();
    }

    /** A row's name as drawn: its lines, the scale they are drawn at, and whether it was cut short. */
    private record Name(List<FormattedCharSequence> lines, float scale, boolean cut) {
        /** The most lines a name takes, and the smallest it is drawn. */
        static final int MOST = 2;
        static final float SMALL = 0.75F;
        /** From one line of a name drawn small to the next. */
        static final int STEP = 8;

        /** How much taller than a name of one line it is. */
        int extra() {
            return (lines.size() - 1) * STEP;
        }

        /** How tall its text is, from its top. */
        int height() {
            return lines.size() == 1 ? 8 : lines.size() * STEP;
        }
    }

    // A row's name in the room it has (see fit).
    private Name name(Row row, Font font, int contentWidth) {
        return fit(font, row.label, Math.max(10, contentWidth - 20 - FRESH - this.countWidth(row, font)));
    }

    // Text in the room it has: as it is if it fits; else smaller, down to three quarters; else over two lines at that,
    // the last cut short with an ellipsis if even they aren't enough.
    private static Name fit(Font font, Component text, int room) {
        int width = font.width(text);
        if (width <= room) return new Name(List.of(text.getVisualOrderText()), 1, false);
        if (width * Name.SMALL <= room) return new Name(List.of(text.getVisualOrderText()), room / (float) width, false);
        int across = (int) (room / Name.SMALL);
        List<FormattedText> parts = font.getSplitter().splitLines(text, across, Style.EMPTY);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int line = 0; line < Math.min(Name.MOST, parts.size()); line++) {
            boolean last = line == Name.MOST - 1 && parts.size() > Name.MOST;
            FormattedText part = last ? FormattedText.composite(parts.get(line), FormattedText.of(" …")) : parts.get(line);
            if (last && font.width(part) > across) {
                part = FormattedText.composite(font.substrByWidth(part, across - font.width("…")), FormattedText.of("…"));
            }
            lines.add(Language.getInstance().getVisualOrder(part));
        }
        return new Name(lines, Name.SMALL, parts.size() > Name.MOST);
    }

    /** The room kept at a row's end for the sparkle of one unread, whether or not it shows now. */
    private static final int FRESH = 10;

    // How wide a row's count is, with the room after it; 0 for a row without one.
    private int countWidth(Row row, Font font) {
        Component count = this.count(row);
        return count == null ? 0 : font.width(count) + 4;
    }

    // A row's count: a category's entries read of all its entries (when the book shows it), or a quest's objectives
    // done of all of them ("✔" once all are).
    private @Nullable Component count(Row row) {
        if (row.category != null && book.showProgress() && row.link != null) {
            int[] read = this.progress(row.category);
            return Component.literal(read[0] + "/" + read[1]);
        }
        if (row.quest.isEmpty()) return null;
        int done = 0;
        for (FoliosObjective objective : row.quest) if (objective.isDone(book)) done++;
        return done == row.quest.size() ? Component.literal("✔") : Component.literal(done + "/" + row.quest.size());
    }

    // A row's highlight under the pointer: a box with its corners rounded by 2 pixels, in rows that don't overlap so
    // its see-through colour stays even.
    private static void highlight(FoliosPainter painter, int x0, int y0, int x1, int y1, int argb) {
        painter.fill(x0 + 2, y0, x1 - 2, y0 + 1, argb);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, argb);
        painter.fill(x0, y0 + 2, x1, y1 - 2, argb);
        painter.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, argb);
        painter.fill(x0 + 2, y1 - 1, x1 - 2, y1, argb);
    }

    private void row(FoliosPainter painter, Row row, int y) {
        FoliosPalette palette = painter.palette();
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight();
        int height = this.height(row);
        boolean over = row.link != null && painter.action(left - 2, y, right - left + 4, height - 1, null, () -> this.follow(row.link));
        if (over) highlight(painter, left - 2, y, right + 2, y + height - 1, FoliosPalette.alpha(palette.accent(), 0x2A));
        boolean barred = this.barred(row);
        Name name = this.name(row, painter.font(), right - left);
        // Without a bar: the icon with 3 pixels of paper over it and under it before the lines, the name centred on it.
        // With one: the name 2 pixels further down than the row before's line, the bar under it, the icon centred
        // between the name's top and the line along the foot.
        int iconY = barred ? (y + 3 + y + height - 2) / 2 - 8 : y + 2 + name.extra() / 2;
        int textY = barred ? y + 3 : iconY + 8 - name.height() / 2;
        if (!row.icon.isEmpty()) {
            painter.push();
            painter.translate(left, iconY);
            row.icon.draw(painter, 0, 0);
            painter.pop();
        } else {
            painter.centeredText(Component.literal("?"), left + 8, iconY + 4, palette.muted());
        }
        boolean fresh = row.fresh.getAsBoolean();
        int textRight = right - (fresh ? FRESH : 0);
        // How far along it is: a count at the right and a bar along its foot, for a category's reading or a quest.
        float progress = -1;
        int steps = 1;
        Component count = null;
        if (row.category != null && book.showProgress() && row.link != null) {
            int[] read = this.progress(row.category);
            count = this.count(row);
            progress = read[1] == 0 ? 0 : read[0] / (float) read[1];
            steps = read[1];
        } else if (!row.quest.isEmpty()) {
            progress = FoliosObjective.progress(book, row.quest);
            steps = row.quest.size();
            count = this.count(row);
        }
        boolean complete = progress >= 1;
        if (count != null) {
            int width = painter.font().width(count);
            painter.text(count, textRight - width, textY, complete ? 0xFF3E8E41 : palette.muted());
            textRight -= width + 4;
        }
        // Its name: smaller, or over two lines, when it is too long for its line; one cut short is shown whole when hovered.
        int nameColor = row.link == null ? palette.muted() : over ? palette.heading() : palette.text();
        for (int line = 0; line < name.lines().size(); line++) {
            painter.push();
            painter.translate(left + 20, textY + (name.lines().size() == 1 ? (1 - name.scale()) * 4 : line * Name.STEP));
            painter.scale(name.scale());
            painter.text(name.lines().get(line), 0, 0, nameColor);
            painter.pop();
        }
        if (name.cut() && painter.hovered(left + 20, textY, textRight - left - 20, name.height())) painter.tooltip(row.label);
        // Unread: a little four-pointed sparkle at the row's end, blinking now and then (each row at its own moment).
        if (fresh) net.ixdarklord.folios.internal.client.screen.FoliosBar.sparkle(painter, right - 7, textY, y * 0.037F,
                palette.accent() | 0xFF000000);
        if (progress >= 0) {
            QuestPageElement.stepped(painter, left + 20, textY + 11 + name.extra(), right - left - 21, progress, steps, complete ? 0xFF4CAF50 : palette.accent());
        }
        // The line along the row's foot, as every row has; under a bar it falls 6 pixels below the bar's edge.
        painter.fill(left + 20, y + height - 2, right, y + height - 1, FoliosPalette.alpha(palette.line(), 0x60));
    }

    /**
     * What is printed round a page's content: its number, the clasp in its lower outer corner that turns back (on a
     * left page) or on (on a right page), and on a right page of a book that can be shut, the clasp at its head that
     * shuts it. They belong to the page, so they turn with it; they are pressable only on a settled spread. Where the
     * book's layout puts them ({@link FoliosBook.Layout}).
     *
     * @param shut what the shut clasp does; null for a book with none
     */
    public void chrome(FoliosPainter painter, Spot spot, boolean right, boolean pressable, @Nullable Runnable shut) {
        FoliosPalette palette = painter.palette();
        FoliosBook.Layout layout = book.layout();
        int width = painter.pageWidth();
        int size = Shapes.BUTTON;
        // The shut clasp is the book's, there whether or not the spread has a right-hand page.
        if (right && shut != null) {
            int x = width - layout.closeX() - size;
            int top = layout.closeY();
            boolean over = pressable && painter.action(x, top - 1, size, size + 1, Component.translatableWithFallback("folios.shut", "Close the book"), shut);
            Shapes.button(painter, x, top, Shapes.Icon.CLOSE, over);
        }
        FoliosPageElement.Laid laid = this.page(spot, right ? 1 : 0);
        if (laid == null) return;
        int bottom = painter.pageHeight();
        if (laid instanceof FoliosPageElement.SpreadHalf) {
            // One number for the spread, at the fold, each page printing its own part of it.
            Shapes.acrossFold(painter, Component.literal(String.valueOf(spot.page + 1)).getVisualOrderText(), bottom - layout.numberY(), palette.muted(), right);
        } else {
            // Centred on the frame's stud at the foot of the page (on its middle pixel) by the pixels it draws: a glyph's
            // width counts the gap after it, which would leave the number a pixel short of the stud's middle.
            Component number = Component.literal(String.valueOf(spot.page + (right ? 2 : 1)));
            painter.text(number, width / 2 + layout.numberX() - (painter.font().width(number) - 1) / 2, bottom - layout.numberY(), palette.muted());
        }
        int y = bottom - layout.buttonY();
        if (!right && (spot.page > 0 || !history.isEmpty())) {
            boolean back = spot.page == 0;
            int x = layout.buttonX();
            Component tooltip = back ? Component.translatableWithFallback("folios.back", "Back")
                    : Component.translatableWithFallback("folios.previous", "Previous page");
            boolean over = pressable && painter.action(x, y - 1, size, size + 1, tooltip, () -> this.turn(-1));
            if (over) painter.tooltip(net.ixdarklord.folios.internal.client.FoliosTooltips.of(tooltip, back ? Component.translatableWithFallback("folios.tip.back", "Backspace or the side mouse button") : Component.translatableWithFallback("folios.tip.turn", "Arrow keys or the mouse wheel")));
            Shapes.button(painter, x, y, back ? Shapes.Icon.BACK : Shapes.Icon.PREVIOUS, over);
        } else if (right && spot.page + 2 < this.chapter(spot.chapter).size()) {
            int x = width - layout.buttonX() - size;
            Component next = Component.translatableWithFallback("folios.next", "Next page");
            boolean over = pressable && painter.action(x, y - 1, size, size + 1, next, () -> this.turn(1));
            if (over) painter.tooltip(net.ixdarklord.folios.internal.client.FoliosTooltips.of(next, Component.translatableWithFallback("folios.tip.turn", "Arrow keys or the mouse wheel")));
            Shapes.button(painter, x, y, Shapes.Icon.NEXT, over);
        }
    }

    /**
     * A tab's colour: its category's own (or the book's accent, for the front, bookmarks and categories without one),
     * full where the reader is, a little deeper under the pointer, and deeper still towards the cover otherwise.
     */
    public int tabColor(@Nullable FoliosCategory category, FoliosPalette palette, boolean here, boolean hovered) {
        return this.tabColor(category, palette, here ? 1 : hovered ? 0.55F : 0);
    }

    /** A tab's colour as it moves: from its resting colour at 0 to its full one at 1 ({@link TabMotion#out}). */
    public int tabColor(@Nullable FoliosCategory category, FoliosPalette palette, float out) {
        int base = category != null && category.color() >= 0 ? 0xFF000000 | category.color() : palette.accent() | 0xFF000000;
        // Past 1 as it overshoots it glows a little brighter; knocked in below 0 it stays at its resting colour.
        float t = Math.max(0, out);
        if (t > 1) return FoliosPalette.mix(base, 0xFFFFFFFF, 0.3F * (t - 1));
        return FoliosPalette.mix(base, 0xFF000000 | book.cover(), 0.4F * (1 - t));
    }

    /** What a tab's tooltip says under its name: how much of a category has been read, what the others hold. */
    public @Nullable Component tabDetail(@Nullable FoliosCategory category, String part) {
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
    public static String tabKey(@Nullable FoliosCategory category, String part) {
        return category != null ? "category:" + category.id() : part;
    }

    /** Entries read and entries open, in a category or (for null) the whole book. */
    public int[] progress(@Nullable String category) {
        int read = 0;
        int total = 0;
        for (FoliosEntry entry : book.entries().values()) {
            if (category != null && !entry.category().equals(category) || !entry.isOpen()) continue;
            FoliosCategory owner = book.category(entry.category());
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
        flip = new Flip(this.page(from, 0), this.page(from, 1), this.page(next, 0), this.page(next, 1), forward, net.ixdarklord.folios.internal.client.FoliosClock.millis(), from, next);
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
        float progress = (net.ixdarklord.folios.internal.client.FoliosClock.millis() - flip.start()) / FLIP_MILLIS;
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

    // The popup window pressing a link pinned over the book, until it is put away.
    private net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup pinned;

    public net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup pinned() {
        return pinned;
    }

    public void unpin() {
        pinned = null;
    }

    /**
     * Mentions a page of the open spread in chat (FoliosMentions): the right one, or the left; the book's front from
     * its front spread. False where there is no page to mention (a list of entries, a search).
     */
    public boolean mention(boolean right) {
        if (spot.chapter.equals("home")) {
            FoliosMentions.mention(book.id(), null);
            return true;
        }
        if (!spot.chapter.startsWith("entry:")) return false;
        int laid = spot.page + (right ? 1 : 0);
        if (laid >= this.chapter(spot.chapter).size()) laid = spot.page;
        FoliosMentions.mention(book.id(), session.location(spot.chapter.substring(6), laid));
        return true;
    }

    @Override
    public void follow(String link) {
        // A kind of a mod's own (FoliosLinks): what it does when pressed, else its popup pinned.
        net.ixdarklord.folios.api.client.FoliosLinks.Found found = net.ixdarklord.folios.api.client.FoliosLinks.find(link);
        if (found != null) {
            if (!found.kind().isOpen(book, found.target()) || found.kind().press(book, found.target())) return;
            pinned = session.popup(link);
            return;
        }
        if (link.startsWith("bookmark:")) {
            int hash = link.lastIndexOf('#');
            this.open(new FoliosBook.Location(link.substring(9, hash), Integer.parseInt(link.substring(hash + 1))));
            return;
        }
        FoliosSession.Target target = session.resolve(link);
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
    public void open(FoliosBook.Location location) {
        this.go(new Spot("entry:" + location.entry(), even(session.pageOf(location))));
    }

    @Override
    public FoliosBook.@Nullable Location here() {
        return spot.chapter.startsWith("entry:") ? session.location(spot.chapter.substring(6), spot.page) : null;
    }

    /** The entry open, if an entry is. */
    public @Nullable FoliosEntry entry() {
        return spot.chapter.startsWith("entry:") ? book.entry(spot.chapter.substring(6)) : null;
    }

    /** The category the reader is in: open, or the one of the entry open. */
    public @Nullable FoliosCategory category() {
        if (spot.chapter.startsWith("category:")) return book.category(spot.chapter.substring(9));
        FoliosEntry entry = this.entry();
        return entry != null ? book.category(entry.category()) : null;
    }

    /** Remembers where the reader left off. */
    public void save() {
        FoliosBook.Location here = this.here();
        // An entry is remembered as where in it the reader was; any other spread (but a search) by its chapter.
        if (here != null) memory.setLast(here);
        boolean other = here == null && !spot.chapter.startsWith("search:");
        memory.setLastSpread(other ? spot.chapter : null, other ? spot.page : 0);
        if (here == null && other) memory.setLast(null);
        memory.save();
    }
}
