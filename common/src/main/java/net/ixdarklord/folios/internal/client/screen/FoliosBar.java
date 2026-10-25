package net.ixdarklord.folios.internal.client.screen;

import net.ixdarklord.folios.internal.client.paint.PixelIcon;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// The bar over a book, a ribbon banner: a band of cloth in the cover's colour across the book, its swallowtail ends
// folded back behind it at either side, stitched in gilt along both edges; its buttons small brass plates pinned to
// it. On it: where the reader is, a bookmark star, search, paper mode (for books drawn in it), fold and close.
// Folded, it is a little pull of the same leather at the middle of its foot, which opens it again. Shared by both kinds
// of book and drawn as their pages are, through a painter: flat over a screen book, and in the world over a handheld
// one, where a leaf turning past it goes in front of it. It draws in its own pixels, (0, 0) its top left, its foot at
// HEIGHT. The search field is a hidden text box that takes the keys; the bar draws what is typed in it.
public final class FoliosBar {
    public static final int HEIGHT = 24;
    /** Where its content starts, below its top gilt line. */
    public static final int INSET = 4;
    /** How tall the bar is folded away: room for its arrow with the leather's edges clear above and below. */
    private static final int PULL = 13;
    private static final int SEARCH_WIDTH = 92;
    /** From one button on the bar to the next: its 16 pixels and a little gap. */
    private static final int STEP = 18;
    private static final float MILLIS = 160;

    /** Gives the search field the keyboard: the screen's focus and the field's own. */
    @FunctionalInterface
    public interface Focus {
        void focus(StyledEditBox field);
    }

    // Folded or not, for the rest of the game, whichever book.
    private static boolean folded;

    private final FoliosReader reader;
    private final FoliosBook book;
    private final Font font;
    private final boolean modeToggle;
    private final @Nullable Runnable close;
    private final Focus focus;
    private long movedAt;
    private @Nullable StyledEditBox search;
    private int width;
    // The search field folds to a glass while it is not being typed in: whether it is wanted open, and since when.
    private boolean searchWanted;
    private long searchMovedAt;

    /**
     * @param modeToggle whether it has the light/dark paper button (a book whose paper follows the mode)
     * @param close      what its close button does, or null for none
     * @param focus      how the screen gives the search field the keyboard
     */
    public FoliosBar(FoliosReader reader, boolean modeToggle, @Nullable Runnable close, Focus focus) {
        this.reader = reader;
        this.book = reader.book;
        this.font = Minecraft.getInstance().font;
        this.modeToggle = modeToggle;
        this.close = close;
        this.focus = focus;
    }

    // ---- The search field ----

    /**
     * Makes the search field, carrying over what an earlier one held; the screen adds it as a widget. It is kept off
     * the screen: it only takes the keys, and the bar shows what is in it.
     */
    public StyledEditBox search(@Nullable StyledEditBox before) {
        StyledEditBox box = new StyledEditBox(font, SEARCH_WIDTH, 16, Component.translatableWithFallback("folios.search", "Search"));
        box.setPosition(-10000, -10000);
        box.setPlaceholder(Component.translatableWithFallback("folios.search.hint", "Search...").withStyle(ChatFormatting.ITALIC));
        box.setMaxLength(48);
        box.setValue(before != null ? before.getValue() : "");
        box.setResponder(this::searched);
        this.search = box;
        return box;
    }

    // Where the search field's right edge is, before paper mode and fold.
    private int searchRight() {
        // The buttons from the right, each a step from the last; the search field the same step past the last of them.
        int buttons = (modeToggle ? 1 : 0) + (close != null ? 1 : 0);
        return width - 2 - buttons * STEP;
    }

    private int searchX() {
        return this.searchRight() - SEARCH_WIDTH;
    }

    // How open the search field is, 0 a glass to 1 the field, eased; it opens while it is typed in.
    private float searchOpenness() {
        boolean wanted = this.typing();
        if (wanted != searchWanted) {
            searchWanted = wanted;
            searchMovedAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
        }
        float t = Math.min(1, (net.ixdarklord.folios.internal.client.FoliosClock.millis() - searchMovedAt) / 140.0F);
        if (!StyleSettings.transitions()) t = 1;
        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        return searchWanted ? eased : 1 - eased;
    }

    public @Nullable StyledEditBox searchField() {
        return search;
    }

    public boolean typing() {
        return search != null && search.isFocused();
    }

    private void searched(String query) {
        if (query.isBlank()) {
            if (reader.spot.chapter().startsWith("search:")) reader.back();
        } else if (query.strip().length() >= 2) {
            reader.search(query);
        }
    }

    // ---- Folding ----

    /** How open it is, 0 folded to 1 open, eased. */
    public float shown() {
        float t = Math.min(1, (net.ixdarklord.folios.internal.client.FoliosClock.millis() - movedAt) / MILLIS);
        if (!StyleSettings.transitions()) t = 1;
        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        return folded ? 1 - eased : eased;
    }

    private void fold(boolean fold) {
        folded = fold;
        movedAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
        if (search != null && fold) search.setFocused(false);
    }

    // ---- Drawing ----

    /** The colours its painter is given: the cover's, so what is drawn see-through lies over the leather. */
    public FoliosPalette palette() {
        int cover = 0xFF000000 | book.cover();
        int text = 0xFFF2EEE6;
        return new FoliosPalette(cover, text, FoliosPalette.alpha(text, 0xA0), 0xFFFFFFFF, this.gilt(), this.gilt(), cover, this.gilt());
    }

    private int gilt() {
        return FoliosPalette.mix(FoliosPalette.mix(0xFF000000 | book.accent(), 0xFFE8C66A, 0.5F), 0xFF000000 | book.cover(), 0.15F);
    }

    /**
     * Draws it in its own pixels, the given width across. {@code appear}, 0 to 1, grows it out of its pull as it
     * comes (a handheld book's, once the book is open); 1 when it is simply there.
     */
    public void draw(FoliosPainter painter, int width, float appear) {
        this.width = width;
        if (appear < 0.03F) {
            if (search != null && search.isFocused()) search.setFocused(false);
            return;
        }
        int cover = 0xFF000000 | book.cover();
        int gilt = this.gilt();
        float shown = Math.min(this.shown(), appear);
        int bottom = HEIGHT;
        if (shown < 0.999F) {
            // The pull, and the bar growing out of it as it opens.
            int wide = Math.round(Mth.lerp(shown, 44, width));
            int tall = Math.round(Mth.lerp(shown, PULL, HEIGHT));
            int x0 = (width - wide) / 2;
            this.ribbon(painter, x0, bottom - tall, x0 + wide, bottom, cover, gilt);
            if (this.shown() < 0.05F) {
                boolean hovered = painter.action(x0, bottom - tall - 2, wide, tall + 4,
                        Component.translatableWithFallback("folios.bar.show", "Show the bar"), () -> this.fold(false));
                int ink = hovered ? 0xFFFFFFFF : FoliosPalette.mix(cover, 0xFFF2EEE6, 0.7F);
                pixels(painter, UNFOLD, x0 + (wide - 9) / 2, bottom - tall + (tall - 9) / 2, 9, ink);
            }
            return;
        }
        this.ribbon(painter, 0, 0, width, bottom, cover, gilt);
        int text = 0xFFF2EEE6;
        int dim = FoliosPalette.mix(cover, text, 0.62F);
        int x = 8;
        // Folding it away: a pocket in the middle of its foot, where its pull is once it is folded.
        this.foldPocket(painter, cover, gilt, text);
        // Close and paper mode, from the right.
        int right = width - 18;
        if (close != null) {
            this.button(painter, right, CLOSE, text, Component.translatableWithFallback("folios.shut", "Close the book"), Component.translatableWithFallback("folios.tip.shut", "Esc puts it away"), close);
            right -= STEP;
        }
        if (modeToggle) {
            boolean dark = ConfigStyle.mode() == ConfigTheme.Mode.DARK;
            this.button(painter, right, dark ? SUN : MOON, text,
                    dark ? Component.translatableWithFallback("coolcatlib.style.mode.light", "Light mode")
                            : Component.translatableWithFallback("coolcatlib.style.mode.dark", "Dark mode"), StyleSettings::toggleThemeMode);
        }
        // Search: a glass that opens into the field when pressed, and folds back once the field lets the keyboard go.
        float searchOpen = this.searchOpenness();
        int searchRight = this.searchRight();
        int searchLeft = Math.round(Mth.lerp(searchOpen, searchRight - 16, this.searchX()));
        StyledEditBox field = search;
        if (searchOpen < 0.005F) {
            boolean searching = field != null && !field.getValue().isBlank();
            this.button(painter, searchLeft, GLASS, searching ? FoliosPalette.mix(gilt, 0xFFFFE680, 0.65F) : text,
                    Component.translatableWithFallback("folios.search", "Search"), Component.translatableWithFallback("folios.tip.search", "Ctrl + F"), field == null ? () -> {} : () -> focus.focus(field));
        } else {
            this.field(painter, searchLeft, searchRight, searchOpen, field);
        }
        // Where the reader is, in the room it has: on one line, shrunk to no less than four fifths; past that on two
        // smaller lines, the book or category over the rest; cut short only if it still doesn't fit. The bookmark star
        // right after it, on an entry.
        FoliosBook.Location here = reader.here();
        // Up to the fold pocket in the middle (or the search field, if that is nearer), its star after it.
        int end = Math.min(searchLeft - 6, (width - POCKET_WIDTH) / 2 - 4);
        int room = end - x - (here != null ? 18 : 0);
        Fitted where = this.fit(room);
        float lineHeight = 9 * where.scale();
        float top = INSET + 8 - (where.lines().size() * lineHeight - where.scale()) / 2;
        for (int index = 0; index < where.lines().size(); index++) {
            painter.push();
            painter.translate(x, top + index * lineHeight);
            painter.scale(where.scale());
            painter.text(where.lines().get(index), 0, 0, index == 0 ? text : FoliosPalette.mix(cover, text, 0.85F));
            painter.pop();
        }
        if (here != null) {
            int star = x + Math.min(room, where.width()) + 2;
            boolean marked = reader.memory.isBookmarked(here.entry());
            Component starName = marked ? Component.translatableWithFallback("folios.bookmark.remove", "Remove bookmark")
                    : Component.translatableWithFallback("folios.bookmark.add", "Bookmark this page");
            boolean hovered = painter.action(star, INSET, 16, 16, starName, () -> reader.memory.toggleBookmark(here));
            if (hovered && !marked) painter.tooltip(net.ixdarklord.folios.internal.client.FoliosTooltips.of(starName, Component.translatableWithFallback("folios.tip.bookmark", "Find it again under the star tab")));
            if (hovered) rounded(painter, star, INSET, star + 16, INSET + 16, 0x30FFFFFF);
            // A marked page's star shines: the gilt lifted toward pale gold, so it stands out on dark leather.
            int shine = FoliosPalette.mix(gilt, 0xFFFFE680, 0.65F);
            pixels(painter, marked ? STAR : STAR_OUTLINE, star, INSET, 16, marked ? (hovered ? 0xFFFFF4C0 : shine) : hovered ? text : dim);
        }
    }

    // The search field, opening out of the glass: pale paper with what is typed in it (its end, if it is too long) and
    // a blinking caret, or the hint while it is empty. Pressed, it takes the keyboard.
    private void field(FoliosPainter painter, int x0, int x1, float open, @Nullable StyledEditBox field) {
        int paper = 0xFFF4F1EA;
        rounded(painter, x0, INSET, x1, INSET + 16, FoliosPalette.mix(paper, 0xFF000000, 0.35F));
        rounded(painter, x0 + 1, INSET + 1, x1 - 1, INSET + 15, paper);
        if (field == null) return;
        String value = field.getValue();
        int ink = 0xFF2E241B;
        boolean clearable = open >= 0.995F && !value.isEmpty();
        // Pressed anywhere but its clear button, it takes the keyboard; the button empties it and keeps the keyboard.
        painter.action(x0, INSET, x1 - x0 - (clearable ? 14 : 0), 16, null, () -> focus.focus(field));
        if (open < 0.995F) return;
        if (clearable) {
            int bx = x1 - 14;
            boolean over = painter.action(bx, INSET, 14, 16, Component.translatableWithFallback("folios.search.clear", "Clear"), () -> {
                field.setValue("");
                focus.focus(field);
            });
            if (over) rounded(painter, bx + 1, INSET + 2, bx + 13, INSET + 14, FoliosPalette.mix(paper, ink, 0.12F));
            pixels(painter, CLOSE, bx, INSET + 1, 14, over ? ink : FoliosPalette.mix(paper, ink, 0.55F));
        }
        int room = x1 - x0 - 8 - (clearable ? 12 : 0);
        if (value.isEmpty()) {
            // Waiting for keys, the hint steps aside for the caret before it.
            int hintX = x0 + 4 + (field.isFocused() ? 4 : 0);
            painter.text(Component.translatableWithFallback("folios.search.hint", "Search...").withStyle(ChatFormatting.ITALIC).getVisualOrderText(),
                    hintX, INSET + 4, FoliosPalette.mix(paper, ink, 0.45F));
        } else {
            String shown = font.width(value) <= room ? value : font.plainSubstrByWidth(value, room, true);
            painter.text(Component.literal(shown).getVisualOrderText(), x0 + 4, INSET + 4, ink);
        }
        if (field.isFocused() && painter.time() % 1.0F < 0.55F) {
            int caret = x0 + 4 + Math.min(room, font.width(value)) + 1;
            painter.fill(caret, INSET + 3, caret + 1, INSET + 13, ink);
        }
    }

    /** The fold pocket's size, in bar pixels. */
    private static final int POCKET_WIDTH = 24;
    private static final int POCKET_HEIGHT = 8;

    // The pocket the bar folds away from: sunk into the middle of its foot, its top corners rounded off smoothly, a
    // shade along its top inside edge and a gilt rim following its shape where the bar's lower gilt line meets it;
    // an arrow up in it. Pressed, the bar folds away into its pull.
    private void foldPocket(FoliosPainter painter, int cover, int gilt, int text) {
        int x0 = (width - POCKET_WIDTH) / 2;
        int x1 = x0 + POCKET_WIDTH;
        int y0 = HEIGHT - POCKET_HEIGHT;
        int y1 = HEIGHT;
        boolean hovered = painter.action(x0, y0, POCKET_WIDTH, POCKET_HEIGHT, Component.translatableWithFallback("folios.bar.hide", "Hide the bar"), () -> this.fold(true));
        int rim = FoliosPalette.mix(cover, gilt, 0.56F);
        int well = FoliosPalette.mix(cover, 0xFF000000, hovered ? 0.2F : 0.38F);
        // The rim: the bar's lower gilt line runs up around the pocket, its corners eased by a step of two.
        painter.fill(x0 - 1, y1 - 3, x0, y1 - 2, cover);
        painter.fill(x1, y1 - 3, x1 + 1, y1 - 2, cover);
        painter.fill(x0 - 1, y0 + 3, x0, y1 - 2, rim);
        painter.fill(x1, y0 + 3, x1 + 1, y1 - 2, rim);
        painter.fill(x0, y0 + 1, x0 + 1, y0 + 3, rim);
        painter.fill(x1 - 1, y0 + 1, x1, y0 + 3, rim);
        painter.fill(x0 + 1, y0, x0 + 3, y0 + 1, rim);
        painter.fill(x1 - 3, y0, x1 - 1, y0 + 1, rim);
        painter.fill(x0 + 3, y0 - 1, x1 - 3, y0, rim);
        // The well, its top corners rounded to match, open at the foot.
        painter.fill(x0 + 3, y0, x1 - 3, y1, well);
        painter.fill(x0 + 1, y0 + 1, x1 - 1, y1, well);
        painter.fill(x0, y0 + 3, x1, y1, well);
        painter.fill(x0 + 3, y0, x1 - 3, y0 + 1, FoliosPalette.mix(well, 0xFF000000, 0.3F));
        int ink = hovered ? 0xFFFFFFFF : FoliosPalette.mix(cover, text, 0.75F);
        pixels(painter, FOLD, x0 + (POCKET_WIDTH - 9) / 2, y0 + (POCKET_HEIGHT - 9) / 2, 9, ink);
    }

    /** How far a ribbon's tails reach out past its ends, and how far down they hang. */
    private static final int TAIL = 9;
    private static final int TAIL_DROP = 3;

    // A ribbon from x0 to x1: its two swallowtail tails behind its ends, hanging a little lower, a fold where each
    // turns back; then the band over them, its cloth woven faintly, lit along its top and shaded along its foot, a
    // stitched gilt seam a pixel inside each edge.
    private void ribbon(FoliosPainter painter, int x0, int y0, int x1, int y1, int cover, int gilt) {
        int tall = y1 - y0;
        int back = FoliosPalette.mix(cover, 0xFF000000, 0.32F);
        int fold = FoliosPalette.mix(cover, 0xFF000000, 0.55F);
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int end = left ? x0 : x1;
            int out = left ? -1 : 1;
            int notch = Math.max(2, tall / 3);
            int top = y0 + TAIL_DROP;
            int bottom = y1 + TAIL_DROP;
            int rows = bottom - top;
            for (int row = 0; row < rows; row++) {
                // The swallowtail: cut in at its middle row by `notch`, out to its full length at its top and foot.
                float fromMiddle = Math.abs(row + 0.5F - rows / 2.0F) / (rows / 2.0F);
                int cut = Math.round(notch * (1 - fromMiddle));
                int reach = TAIL - cut;
                int a = left ? end - reach : end - 4;
                int b = left ? end + 4 : end + reach;
                painter.fill(a, top + row, b, top + row + 1, row == rows - 1 ? FoliosPalette.mix(back, 0xFF000000, 0.25F) : back);
            }
            // The fold where the tail turns back under the band: a small dark triangle under the band's corner.
            for (int row = 0; row < TAIL_DROP; row++) {
                int span = TAIL_DROP - row;
                int a = left ? end : end - span;
                int b = left ? end + span : end;
                painter.fill(a, y1 + row, b, y1 + row + 1, fold);
            }
        }
        // The band.
        painter.fill(x0, y0, x1, y1, cover);
        // A faint weave: every other row a shade darker.
        int weave = FoliosPalette.alpha(0xFF000000, 0x09);
        for (int y = y0 + 1; y < y1 - 1; y += 2) painter.fill(x0, y, x1, y + 1, weave);
        painter.fill(x0, y0, x1, y0 + 1, FoliosPalette.mix(cover, 0xFFFFFFFF, 0.2F));
        painter.fill(x0, y1 - 1, x1, y1, FoliosPalette.mix(cover, 0xFF000000, 0.35F));
        if (tall >= 10) {
            // The seams: stitches of three pixels with gaps of two, a pixel in from each edge and the ends.
            int stitch = FoliosPalette.mix(cover, gilt, 0.7F);
            for (int x = x0 + 3; x < x1 - 3; x += 5) {
                int e = Math.min(x + 3, x1 - 3);
                painter.fill(x, y0 + 2, e, y0 + 3, stitch);
                painter.fill(x, y1 - 3, e, y1 - 2, stitch);
            }
        }
    }

    // A brass plate pinned to the ribbon for a button: bevelled, lit along its top, a rivet's glint at each upper
    // corner; brighter under the pointer.
    private static void plate(FoliosPainter painter, int x, int y, int gilt, int cover, boolean hovered) {
        int brass = FoliosPalette.mix(cover, gilt, hovered ? 0.62F : 0.42F);
        rounded(painter, x, y, x + 16, y + 16, FoliosPalette.mix(brass, 0xFF000000, 0.35F));
        rounded(painter, x + 1, y + 1, x + 15, y + 15, brass);
        painter.fill(x + 2, y + 1, x + 14, y + 2, FoliosPalette.mix(brass, 0xFFFFFFFF, 0.3F));
        painter.fill(x + 2, y + 14, x + 14, y + 15, FoliosPalette.mix(brass, 0xFF000000, 0.25F));
    }

    private void button(FoliosPainter painter, int x, PixelIcon icon, int color, Component name, Runnable action) {
        this.button(painter, x, icon, color, name, null, action);
    }

    // A button, its tooltip a name and a line saying more (a key for it).
    private void button(FoliosPainter painter, int x, PixelIcon icon, int color, Component name, @Nullable Component detail, Runnable action) {
        boolean hovered = painter.action(x, INSET, 16, 16, name, action);
        if (hovered && detail != null) painter.tooltip(net.ixdarklord.folios.internal.client.FoliosTooltips.of(name, detail));
        plate(painter, x, INSET, this.gilt(), 0xFF000000 | book.cover(), hovered);
        pixels(painter, icon, x, INSET, 16, hovered ? 0xFFFFFFFF : color);
    }

    // Book › Category › Entry, or what the reader is looking at.
    /** Where the reader is as drawn: its lines, how large, and how wide they come to on the bar. */
    private record Fitted(List<FormattedCharSequence> lines, float scale, int width) {}

    private Fitted fit(int room) {
        Component where = this.where();
        int wide = font.width(where);
        // On two lines: split where the trail steps on, else wherever the words wrap; as large as lets the longer fit.
        // Only when that is larger than one line would be.
        List<Component> parts = this.whereParts();
        float oneLine = room / (float) wide;
        float twoLines = parts.size() == 2
                ? Math.min(0.75F, room / (float) Math.max(font.width(parts.get(0)), font.width(parts.get(1)) + font.width("› ")))
                : 0.75F;
        if (oneLine >= 0.8F || oneLine >= twoLines) {
            float scale = Math.max(0.6F, Math.min(1.0F, oneLine));
            int fits = Math.round(room / scale);
            FormattedCharSequence line = wide <= fits ? where.getVisualOrderText()
                    : Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(where, fits - font.width("…")), FormattedText.of("…")));
            return new Fitted(List.of(line), scale, Math.round(Math.min(wide, fits) * scale));
        }
        List<FormattedText> lines = new ArrayList<>();
        if (parts.size() == 2) {
            lines.add(parts.get(0));
            lines.add(Component.literal("› ").withStyle(ChatFormatting.GRAY).append(parts.get(1)));
        } else {
            lines.addAll(font.getSplitter().splitLines(where, Math.round(room / 0.75F), net.minecraft.network.chat.Style.EMPTY));
        }
        int longest = 0;
        for (FormattedText line : lines) longest = Math.max(longest, font.width(line));
        float scale = Math.max(0.6F, Math.min(0.75F, room / (float) Math.max(1, longest)));
        int fits = Math.round(room / scale);
        List<FormattedCharSequence> shown = new ArrayList<>();
        int widest = 0;
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            FormattedText line = lines.get(index);
            boolean more = index == 1 && lines.size() > 2;
            if (font.width(line) > fits || more) {
                line = FormattedText.composite(font.substrByWidth(line, fits - font.width("…")), FormattedText.of("…"));
            }
            shown.add(Language.getInstance().getVisualOrder(line));
            widest = Math.max(widest, font.width(line));
        }
        return new Fitted(shown, scale, Math.round(widest * scale));
    }

    // The trail's first step and the rest, for setting it on two lines; one part when it has no steps.
    private List<Component> whereParts() {
        List<Component> steps = this.trail();
        if (steps.size() < 2) return List.of(this.where());
        return List.of(steps.get(0), join(steps.subList(1, steps.size())));
    }

    private Component where() {
        return join(this.trail());
    }

    // Steps of the trail, one after another with an arrow between them.
    private static Component join(List<Component> steps) {
        MutableComponent line = Component.empty();
        for (int index = 0; index < steps.size(); index++) {
            if (index > 0) line.append(Component.literal("  ›  ").withStyle(ChatFormatting.GRAY));
            line.append(steps.get(index));
        }
        return line;
    }

    // Where the reader is, step by step: the book (or, inside an entry, its category: the icon beside it already names
    // the book) in bold, then the category, entry, search, bookmarks or index.
    private List<Component> trail() {
        String chapter = reader.spot.chapter();
        FoliosEntry open = reader.entry();
        FoliosCategory owner = reader.category();
        if (open != null && owner != null) {
            return List.of(bold(owner.title()), plain(open.title()));
        }
        List<Component> steps = new ArrayList<>();
        steps.add(bold(book.title()));
        Component step = null;
        if (chapter.startsWith("search:")) step = Component.translatableWithFallback("folios.search.results", "Search");
        else if (chapter.equals("bookmarks")) step = Component.translatableWithFallback("folios.bookmarks", "Bookmarks");
        else if (chapter.equals("index")) step = Component.translatableWithFallback("folios.index", "Index");
        else if (owner != null) steps.add(plain(owner.title()));
        if (step != null) steps.add(plain(step));
        return steps;
    }

    private static Component bold(Component text) {
        return text.copy().withStyle(ChatFormatting.BOLD);
    }

    private static Component plain(Component text) {
        return text.copy().withStyle(style -> style.withBold(false));
    }

    // ---- Pixel icons ----

    // The bar's icons, white images tinted when drawn (assets/folios/textures/gui/icons/), 9 pixels square at most;
    // a resource pack can redraw them.
    public static final PixelIcon STAR = PixelIcon.of("star", 9, 9);
    /** The star, its edge only: a page not yet bookmarked. */
    public static final PixelIcon STAR_OUTLINE = PixelIcon.of("star_outline", 9, 9);
    private static final PixelIcon SUN = PixelIcon.of("sun", 9, 9);
    private static final PixelIcon MOON = PixelIcon.of("moon", 9, 9);
    private static final PixelIcon GLASS = PixelIcon.of("search", 9, 9);
    private static final PixelIcon CLOSE = PixelIcon.of("close", 7, 7);
    /**
     * A four-pointed sparkle, marking what hasn't been read yet, as the frames of its blink: from a point of light
     * through a small star to its resting shape and a flare with its points drawn out.
     */
    public static final PixelIcon SPARKLE = PixelIcon.of("sparkle", 7, 7, 5);
    /** How long a blink takes, and how often one comes (seconds). */
    private static final float BLINK_TIME = 0.75F;
    private static final float BLINK_EVERY = 2.6F;

    /**
     * Draws the unread sparkle, 7 pixels square from (x, y), blinking now and then; {@code phase} (seconds) staggers
     * sparkles so they don't all blink together. Brighter on its flare. The blink is smooth: the sparkle eases from
     * rest out to its flare, down to a point and back, each pixel fading between the frames on either side.
     */
    public static void sparkle(FoliosPainter painter, int x, int y, float phase, int color) {
        float t = (painter.time() + phase) % BLINK_EVERY;
        float frame = t < BLINK_TIME ? blink(t / BLINK_TIME) : 3;
        int low = Math.min(SPARKLE.frames() - 2, (int) frame);
        float between = frame - low;
        int ink = FoliosPalette.mix(color, 0xFFFFFFFF, 0.35F * Math.max(0, frame - 3));
        int alpha = ink >>> 24;
        if (between < 0.98F) SPARKLE.draw(painter, x, y, low, FoliosPalette.alpha(ink, Math.round(alpha * (1 - between))));
        if (between > 0.02F) SPARKLE.draw(painter, x, y, low + 1, FoliosPalette.alpha(ink, Math.round(alpha * between)));
    }

    // Where the blink is at, as a frame between 0 (a point) and 4 (the flare), 3 at rest: out to the flare, down to a
    // point, and back to rest, each leg eased.
    private static float blink(float t) {
        if (t < 0.2F) return 3 + smooth(t / 0.2F);
        if (t < 0.55F) return 4 - 4 * smooth((t - 0.2F) / 0.35F);
        return 3 * smooth((t - 0.55F) / 0.45F);
    }

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Lines of a list, for the index tab. */
    public static final PixelIcon INDEX = PixelIcon.of("index", 9, 7);
    private static final PixelIcon FOLD = PixelIcon.of("fold", 9, 5);
    private static final PixelIcon UNFOLD = PixelIcon.of("unfold", 9, 5);

    /** An icon centred in a box. */
    static void pixels(GuiGraphicsExtractor graphics, PixelIcon icon, int boxX, int boxY, int boxSize, int color) {
        icon.draw(graphics, boxX + (boxSize - icon.width()) / 2, boxY + (boxSize - icon.height()) / 2, color);
    }

    /** The same, through a painter (a page, a handheld book's tab, the bar). */
    public static void pixels(FoliosPainter painter, PixelIcon icon, int boxX, int boxY, int boxSize, int color) {
        icon.draw(painter, boxX + (boxSize - icon.width()) / 2, boxY + (boxSize - icon.height()) / 2, color);
    }

    private static void rounded(FoliosPainter painter, int x0, int y0, int x1, int y1, int color) {
        painter.fill(x0 + 2, y0, x1 - 2, y1, color);
        painter.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, color);
        painter.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, color);
        painter.fill(x0, y0 + 2, x0 + 1, y1 - 2, color);
        painter.fill(x1 - 1, y0 + 2, x1, y1 - 2, color);
    }
}
