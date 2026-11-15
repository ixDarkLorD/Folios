package net.ixdarklord.folios.internal.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.api.FoliosBooks;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.FoliosClient;
import net.ixdarklord.folios.internal.client.FoliosClock;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.ixdarklord.folios.internal.client.page.Shapes;
import net.ixdarklord.folios.internal.client.paint.ScreenPainter;
import net.ixdarklord.folios.internal.compat.CompatEditBox;
import net.ixdarklord.folios.internal.compat.CompatScreen;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.folios.internal.compat.KeyEvent;
import net.ixdarklord.folios.internal.compat.MouseButtonEvent;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LibraryScreen extends CompatScreen {
    private static final int PAD = 14;
    private static final int CARD = 26;
    private static final int GAP = 3;
    private static final int HEAD = 36;
    private static final int COVER = 5;
    private static final int SEARCH_WIDTH = 120;

    private final @Nullable InteractionHand hand;
    private final List<FoliosBook> books = new ArrayList<>();
    private List<FoliosBook> shown = List.of();
    private @Nullable CompatEditBox search;
    private final FoliosPalette palette;
    private final int cover;
    private @Nullable FoliosReader navigator;
    private String query = "";
    private int scroll;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int columns;

    public LibraryScreen(@Nullable InteractionHand hand) {
        super(Component.translatableWithFallback("folios.library.name", "Compendium"));
        this.hand = hand;
        for (FoliosBook book : FoliosLibrary.books().values()) if (FoliosLibrary.isListed(book.id())) books.add(book);
        books.sort(Comparator.comparing(book -> book.title().getString().toLowerCase(Locale.ROOT)));
        FoliosBook library = FoliosLibrary.book(Folios.LIBRARY);
        this.palette = FoliosScreen.pagePalette(library != null ? library : books.getFirst());
        this.cover = 0xFF000000 | (library != null ? library.cover() : 0x26304A);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(340, this.width - 20 - COVER * 2);
        columns = panelWidth >= 300 ? 2 : 1;
        int rows = Math.max(2, (books.size() + columns - 1) / columns);
        panelHeight = Math.min(Math.min(250, this.height - 30 - COVER * 2), HEAD + rows * (CARD + GAP) - GAP + PAD);
        left = (this.width - panelWidth) / 2;
        top = (this.height - panelHeight) / 2;
        String typed = search != null ? search.getValue() : query;
        int searchWidth = Math.min(SEARCH_WIDTH, panelWidth / 2 - PAD);
        search = new CompatEditBox(this.font, searchWidth - 8, 10, Component.translatableWithFallback("folios.search", "Search"));
        search.setPosition(left + panelWidth - PAD - searchWidth + 4, top + 13);
        search.setBordered(false);
        search.setTextShadow(false);
        search.setTextColor(palette.text());
        search.setHint(Component.translatableWithFallback("folios.search.hint", "Search...").withStyle(style -> style.withItalic(true).withColor(palette.muted())));
        search.setMaxLength(48);
        search.setValue(typed);
        search.setResponder(text -> {
            query = text;
            scroll = 0;
            this.filter();
        });
        this.addRenderableWidget(search);
        this.setInitialFocus(search);
        this.filter();
    }

    private void filter() {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        List<FoliosBook> found = new ArrayList<>();
        for (FoliosBook book : books) {
            if (wanted.isEmpty() || book.title().getString().toLowerCase(Locale.ROOT).contains(wanted)
                    || book.id().toString().contains(wanted) || line(book).getString().toLowerCase(Locale.ROOT).contains(wanted)) {
                found.add(book);
            }
        }
        shown = found;
    }

    private static Component line(FoliosBook book) {
        if (!book.subtitle().isBlank()) return FoliosText.in(book, () -> FoliosText.inline(book.subtitle()));
        Component credit = FoliosLibrary.credit(book.id());
        return credit != null ? credit : Component.literal(book.id().getNamespace());
    }

    private int listTop() {
        return top + HEAD;
    }

    private int rowsVisible() {
        return Math.max(1, (panelHeight - HEAD - PAD + GAP) / (CARD + GAP));
    }

    private int rows() {
        return (shown.size() + columns - 1) / columns;
    }

    private int cardWidth() {
        return (panelWidth - PAD * 2 - GAP * 2 * (columns - 1)) / columns;
    }

    private int at(double mouseX, double mouseY) {
        int width = this.cardWidth();
        for (int row = 0; row < this.rowsVisible(); row++) {
            for (int column = 0; column < columns; column++) {
                int index = (scroll + row) * columns + column;
                if (index >= shown.size()) return -1;
                int x = left + PAD + column * (width + GAP * 2);
                int y = this.listTop() + row * (CARD + GAP);
                if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + CARD) return index;
            }
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int right = left + panelWidth;
        int bottom = top + panelHeight;
        graphics.fill(left - COVER, top - COVER, right + COVER, bottom + COVER, FoliosPalette.mix(cover, 0xFF000000, 0.35F));
        graphics.fill(left - COVER + 1, top - COVER + 1, right + COVER - 1, bottom + COVER - 1, cover);
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, FoliosPalette.mix(cover, palette.accent(), 0.45F));
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, FoliosPalette.mix(cover, 0xFF000000, 0.5F));
        graphics.fill(left, top, right, bottom, palette.paper());
        FoliosSession session = FoliosLibrary.session(Folios.LIBRARY);
        FoliosBook library = FoliosLibrary.book(Folios.LIBRARY);
        float seconds = FoliosClock.millis() / 1000F;
        if (session != null && library != null) {
            if (navigator == null) navigator = new FoliosReader(library, session, null);
            ScreenPainter painter = new ScreenPainter(graphics, left, top, 1, palette, session, navigator, seconds, Float.NaN, Float.NaN, new ArrayList<>());
            try {
                Shapes.pageFrame(painter, library.textures().frame(), panelWidth, panelHeight);
                this.list(painter, mouseX, mouseY);
            } finally {
                painter.end();
            }
        }
        Component heading = this.title.copy().withStyle(ChatFormatting.BOLD);
        graphics.text(this.font, heading, left + PAD, top + 13, palette.heading(), false);
        int line = top + 27;
        graphics.fill(left + PAD, line, right - PAD, line + 1, FoliosPalette.alpha(palette.line(), 0xC0));
        graphics.fill(left + PAD, line, left + PAD + Math.min(this.font.width(heading), 24), line + 1, palette.accent());
        Component hint = this.hand != null
                ? Component.translatableWithFallback("folios.library.choose", "Choose the book this one becomes")
                : Component.translatableWithFallback("folios.library.choose_read", "Choose a book to read");
        graphics.text(this.font, hint, left + (panelWidth - this.font.width(hint)) / 2, bottom + COVER + 5, 0xFFE8E2D4, true);
        if (search != null) {
            int x0 = search.getX() - 4;
            int y0 = search.getY() - 4;
            int x1 = search.getX() + search.getWidth() + 4;
            int y1 = y0 + 16;
            int edge = search.isFocused() ? palette.accent() : FoliosPalette.mix(palette.paper(), palette.line(), 0.9F);
            graphics.fill(x0 + 1, y0, x1 - 1, y1, edge);
            graphics.fill(x0, y0 + 1, x1, y1 - 1, edge);
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FoliosPalette.mix(palette.paper(), palette.panel(), 0.6F));
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void list(ScreenPainter painter, int mouseX, int mouseY) {
        if (shown.isEmpty()) {
            Component none = books.isEmpty()
                    ? Component.translatableWithFallback("folios.library.empty", "No other guide books are installed.")
                    : Component.translatableWithFallback("folios.library.none", "No book matches.");
            painter.centeredText(none, panelWidth / 2, HEAD + 16, palette.muted());
            return;
        }
        int width = this.cardWidth();
        int over = this.at(mouseX, mouseY);
        for (int row = 0; row < this.rowsVisible(); row++) {
            for (int column = 0; column < columns; column++) {
                int index = (scroll + row) * columns + column;
                if (index >= shown.size()) break;
                FoliosBook book = shown.get(index);
                int x = PAD + column * (width + GAP * 2);
                int y = HEAD + row * (CARD + GAP);
                boolean hovered = index == over;
                if (hovered) painter.fill(x - 2, y, x + width + 2, y + CARD - 1, FoliosPalette.alpha(palette.accent(), 0x2A));
                painter.fill(x, y + 3, x + 2, y + CARD - 4, 0xFF000000 | book.accent());
                book.icon().draw(painter, x + 6, y + (CARD - 16) / 2 - 1);
                int textLeft = x + 27;
                int room = width - 29;
                painter.text(Component.literal(this.fit(book.title().getString(), room)).getVisualOrderText(), textLeft, y + 4, hovered ? palette.heading() : palette.text());
                painter.text(Component.literal(this.fit(line(book).getString(), room)).getVisualOrderText(), textLeft, y + 14, palette.muted());
                painter.fill(textLeft, y + CARD - 1, x + width, y + CARD, FoliosPalette.alpha(palette.line(), 0x60));
            }
        }
        int rows = this.rows();
        int visible = this.rowsVisible();
        if (rows > visible) {
            int trackHeight = visible * (CARD + GAP) - GAP;
            int thumb = Math.max(12, trackHeight * visible / rows);
            int thumbTop = HEAD + (trackHeight - thumb) * scroll / Math.max(1, rows - visible);
            Shapes.scrollbar(painter, panelWidth - PAD + 5, HEAD, trackHeight, thumbTop, thumb, false);
        }
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        int index = this.at(event.x(), event.y());
        if (index < 0) return false;
        this.choose(shown.get(index));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, this.rows() - this.rowsVisible()));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && !shown.isEmpty()) {
            this.choose(shown.getFirst());
            return true;
        }
        return super.keyPressed(event);
    }

    private void choose(FoliosBook book) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        if (hand == null || player == null) {
            this.onClose();
            FoliosClient.open(book.id(), null, null);
            return;
        }
        ItemStack held = player.getItemInHand(hand);
        if (Folios.LIBRARY.equals(FoliosBooks.bookOf(held))) {
            held.set(FoliosRegistry.GUIDE_BOOK.get(), book.id());
            Network.sendToServer(new FoliosPayloads.ChooseBook(hand.ordinal(), book.id()));
            player.displayClientMessage(Component.translatableWithFallback("folios.library.became", "The Compendium became %s", book.title()), true);
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
