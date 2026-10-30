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
import net.ixdarklord.folios.internal.client.paint.EdgeFadePainter;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

public class FoliosReader implements AbstractPainter.Navigator {
    public static final float FLIP_MILLIS = 420;
    private static final int ROW = 23;
    private static final int BAR_ROW = 27;

    public final FoliosBook book;
    public FoliosSession session;
    public final FoliosMemory memory;
    private final long openedAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
    private final Map<String, List<FoliosPageElement.Laid>> chapters = new HashMap<>();
    private final Deque<Spot> history = new ArrayDeque<>();

    public record Spot(String chapter, int page) {}

    public record Flip(FoliosPageElement.@Nullable Laid oldLeft, FoliosPageElement.@Nullable Laid oldRight, FoliosPageElement.@Nullable Laid newLeft,
                       FoliosPageElement.@Nullable Laid newRight, boolean forward, long start, Spot from, Spot to) {
        public Spot staticLeft() {
            return forward ? from : to;
        }

        public Spot staticRight() {
            return forward ? to : from;
        }

        public Spot face() {
            return forward ? from : to;
        }

        public Spot back() {
            return forward ? to : from;
        }
    }

    public Spot spot;
    public final TabMotion tabs = new TabMotion();
    public @Nullable Flip flip;

    public FoliosReader(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at) {
        this(book, session, at, null);
    }

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
            if (at != null && at.element() >= 0) this.flash(entry.id(), FoliosSession.elementMarker(at));
        } else if (at == null && this.resumable(memory.lastChapter())) {
            String chapter = memory.lastChapter();
            int page = even(Math.max(0, memory.lastPage()));
            if (page >= this.chapter(chapter).size()) page = 0;
            if (!chapter.equals("home")) history.push(spot);
            this.spot = new Spot(chapter, page);
        }
    }

    public void keepCurrent() {
        if (flip != null || session.isCurrent()) return;
        FoliosSession fresh = FoliosLibrary.session(book.id(), session.layout().pageWidth(), session.layout().pageHeight());
        if (fresh != null) this.useSession(fresh);
    }

    public void useSession(FoliosSession next) {
        if (next == session) return;
        FoliosBook.Location here = this.here();
        session = next;
        chapters.clear();
        flip = null;
        int page = here != null ? even(session.pageOf(here)) : spot.page;
        spot = new Spot(spot.chapter, Math.max(0, Math.min(even(page), even(Math.max(0, this.chapter(spot.chapter).size() - 1)))));
    }

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

    private long revealAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();

    protected boolean revealHeld() {
        return false;
    }

    public void reveal() {
        revealAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
    }

    public FoliosReveal.Use revealOf(Spot at) {
        if (at.chapter.startsWith("entry:")) {
            FoliosEntry entry = book.entry(at.chapter.substring(6));
            if (entry != null && entry.reveal() != null) return entry.reveal();
        }
        return book.reveal();
    }

    public FoliosPainter revealing(FoliosPainter painter, Spot at, boolean right) {
        FoliosPageElement.Laid page = this.page(at, right ? 1 : 0);
        float progress = this.revealProgress(at, page, right);
        return progress >= 1 ? painter : net.ixdarklord.folios.internal.client.paint.RevealPainter.of(painter, FoliosReveals.get(this.revealOf(at).effect()),
                progress, this.revealX(page, right));
    }

    private float revealX(FoliosPageElement.@Nullable Laid page, boolean right) {
        return right && page instanceof FoliosPageElement.SpreadHalf ? session.layout().pageWidth() : 0;
    }

    public void drawRevealed(FoliosPainter painter, FoliosPageElement.Laid page, Spot at, boolean right) {
        float progress = this.revealProgress(at, page, right);
        if (progress >= 1) {
            float lit = this.flashed(at, right);
            if (lit > 0) this.drawFlash(painter, lit, false);
            page.draw(painter);
            if (lit > 0) this.drawFlash(painter, lit, true);
        } else {
            net.ixdarklord.folios.internal.client.paint.RevealPainter.draw(painter, page, FoliosReveals.get(this.revealOf(at).effect()), progress,
                    this.revealX(page, right));
        }
    }

    private record Flash(String chapter, FoliosLayout.Mark place, long[] since) {}

    private static final long FLASH_MILLIS = 1800;
    private @Nullable Flash flash;

    private void flash(String entry, @Nullable String marker) {
        FoliosLayout.Mark place = marker == null ? null : session.place(entry, marker);
        flash = place == null ? null : new Flash("entry:" + entry, place, new long[] {-1});
    }

    private float flashed(Spot at, boolean right) {
        Flash lit = flash;
        if (lit == null || !lit.chapter().equals(at.chapter) || lit.place().page() != at.page + (right ? 1 : 0)) return 0;
        long now = FoliosClock.millis();
        if (lit.since()[0] < 0) {
            if (this.flipProgress() < 1) return 0;
            lit.since()[0] = now;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.7F));
        }
        float t = (now - lit.since()[0]) / (float) FLASH_MILLIS;
        if (t >= 1) {
            flash = null;
            return 0;
        }
        if (t < 0.12F) {
            float rise = t / 0.12F;
            return rise * rise * (3 - 2 * rise);
        }
        float fall = (t - 0.12F) / 0.88F;
        return 1 - fall * fall;
    }

    private static final int FLASH_RADIUS = 6;

    private void drawFlash(FoliosPainter painter, float strength, boolean over) {
        Flash lit = flash;
        if (lit == null) return;
        FoliosPalette palette = painter.palette();
        int x0 = FoliosLayout.CONTENT_LEFT - 3;
        int x1 = painter.contentRight() + 3;
        int y0 = lit.place().top() - 3;
        int y1 = lit.place().bottom() + 3;
        int accent = palette.accent();
        if (!over) {
            rounded(painter, x0 - 2, y0 - 2, x1 + 2, y1 + 2, FLASH_RADIUS + 2, FoliosPalette.alpha(accent, Math.round(0x24 * strength)));
            rounded(painter, x0, y0, x1, y1, FLASH_RADIUS, FoliosPalette.alpha(accent, Math.round(0x34 * strength)));
            return;
        }
        rim(painter, x0, y0, x1, y1, FLASH_RADIUS, FoliosPalette.alpha(accent, Math.round(0xC8 * strength)));
        float t = (FoliosClock.millis() - lit.since()[0]) / (float) FLASH_MILLIS;
        float sweep = t / 0.4F;
        if (sweep <= 0 || sweep >= 1) return;
        int light = FoliosPalette.mix(accent, 0xFFFFFFFF, 0.65F);
        float centre = x0 + (x1 - x0 + 24) * sweep - 12;
        for (int x = Math.max(x0 + 1, (int) centre - 12); x < Math.min(x1 - 1, (int) centre + 12); x++) {
            float near = 1 - Math.abs(x + 0.5F - centre) / 12;
            int inset = Math.max(inset(FLASH_RADIUS, x - x0), inset(FLASH_RADIUS, x1 - 1 - x));
            painter.fill(x, y0 + 1 + inset, x + 1, y1 - 1 - inset, FoliosPalette.alpha(light, Math.round(0x40 * near * near * strength)));
        }
    }

    private static int inset(int r, int along) {
        if (along >= r) return 0;
        float dy = r - along - 0.5F;
        return Math.round(r - (float) Math.sqrt(r * r - dy * dy));
    }

    private static void rounded(FoliosPainter painter, int x0, int y0, int x1, int y1, int r, int argb) {
        r = Math.min(r, Math.min(x1 - x0, y1 - y0) / 2);
        for (int row = 0; row < r; row++) {
            int in = inset(r, row);
            painter.fill(x0 + in, y0 + row, x1 - in, y0 + row + 1, argb);
            painter.fill(x0 + in, y1 - row - 1, x1 - in, y1 - row, argb);
        }
        painter.fill(x0, y0 + r, x1, y1 - r, argb);
    }

    private static void rim(FoliosPainter painter, int x0, int y0, int x1, int y1, int r, int argb) {
        r = Math.min(r, Math.min(x1 - x0, y1 - y0) / 2);
        painter.fill(x0 + inset(r, 0), y0, x1 - inset(r, 0), y0 + 1, argb);
        painter.fill(x0 + inset(r, 0), y1 - 1, x1 - inset(r, 0), y1, argb);
        painter.fill(x0, y0 + r, x0 + 1, y1 - r, argb);
        painter.fill(x1 - 1, y0 + r, x1, y1 - r, argb);
        for (int row = 1; row < r; row++) {
            int in = inset(r, row);
            int width = Math.max(1, inset(r, row - 1) - in);
            painter.fill(x0 + in, y0 + row, x0 + in + width, y0 + row + 1, argb);
            painter.fill(x1 - in - width, y0 + row, x1 - in, y0 + row + 1, argb);
            painter.fill(x0 + in, y1 - row - 1, x0 + in + width, y1 - row, argb);
            painter.fill(x1 - in - width, y1 - row - 1, x1 - in, y1 - row, argb);
        }
    }

    private float revealProgress(Spot at, FoliosPageElement.@Nullable Laid page, boolean right) {
        FoliosReveal.Use use = this.revealOf(at);
        if (use.seconds() <= 0 || !net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings.transitions()
                || use.once() && this.seen(at) || use.unread() && memory.isSeen(this.spreadKey(at))) {
            if (at.equals(spot) && flip == null && !this.revealHeld()) this.shown(at);
            return 1;
        }
        if (flip != null) return at.equals(flip.to()) ? 0 : 1;
        if (this.revealHeld() && at.equals(spot)) return 0;
        if (!at.equals(spot)) return 1;
        float elapsed = (net.ixdarklord.folios.internal.client.FoliosClock.millis() - revealAt) / (use.seconds() * 1000.0F);
        boolean together = page instanceof FoliosPageElement.SpreadHalf;
        if (elapsed >= (together ? 1 : 2)) {
            SEEN.add(this.seenKey(at));
            this.shown(at);
        }
        return Math.max(0, Math.min(1, together || !right ? elapsed : elapsed - 1));
    }

    private static final java.util.Set<String> SEEN = new java.util.HashSet<>();
    private static final java.util.Map<net.minecraft.resources.Identifier, java.util.Set<String>> READ_BEFORE = new java.util.HashMap<>();

    private String spreadKey(Spot at) {
        return at.chapter + "|" + at.page;
    }

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

    protected boolean canMove() {
        return true;
    }

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
        List<FoliosObjective> all = new ArrayList<>();
        for (FoliosCategory category : book.visibleCategories()) {
            for (FoliosEntry entry : book.entries(category.id())) all.addAll(entry.objectives());
        }
        boolean mastery = !all.isEmpty();
        Component bookTitle = book.title().copy().withStyle(ChatFormatting.BOLD);
        Name title = fit(layout.font(), bookTitle, layout.contentWidth());
        int titleBottom = (mastery ? 134 : 96) + title.extra();
        List<FoliosText> landing = FoliosText.isBlank(book.landing()) ? List.of()
                : layout.text(book.landing()).split(layout.contentBottom() - titleBottom, layout.contentBottom() - FoliosLayout.CONTENT_TOP);
        FoliosText first = landing.isEmpty() ? null : landing.getFirst();
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
        Component heading = Component.translatableWithFallback("folios.categories", "Categories");
        FoliosBook.CategoryView view = book.categoryView();
        IntFunction<List<Line>> lines = width -> view.grid() ? this.gridLines(rows, width) : this.listLines(rows, width);
        pages.addAll(view.scroll() ? this.scrolled(heading, lines) : this.paged(heading, null, lines.apply(layout.contentWidth())));
        return pages;
    }

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

    private static void rounded(FoliosPainter painter, int x0, int y0, int x1, int y1, int color) {
        painter.fill(x0 + 3, y0, x1 - 3, y1, color);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, color);
        painter.fill(x0, y0 + 3, x1, y1 - 3, color);
    }

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
            lines.add(Component.literal(questDone ? " \u2714 " : " \u2022 ").withColor(questDone ? 0xFF6FCF73 : 0xFFA89A86)
                    .append(name.copy().withColor(questDone ? 0xFFA89A86 : 0xFFE0D4BC))
                    .append(Component.literal(questDone ? "" : "  " + ticked + "/" + quest.objectives().size()).withColor(0xFF8E8270)));
        }
        if (quests.isEmpty() || finished < quests.size()) {
            lines.add(Component.translatableWithFallback("folios.mastery.hint", "Finish every quest to master the book").withStyle(ChatFormatting.ITALIC));
        } else {
            lines.add(Component.translatableWithFallback("folios.mastery.done", "Mastered").withStyle(ChatFormatting.ITALIC).withColor(0xFFE0B23A));
        }
        return lines;
    }

    private static final net.ixdarklord.folios.internal.client.paint.PixelIcon TROPHY = net.ixdarklord.folios.internal.client.paint.PixelIcon.of("trophy", 9, 9);
    private static final net.ixdarklord.folios.internal.client.paint.PixelIcon TROPHY_SHEEN = net.ixdarklord.folios.internal.client.paint.PixelIcon.of("trophy_sheen", 9, 9, 10);

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
        for (FoliosSession.Found found : session.search(query)) {
            rows.add(found.listing() != null ? this.listingRow(found.entry(), found.listing()) : this.entryRow(found.entry()));
        }
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

    private List<FoliosPageElement.Laid> indexPages() {
        record Listed(Row row, boolean open, String name) {}
        List<Listed> listed = new ArrayList<>();
        for (FoliosCategory category : book.visibleCategories()) {
            if (!category.isOpen()) continue;
            for (FoliosEntry entry : book.entries(category.id())) {
                listed.add(new Listed(this.entryRow(entry), entry.isOpen(), entry.title().getString()));
                for (FoliosEntry.Listing listing : entry.listings()) {
                    if (!listing.isVisible(entry)) continue;
                    boolean open = listing.isOpen(entry);
                    listed.add(new Listed(this.listingRow(entry, listing), open, open ? listing.title(entry).getString() : ""));
                }
            }
        }
        listed.sort(java.util.Comparator.comparing((Listed one) -> !one.open()).thenComparing(one -> one.name().toLowerCase(java.util.Locale.ROOT)));
        List<Row> rows = new ArrayList<>();
        for (Listed one : listed) rows.add(one.row());
        return this.rows(Component.translatableWithFallback("folios.index", "Index"), rows.isEmpty() ? "Nothing to list yet." : null, rows);
    }

    private Row listingRow(FoliosEntry entry, FoliosEntry.Listing listing) {
        boolean open = listing.isOpen(entry);
        Component title = open ? listing.title(entry) : Component.literal("???");
        Row row = new Row(title, open ? listing.icon(entry) : FoliosIcon.NONE, open ? listing.link(entry) : null, () -> false, null);
        return open && entry.isOpen() && !title.getString().equals(entry.title().getString()) ? row.withNote(entry.title()) : row;
    }

    private Row entryRow(FoliosEntry entry) {
        Row row = new Row(entry.isOpen() ? entry.title() : Component.literal("???"), entry.isOpen() ? entry.icon() : FoliosIcon.NONE,
                entry.isOpen() ? "entry:" + entry.id() : null, () -> entry.isOpen() && !memory.isRead(entry.id()), null);
        return entry.isOpen() && entry.isQuest() ? row.withQuest(entry.objectives()) : row;
    }

    private boolean unread(String category) {
        return book.entries(category).stream().anyMatch(entry -> entry.isOpen() && !memory.isRead(entry.id()));
    }

    private record Row(Component label, FoliosIcon icon, @Nullable String link, BooleanSupplier fresh, @Nullable String category,
                       List<FoliosObjective> quest, @Nullable Component note) {
        Row(Component label, FoliosIcon icon, @Nullable String link, BooleanSupplier fresh, @Nullable String category) {
            this(label, icon, link, fresh, category, List.of(), null);
        }

        Row withQuest(List<FoliosObjective> objectives) {
            return new Row(label, icon, link, fresh, category, objectives, note);
        }

        Row withNote(Component note) {
            return new Row(label, icon, link, fresh, category, quest, note);
        }
    }

    private static final int NOTE_WIDTH = 46;

    @FunctionalInterface
    private interface LineDraw {
        void draw(FoliosPainter painter, int y, int right);
    }

    private record Line(int height, LineDraw draw) {}

    private List<FoliosPageElement.Laid> rows(Component title, @Nullable String text, List<Row> rows) {
        return this.paged(title, text, this.listLines(rows, session.layout().contentWidth()));
    }

    private List<Line> listLines(List<Row> rows, int width) {
        List<Line> lines = new ArrayList<>();
        for (Row row : rows) lines.add(new Line(this.height(row, width), (painter, y, right) -> this.row(painter, row, y, right)));
        return lines;
    }

    private List<FoliosPageElement.Laid> paged(Component title, @Nullable String text, List<Line> lines) {
        FoliosLayout layout = session.layout();
        List<FoliosPageElement.Laid> pages = new ArrayList<>();
        FoliosText description = text == null ? null : layout.text(text);
        int top = FoliosLayout.CONTENT_TOP + FoliosLayout.TITLE_HEIGHT + (description != null ? description.height() + 8 : 0);
        int index = 0;
        boolean firstPage = true;
        do {
            int y = firstPage ? top : FoliosLayout.CONTENT_TOP;
            int fits = 0;
            for (int at = y; index + fits < lines.size(); fits++) {
                at += lines.get(index + fits).height();
                if (at > layout.contentBottom() && fits > 0) break;
            }
            List<Line> on = lines.subList(index, Math.min(lines.size(), index + Math.max(1, fits)));
            index += on.size();
            boolean titled = firstPage;
            pages.add(painter -> {
                if (titled) {
                    FoliosLayout.title(painter, title, FoliosLayout.CONTENT_TOP);
                    if (description != null) description.draw(painter, FoliosLayout.CONTENT_LEFT, FoliosLayout.CONTENT_TOP + FoliosLayout.TITLE_HEIGHT);
                }
                int lineY = titled ? top : FoliosLayout.CONTENT_TOP;
                for (Line line : on) {
                    line.draw().draw(painter, lineY, painter.contentRight());
                    lineY += line.height();
                }
            });
            firstPage = false;
        } while (index < lines.size());
        return pages;
    }

    private static final int SCROLL_GAP = 3;
    private static final int SCROLL_FADE = 12;
    private static final int SCROLL_PAD = 2;

    private List<FoliosPageElement.Laid> scrolled(Component title, IntFunction<List<Line>> laying) {
        FoliosLayout layout = session.layout();
        int top = FoliosLayout.CONTENT_TOP + FoliosLayout.TITLE_HEIGHT;
        int room = layout.contentBottom() - top;
        List<Line> whole = laying.apply(layout.contentWidth());
        if (whole.stream().mapToInt(Line::height).sum() <= room) return this.paged(title, null, whole);
        List<Line> lines = laying.apply(layout.contentWidth() - Shapes.SCROLLBAR - SCROLL_GAP);
        int[] offsets = new int[lines.size() + 1];
        for (int index = 0; index < lines.size(); index++) offsets[index + 1] = offsets[index] + lines.get(index).height();
        int total = offsets[lines.size()];
        int furthest = total - room;
        float[] target = {0};
        float[] eased = {-1, 0};
        return List.of(painter -> {
            FoliosLayout.title(painter, title, FoliosLayout.CONTENT_TOP);
            int right = painter.contentRight();
            int barX = right - Shapes.SCROLLBAR;
            float now = painter.time();
            if (eased[0] < 0 || now < eased[1]) eased[0] = target[0];
            else eased[0] += (target[0] - eased[0]) * Math.min(1, (now - eased[1]) * 14);
            if (Math.abs(target[0] - eased[0]) < 0.05F) eased[0] = target[0];
            eased[1] = now;
            float offset = eased[0];
            int windowTop = top - SCROLL_PAD;
            int windowBottom = top + room + SCROLL_PAD;
            int left = FoliosLayout.CONTENT_LEFT - SCROLL_PAD;
            painter.clip(left, windowTop, barX - 1 - left, windowBottom - windowTop, () -> {
                FoliosPainter masked = EdgeFadePainter.window(painter, windowTop, windowBottom,
                        Math.min(SCROLL_FADE, offset), Math.min(SCROLL_FADE, furthest - offset));
                masked.push();
                masked.translate(0, -offset);
                for (int index = 0; index < lines.size(); index++) {
                    int y = top + offsets[index];
                    if (y + lines.get(index).height() - offset > windowTop && y - offset < windowBottom) {
                        lines.get(index).draw().draw(masked, y, barX - SCROLL_GAP);
                    }
                }
                masked.pop();
            });
            painter.scroll(FoliosLayout.CONTENT_LEFT, top, right - FoliosLayout.CONTENT_LEFT, room, notches -> {
                int at = 0;
                while (at < lines.size() && offsets[at] < target[0] - 0.5F) at++;
                int next = notches > 0 ? Math.max(0, at - 1) : Math.min(lines.size(), offsets[at] > target[0] + 0.5F ? at : at + 1);
                target[0] = Math.max(0, Math.min(furthest, offsets[next]));
            });
            int thumb = Math.max(8, room * room / total);
            int travel = Math.max(1, room - thumb);
            boolean held = painter.drag(barX - 2, top, Shapes.SCROLLBAR + 4, room,
                    (dx, dy) -> target[0] = Math.max(0, Math.min(furthest, target[0] + dy * furthest / (float) travel)));
            Shapes.scrollbar(painter, barX, top, room, top + Math.round(travel * offset / furthest), thumb, held);
        });
    }

    private static final int TILE_WIDTH = 34;
    private static final int TILE_GAP = 4;
    private static final int TILE_CARD = 30;
    private static final int TILE_NAME = 2 * Name.STEP;

    private List<Line> gridLines(List<Row> rows, int width) {
        int across = Math.max(1, (width + TILE_GAP) / (TILE_WIDTH + TILE_GAP));
        int tile = (width - (across - 1) * TILE_GAP) / across;
        int inset = (width - across * tile - (across - 1) * TILE_GAP) / 2;
        boolean bars = rows.stream().anyMatch(this::barred);
        int tall = TILE_CARD + 3 + TILE_NAME + (bars ? 7 : 0);
        List<Line> lines = new ArrayList<>();
        for (int start = 0; start < rows.size(); start += across) {
            List<Row> on = rows.subList(start, Math.min(rows.size(), start + across));
            lines.add(new Line(tall + TILE_GAP, (painter, y, right) -> {
                for (int index = 0; index < on.size(); index++) {
                    this.tile(painter, on.get(index), FoliosLayout.CONTENT_LEFT + inset + index * (tile + TILE_GAP), y, tile, tall);
                }
            }));
        }
        return lines;
    }

    private void tile(FoliosPainter painter, Row row, int x, int y, int width, int height) {
        FoliosPalette palette = painter.palette();
        boolean over = row.link != null && painter.action(x - 1, y - 1, width + 2, height + 2, null, () -> this.follow(row.link));
        if (over) {
            highlight(painter, x - 2, y - 2, x + width + 2, y + height + 2, FoliosPalette.alpha(palette.accent(), 0x2A));
            FoliosCategory category = row.category == null ? null : book.category(row.category);
            painter.tooltip(FoliosTooltips.of(row.label, category == null ? null : this.tabDetail(category, "")));
        }
        Shapes.panel(painter, x, y, x + width, y + TILE_CARD);
        int middle = x + width / 2;
        if (!row.icon.isEmpty()) {
            painter.push();
            painter.translate(middle - 12, y + 3);
            painter.scale(1.5F);
            row.icon.draw(EdgeFadePainter.quiet(painter), 0, 0);
            painter.pop();
        } else {
            painter.push();
            painter.translate(middle - 5, y + TILE_CARD / 2 - 7);
            painter.scale(2);
            painter.text(Component.literal("?"), 0, 0, palette.muted());
            painter.pop();
        }
        if (row.fresh.getAsBoolean()) {
            net.ixdarklord.folios.internal.client.screen.FoliosBar.sparkle(painter, x + width - 9, y + 2, x * 0.037F, palette.accent() | 0xFF000000);
        }
        Name name = fit(painter.font(), row.label, width - 2);
        int nameColor = row.link == null ? palette.muted() : over ? palette.heading() : palette.text();
        int nameY = y + TILE_CARD + 3 + (TILE_NAME - name.height()) / 2;
        for (int line = 0; line < name.lines().size(); line++) {
            FormattedCharSequence shown = name.lines().get(line);
            float wide = painter.font().width(shown) * name.scale();
            painter.push();
            painter.translate(middle - wide / 2, nameY + (name.lines().size() == 1 ? (1 - name.scale()) * 4 : line * Name.STEP));
            painter.scale(name.scale());
            painter.text(shown, 0, 0, nameColor);
            painter.pop();
        }
        if (row.category != null && book.showProgress() && row.link != null) {
            int[] read = this.progress(row.category);
            float progress = read[1] == 0 ? 0 : read[0] / (float) read[1];
            if (read[1] > 1) QuestPageElement.stepped(painter, x + 2, y + height - 5, width - 4, progress, read[1], progress >= 1 ? 0xFF4CAF50 : palette.accent());
        }
    }

    private boolean barred(Row row) {
        return row.category != null && book.showProgress() && row.link != null || !row.quest.isEmpty();
    }

    private int height(Row row, int width) {
        return (this.barred(row) ? BAR_ROW : ROW) + this.name(row, session.layout().font(), width).extra();
    }

    private record Name(List<FormattedCharSequence> lines, float scale, boolean cut) {
        static final int MOST = 2;
        static final float SMALL = 0.75F;
        static final int STEP = 8;

        int extra() {
            return (lines.size() - 1) * STEP;
        }

        int height() {
            return lines.size() == 1 ? 8 : lines.size() * STEP;
        }
    }

    private Name name(Row row, Font font, int contentWidth) {
        return fit(font, row.label, Math.max(10, contentWidth - 20 - FRESH - this.countWidth(row, font)));
    }

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

    private static final int FRESH = 10;

    private int countWidth(Row row, Font font) {
        Component count = this.count(row);
        return count == null ? 0 : font.width(count) + 4;
    }

    private @Nullable Component count(Row row) {
        if (row.note != null) {
            Font font = session.layout().font();
            if (font.width(row.note) <= NOTE_WIDTH) return row.note;
            return Component.literal(font.plainSubstrByWidth(row.note.getString(), NOTE_WIDTH - font.width("…")) + "…");
        }
        if (row.category != null && book.showProgress() && row.link != null) {
            int[] read = this.progress(row.category);
            return Component.literal(read[0] + "/" + read[1]);
        }
        if (row.quest.isEmpty()) return null;
        int done = 0;
        for (FoliosObjective objective : row.quest) if (objective.isDone(book)) done++;
        return done == row.quest.size() ? Component.literal("✔") : Component.literal(done + "/" + row.quest.size());
    }

    private static void highlight(FoliosPainter painter, int x0, int y0, int x1, int y1, int argb) {
        painter.fill(x0 + 2, y0, x1 - 2, y0 + 1, argb);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, argb);
        painter.fill(x0, y0 + 2, x1, y1 - 2, argb);
        painter.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, argb);
        painter.fill(x0 + 2, y1 - 1, x1 - 2, y1, argb);
    }

    private void row(FoliosPainter painter, Row row, int y, int right) {
        FoliosPalette palette = painter.palette();
        int left = FoliosLayout.CONTENT_LEFT;
        int height = this.height(row, right - left);
        boolean over = row.link != null && painter.action(left - 2, y, right - left + 4, height - 1, null, () -> this.follow(row.link));
        if (over) highlight(painter, left - 2, y, right + 2, y + height - 1, FoliosPalette.alpha(palette.accent(), 0x2A));
        boolean barred = this.barred(row);
        Name name = this.name(row, painter.font(), right - left);
        int iconY = barred ? (y + 3 + y + height - 2) / 2 - 8 : y + 2 + name.extra() / 2;
        int textY = barred ? y + 3 : iconY + 8 - name.height() / 2;
        if (!row.icon.isEmpty()) {
            painter.push();
            painter.translate(left, iconY);
            row.icon.draw(row.category != null ? EdgeFadePainter.quiet(painter) : painter, 0, 0);
            painter.pop();
        } else {
            painter.centeredText(Component.literal("?"), left + 8, iconY + 4, palette.muted());
        }
        boolean fresh = row.fresh.getAsBoolean();
        int textRight = right - (fresh ? FRESH : 0);
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
        } else if (row.note != null) {
            count = this.count(row);
        }
        boolean complete = progress >= 1;
        if (count != null) {
            int width = painter.font().width(count);
            painter.text(count, textRight - width, textY, complete ? 0xFF3E8E41 : palette.muted());
            textRight -= width + 4;
        }
        int nameColor = row.link == null ? palette.muted() : over ? palette.heading() : palette.text();
        for (int line = 0; line < name.lines().size(); line++) {
            painter.push();
            painter.translate(left + 20, textY + (name.lines().size() == 1 ? (1 - name.scale()) * 4 : line * Name.STEP));
            painter.scale(name.scale());
            painter.text(name.lines().get(line), 0, 0, nameColor);
            painter.pop();
        }
        if (name.cut() && painter.hovered(left + 20, textY, textRight - left - 20, name.height())) painter.tooltip(row.label);
        if (fresh) net.ixdarklord.folios.internal.client.screen.FoliosBar.sparkle(painter, right - 7, textY, y * 0.037F,
                palette.accent() | 0xFF000000);
        if (progress >= 0) {
            QuestPageElement.stepped(painter, left + 20, textY + 11 + name.extra(), right - left - 21, progress, steps, complete ? 0xFF4CAF50 : palette.accent());
        }
        painter.fill(left + 20, y + height - 2, right, y + height - 1, FoliosPalette.alpha(palette.line(), 0x60));
    }

    public void chrome(FoliosPainter painter, Spot spot, boolean right, boolean pressable, @Nullable Runnable shut) {
        FoliosPalette palette = painter.palette();
        FoliosBook.Layout layout = book.layout();
        int width = painter.pageWidth();
        int size = Shapes.BUTTON;
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
            Shapes.acrossFold(painter, Component.literal(String.valueOf(spot.page + 1)).getVisualOrderText(), bottom - layout.numberY(), palette.muted(), right);
        } else {
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

    public int tabColor(@Nullable FoliosCategory category, FoliosPalette palette, boolean here, boolean hovered) {
        return this.tabColor(category, palette, here ? 1 : hovered ? 0.55F : 0);
    }

    public int tabColor(@Nullable FoliosCategory category, FoliosPalette palette, float out) {
        int base = category != null && category.color() >= 0 ? 0xFF000000 | category.color() : palette.accent() | 0xFF000000;
        float t = Math.max(0, out);
        if (t > 1) return FoliosPalette.mix(base, 0xFFFFFFFF, 0.3F * (t - 1));
        return FoliosPalette.mix(base, 0xFF000000 | book.cover(), 0.4F * (1 - t));
    }

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

    public static String tabKey(@Nullable FoliosCategory category, String part) {
        return category != null ? "category:" + category.id() : part;
    }

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

    public void moveAlong(Spot next) {
        if (next.equals(spot)) return;
        this.leaf(next, !next.chapter.equals(spot.chapter) || next.page > spot.page);
    }

    public float flipProgress() {
        if (flip == null) return 1;
        float progress = (net.ixdarklord.folios.internal.client.FoliosClock.millis() - flip.start()) / FLIP_MILLIS;
        if (progress >= 1) {
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

    private net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup pinned;

    public net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup pinned() {
        return pinned;
    }

    public void unpin() {
        pinned = null;
    }

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
            ConfirmLinkScreen.confirmLinkNow(minecraft.gui.screen(), java.net.URI.create(target.url()));
        } else if (target.category() != null) {
            this.category(target.category());
        } else if (target.entry() != null) {
            this.go(new Spot("entry:" + target.entry(), even(target.page())));
            this.flash(target.entry(), target.marker());
        }
    }

    @Override
    public void open(FoliosBook.Location location) {
        this.go(new Spot("entry:" + location.entry(), even(session.pageOf(location))));
        if (location.element() >= 0) this.flash(location.entry(), FoliosSession.elementMarker(location));
    }

    @Override
    public FoliosBook.@Nullable Location here() {
        return spot.chapter.startsWith("entry:") ? session.location(spot.chapter.substring(6), spot.page) : null;
    }

    public @Nullable FoliosEntry entry() {
        return spot.chapter.startsWith("entry:") ? book.entry(spot.chapter.substring(6)) : null;
    }

    public @Nullable FoliosCategory category() {
        if (spot.chapter.startsWith("category:")) return book.category(spot.chapter.substring(9));
        FoliosEntry entry = this.entry();
        return entry != null ? book.category(entry.category()) : null;
    }

    public void save() {
        FoliosBook.Location here = this.here();
        if (here != null) memory.setLast(here);
        boolean other = here == null && !spot.chapter.startsWith("search:");
        memory.setLastSpread(other ? spot.chapter : null, other ? spot.page : 0);
        if (here == null && other) memory.setLast(null);
        memory.save();
    }
}
