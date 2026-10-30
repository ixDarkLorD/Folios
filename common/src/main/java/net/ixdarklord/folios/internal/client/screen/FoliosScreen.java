package net.ixdarklord.folios.internal.client.screen;

import net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.coolcatcore.api.config.ConfigTheme;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.FoliosMentions;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.PageSize;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.ixdarklord.folios.internal.client.page.Shapes;
import net.ixdarklord.folios.internal.client.paint.ScreenPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

public final class FoliosScreen extends Screen {
    public static final int BORDER = 7;
    private static final int SPINE = 6;
    public static final int BOOK_WIDTH = FoliosLayout.PAGE_WIDTH * 2 + SPINE + BORDER * 2;
    private static final int BAR = FoliosBar.HEIGHT;
    private static final int BAR_GAP = 5;
    private static final int TAB = 22;
    private static final float SPREAD_FOLD = 0.35F;
    private static final float OPEN_MILLIS = 260;

    private record Hit(float x0, float y0, float x1, float y1, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final FoliosReader reader;

    public FoliosReader reader() {
        return reader;
    }
    private final FoliosBook book;
    private final long openedAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
    private final FoliosBar bar;
    private @Nullable StyledEditBox search;
    private final List<Hit> hits = new ArrayList<>();
    private final List<AbstractPainter.Hit> leftHits = new ArrayList<>();
    private final List<AbstractPainter.Hit> rightHits = new ArrayList<>();
    private final PopupLayer popups = new PopupLayer();
    private net.ixdarklord.folios.api.client.page.@org.jspecify.annotations.Nullable FoliosPopup hoveredPopup;
    private final List<AbstractPainter.Hit> unpressable = new ArrayList<>();
    private @Nullable List<Component> tooltip;
    private float originX;
    private float originY;
    private float scale = 1;

    public FoliosScreen(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at) {
        this(book, session, at, null);
    }

    public FoliosScreen(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at, net.minecraft.world.@Nullable InteractionHand hand) {
        super(book.title());
        this.book = book;
        this.reader = new FoliosReader(book, session, at, hand);
        this.bar = new FoliosBar(this.reader, true, this::onClose, field -> {
            this.setFocused(field);
            field.setFocused(true);
        });
    }

    private int pw = FoliosLayout.PAGE_WIDTH;
    private int ph = FoliosLayout.PAGE_HEIGHT;
    private int bookWidth = BOOK_WIDTH;
    private int bookHeight = FoliosLayout.PAGE_HEIGHT + BORDER * 2;

    private float bookScale(int needWidth, int needHeight) {
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        int guiScale = Math.max(1, (int) Math.round(window.getGuiScale()));
        int auto = window.calculateScale(0, net.minecraft.client.Minecraft.getInstance().isEnforceUnicode());
        int fits = (int) Math.min(window.getWidth() / (float) needWidth, window.getHeight() / (float) needHeight);
        int pixels = Math.min(Math.max(auto, guiScale), fits);
        if (pixels < 1) return Math.min(this.width / (float) needWidth, this.height / (float) needHeight);
        return pixels / (float) guiScale;
    }

    @Override
    protected void init() {
        float factor = PageSize.factor();
        float drawn = PageSize.drawn();
        int chromeWidth = SPINE + BORDER * 2 + TAB * 2 + 8;
        int chromeHeight = BORDER * 2 + BAR + BAR_GAP + 8;
        factor = Math.min(factor, Math.min((this.width / drawn - chromeWidth) / 2.0F / FoliosLayout.PAGE_WIDTH,
                (this.height / drawn - chromeHeight) / (float) FoliosLayout.PAGE_HEIGHT));
        this.pw = PageSize.width(factor);
        this.ph = PageSize.height(factor);
        this.bookWidth = pw * 2 + SPINE + BORDER * 2;
        this.bookHeight = ph + BORDER * 2;
        FoliosSession sized = FoliosLibrary.session(this.book.id(), pw, ph);
        if (sized != null) this.reader.useSession(sized);
        int needWidth = bookWidth + TAB * 2 + 8;
        int needHeight = bookHeight + BAR + BAR_GAP + 8;
        this.scale = factor > 1 ? drawn : bookScale(needWidth, needHeight);
        this.originX = (this.width - bookWidth * this.scale) / 2;
        this.originY = (this.height - (bookHeight + BAR + BAR_GAP) * this.scale) / 2 + (BAR + BAR_GAP) * this.scale;
        this.search = this.addRenderableWidget(this.bar.search(this.search));
    }

    private FoliosPalette palette() {
        return pagePalette(this.book);
    }

    public static FoliosPalette pagePalette(FoliosBook book) {
        int accent = 0xFF000000 | book.accent();
        if (ConfigStyle.mode() == ConfigTheme.Mode.DARK) {
            int bright = FoliosPalette.mix(accent, 0xFFFFFFFF, 0.25F);
            return book.colors().over(new FoliosPalette(0xFF1F222C, 0xFFD5D8E2, 0xFF878DA0, 0xFFFFFFFF, bright, FoliosPalette.mix(bright, 0xFFFFFFFF, 0.25F), 0xFF282C38, 0xFF393E4E), true);
        }
        return book.colors().over(new FoliosPalette(0xFFF8F4EB, 0xFF35312B, 0xFF8E877B, 0xFF1A1814, accent, FoliosPalette.mix(accent, 0xFF000000, 0.2F), 0xFFEEE7D9, 0xFFD8CEBA), false);
    }

    private float bookX(double mouseX) {
        return (float) ((mouseX - this.originX) / this.scale);
    }

    private float bookY(double mouseY) {
        return (float) ((mouseY - this.originY) / this.scale);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.popups.click(this.reader, event.x(), event.y())) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_4) {
            this.reader.back();
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (this.search != null && this.search.isFocused()) {
            this.search.setFocused(false);
            this.setFocused(null);
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        float x = this.bookX(event.x());
        float y = this.bookY(event.y());
        for (Hit hit : List.copyOf(this.hits)) {
            if (hit.contains(x, y)) {
                this.click();
                hit.action.run();
                return true;
            }
        }
        if (this.reader.flip == null) {
            if (this.press(this.leftHits, x - BORDER, y - BORDER)) return true;
            if (this.press(this.rightHits, x - BORDER - pw - SPINE, y - BORDER)) return true;
        }
        boolean onBook = x >= -2 && x < bookWidth + 36 && y >= -BAR - BAR_GAP - 2 && y < bookHeight + 2;
        if (!onBook && this.book.clickOutsideCloses()) {
            this.onClose();
            return true;
        }
        return false;
    }

    private boolean press(List<AbstractPainter.Hit> pageHits, float x, float y) {
        for (int index = pageHits.size() - 1; index >= 0; index--) {
            AbstractPainter.Hit hit = pageHits.get(index);
            if (hit.scroll() == null && hit.contains(x, y)) {
                if (hit.drag() != null) {
                    this.dragging = hit.drag();
                    AbstractPainter.hold(hit);
                    return true;
                }
                this.click();
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    private AbstractPainter.@Nullable Drag dragging;

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && this.popups.drag(dx, dy)) return true;
        if (this.dragging != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragging.moved((float) (dx / this.scale), (float) (dy / this.scale));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) this.popups.release();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragging = null;
            AbstractPainter.hold(null);
        }
        return super.mouseReleased(event);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.5F));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.popups.scroll(this.reader, x, y, scrollY)) return true;
        if (scrollY == 0) return true;
        float[] column = this.tabColumn;
        if (column != null && over(this.bookX(x), this.bookY(y), column[0], column[1], column[2], column[3])) {
            this.tabFirst += scrollY > 0 ? -this.tabShown : this.tabShown;
            return true;
        }
        if (this.reader.flip == null) {
            float bx = this.bookX(x);
            float by = this.bookY(y);
            FoliosPainter.Scroll scroll = AbstractPainter.Hit.scrollAt(this.leftHits, bx - BORDER, by - BORDER);
            if (scroll == null) scroll = AbstractPainter.Hit.scrollAt(this.rightHits, bx - BORDER - pw - SPINE, by - BORDER);
            if (scroll != null) {
                scroll.scrolled(scrollY);
                return true;
            }
        }
        this.reader.turn(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.bar.typing() && FoliosMentions.KEY.matches(event) && this.mentionHovered()) return true;
        if (event.key() == InputConstants.KEY_ESCAPE && this.popups.escape(this.reader)) return true;
        boolean typing = this.bar.typing();
        if (event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk() && this.search != null) {
            this.setFocused(this.search);
            this.search.setFocused(true);
            return true;
        }
        if (!typing) {
            switch (event.key()) {
                case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> {
                    this.reader.turn(-1);
                    return true;
                }
                case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> {
                    this.reader.turn(1);
                    return true;
                }
                case InputConstants.KEY_BACKSPACE -> {
                    this.reader.back();
                    return true;
                }
                case InputConstants.KEY_HOME -> {
                    this.reader.home();
                    return true;
                }
                default -> {}
            }
            if (this.minecraft.options.keyInventory.matches(event)) {
                this.onClose();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        this.reader.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float opening() {
        float t = Math.min(1, (net.ixdarklord.folios.internal.client.FoliosClock.millis() - this.openedAt) / OPEN_MILLIS);
        if (!StyleSettings.transitions()) t = 1;
        float u = 1 - t;
        return 1 - u * u * u;
    }

    @Override
    public void tick() {
        super.tick();
        this.reader.keepCurrent();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Identifier backdrop = this.book.textures().backdrop();
        if (backdrop != null) {
            layer(graphics, backdrop, 0, 0, this.width, this.height, FoliosPalette.alpha(0xFFFFFFFF, Math.round(255 * this.opening())));
            return;
        }
        int alpha = Math.round(0x90 * this.opening());
        graphics.fillGradient(0, 0, this.width, this.height / 2, alpha << 24, (alpha / 2) << 24);
        graphics.fillGradient(0, this.height / 2, this.width, this.height, (alpha / 2) << 24, alpha << 24);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.hits.clear();
        this.tooltip = null;
        this.hoveredPopup = null;
        float bx = this.bookX(mouseX);
        float by = this.bookY(mouseY);
        this.pointerX = bx;
        this.pointerY = by;
        graphics.pose().pushMatrix();
        graphics.pose().translate(this.originX, this.originY + (1 - this.opening()) * 12);
        graphics.pose().scale(this.scale, this.scale);
        this.reader.tabs.frame();
        List<AbstractPainter.Hit> barHits = new ArrayList<>();
        ScreenPainter barPainter = new ScreenPainter(graphics, 0, -BAR - BAR_GAP, 1, this.bar.palette(), this.reader.session, this.reader,
                this.reader.seconds(), bx, by + BAR + BAR_GAP, barHits);
        this.bar.draw(barPainter, bookWidth, 1);
        barPainter.end();
        for (AbstractPainter.Hit hit : barHits) this.hit(hit.x0(), hit.y0() - BAR - BAR_GAP, hit.x1(), hit.y1() - BAR - BAR_GAP, hit.action());
        if (barPainter.tooltipLines() != null) this.tooltip = barPainter.tooltipLines();
        this.drawBook(graphics, bx, by);
        this.drawTabs(graphics, bx, by);
        graphics.pose().popMatrix();
        super.extractRenderState(graphics, mouseX, mouseY, a);
        boolean popped = this.popups.draw(graphics, this.reader, this.palette(), this.hoveredPopup, mouseX, mouseY, this.scale, this.width, this.height);
        if (!popped && this.tooltip != null && !this.tooltip.isEmpty()) net.ixdarklord.folios.internal.client.FoliosTooltips.show(graphics, this.font, this.tooltip, mouseX, mouseY, this.book, -1, this.scale);
    }

    private float pointerX = Float.NaN, pointerY = Float.NaN;

    private boolean mentionHovered() {
        if (this.reader.flip != null || Float.isNaN(pointerX) || pointerY < BORDER || pointerY >= BORDER + ph) return false;
        if (pointerX >= BORDER && pointerX < BORDER + pw) return this.reader.mention(false);
        if (pointerX >= BORDER + pw + SPINE && pointerX < BORDER + pw * 2 + SPINE) return this.reader.mention(true);
        return false;
    }

    private void hit(float x0, float y0, float x1, float y1, Runnable action) {
        this.hits.add(new Hit(x0, y0, x1, y1, action));
    }

    private static boolean over(float x, float y, float x0, float y0, float x1, float y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    private static void layer(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width, int height, int tint) {
        int[] size = ConfigStyle.textureSize(texture);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, size[0], size[1], size[0], size[1], tint);
    }

    private static void rounded(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0 + 2, y0, x1 - 2, y1, color);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, color);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, color);
        graphics.fill(x0, y0 + 2, x0 + 1, y1 - 2, color);
        graphics.fill(x1 - 1, y0 + 2, x1, y1 - 2, color);
    }

    private void drawBook(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        FoliosPalette palette = this.palette();
        int cover = 0xFF000000 | this.book.cover();
        for (int step = 1; step <= 4; step++) {
            rounded(graphics, -step + 2, step + 1, bookWidth + step - 2, bookHeight + step + 2, 0x18000000);
        }
        rounded(graphics, 0, 0, bookWidth, bookHeight, cover);
        FoliosBook.Textures textures = this.book.textures();
        layer(graphics, textures.cover(), 1, 1, bookWidth - 2, bookHeight - 2, cover);
        graphics.fill(2, 1, bookWidth - 2, 2, FoliosPalette.mix(cover, 0xFFFFFFFF, 0.18F));
        graphics.fill(2, bookHeight - 2, bookWidth - 2, bookHeight - 1, FoliosPalette.mix(cover, 0xFF000000, 0.3F));
        int gilt = FoliosPalette.alpha(FoliosPalette.mix(palette.accent(), 0xFFFFFFFF, 0.2F), 0x90);
        graphics.fill(3, 3, bookWidth - 3, 4, gilt);
        graphics.fill(3, bookHeight - 4, bookWidth - 3, bookHeight - 3, gilt);
        int leftX = BORDER;
        int rightX = BORDER + pw + SPINE;
        int top = BORDER;
        int paper = palette.paper();
        int spine = leftX + pw + SPINE / 2;
        graphics.fill(leftX, top, rightX + pw, top + ph, paper);
        layer(graphics, textures.page(), leftX, top, pw + SPINE / 2, ph, paper);
        layer(graphics, textures.page(), spine, top, pw + SPINE / 2, ph, paper);
        float turned = this.reader.flipProgress();
        FoliosReader.Flip flip = this.reader.flip;
        FoliosReader.Spot spot = this.reader.spot;
        float angle = flip == null ? 0 : ease(flip.forward() ? turned : 1 - turned) * Mth.PI;
        FoliosPageElement.Laid left = flip == null ? this.reader.page(spot, 0) : flip.forward() ? flip.oldLeft() : flip.newLeft();
        FoliosPageElement.Laid right = flip == null ? this.reader.page(spot, 1) : flip.forward() ? flip.newRight() : flip.oldRight();
        int leftFold = foldStrength(left);
        int rightFold = foldStrength(right);
        for (int step = 0; step < 8; step++) {
            float fade = 1 - step / 8.0F;
            graphics.fill(spine - step - 1, top, spine - step, top + ph, Math.round(leftFold * fade) << 24);
            graphics.fill(spine + step, top, spine + step + 1, top + ph, Math.round(rightFold * fade) << 24);
        }
        ScreenPainter edges = new ScreenPainter(graphics, 0, 0, 1, palette, this.reader.session, this.reader, this.reader.seconds(), Float.NaN, Float.NaN, this.unpressable);
        Shapes.foreEdge(edges, leftX, top + 1, top + ph - 1, false);
        Shapes.foreEdge(edges, rightX + pw, top + 1, top + ph - 1, true);
        Shapes.wornEdges(edges, leftX, top, leftX + pw, top + ph, false, cover);
        Shapes.wornEdges(edges, rightX, top, rightX + pw, top + ph, true, cover);
        Shapes.deckle(edges, leftX, rightX + pw, top, false, cover);
        Shapes.deckle(edges, leftX, rightX + pw, top + ph - 1, true, cover);
        edges.end();

        boolean settled = flip == null;
        this.leftHits.clear();
        this.rightHits.clear();
        this.page(graphics, palette, left, leftX, top, mouseX, mouseY, settled, this.leftHits, settled ? spot : flip.staticLeft(), false);
        this.page(graphics, palette, right, rightX, top, mouseX, mouseY, settled, this.rightHits, settled ? spot : flip.staticRight(), true);
        if (!settled) {
            graphics.nextStratum();
            this.leaf(graphics, palette, flip, angle, spine, top);
        }
    }

    private static int foldStrength(FoliosPageElement.@Nullable Laid page) {
        int fold = ConfigStyle.mode() == ConfigTheme.Mode.DARK ? 0x70 : 0x2C;
        return page instanceof FoliosPageElement.SpreadHalf ? Math.round(fold * SPREAD_FOLD) : fold;
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    private void page(GuiGraphicsExtractor graphics, FoliosPalette palette, FoliosPageElement.@Nullable Laid page, int x, int y, float mouseX, float mouseY,
                      boolean pressable, List<AbstractPainter.Hit> pageHits, FoliosReader.Spot spot, boolean right) {
        float pointerX = mouseX - x;
        float pointerY = mouseY - y;
        boolean on = pressable && pointerX >= 0 && pointerX < pw && pointerY >= 0 && pointerY < ph;
        this.unpressable.clear();
        ScreenPainter painter = new ScreenPainter(graphics, x, y, 1, palette, this.reader.session, this.reader, this.reader.seconds(),
                on ? pointerX : Float.NaN, on ? pointerY : Float.NaN, pressable ? pageHits : this.unpressable);
        try {
            Shapes.pageFrame(painter, this.book.textures().frame(), pw, ph, page, right);
            if (page != null) this.reader.drawRevealed(painter, page, spot, right);
            this.reader.chrome(painter, spot, right, pressable, null);
        } finally {
            painter.end();
        }
        if (painter.tooltipLines() != null) this.tooltip = painter.tooltipLines();
        if (painter.popup() != null) this.hoveredPopup = painter.popup();
    }

    private void leaf(GuiGraphicsExtractor graphics, FoliosPalette palette, FoliosReader.Flip flip, float angle, int spine, int top) {
        float upright = Mth.sin(angle);
        boolean faceShown = angle < Mth.HALF_PI;
        FoliosPageElement.Laid shown = faceShown ? (flip.forward() ? flip.oldRight() : flip.newRight()) : (flip.forward() ? flip.newLeft() : flip.oldLeft());
        int shadow = Math.round(pw * 0.45F * upright);
        for (int step = 0; step < 6 && shadow > 0; step++) {
            int alpha = Math.round(0x40 * upright * (1 - step / 6.0F));
            int from = Math.round(shadow * step / 6.0F);
            int to = Math.round(shadow * (step + 1) / 6.0F);
            if (faceShown) graphics.fill(spine - to, top, spine - from, top + ph, alpha << 24);
            else graphics.fill(spine + from, top, spine + to, top + ph, alpha << 24);
        }
        float length = pw + SPINE / 2.0F;
        boolean bend = this.book.pageTurn() == FoliosBook.PageTurn.BEND;
        float curl = bend ? (flip.forward() ? -1 : 1) * LEAF_BEND * upright : 0;
        float[] edges = new float[LEAF_STRIPS + 1];
        float[] turns = new float[LEAF_STRIPS];
        edges[0] = spine;
        for (int strip = 0; strip < LEAF_STRIPS; strip++) {
            float along = (strip + 0.5F) / LEAF_STRIPS;
            turns[strip] = angle + curl * along * along;
            edges[strip + 1] = edges[strip] + Mth.cos(turns[strip]) * length / LEAF_STRIPS;
        }
        FoliosReader.Spot spot = faceShown ? flip.face() : flip.back();
        int fold = foldStrength(shown);
        for (int strip = 0; strip < LEAF_STRIPS; strip++) {
            float s0 = strip * length / LEAF_STRIPS;
            float s1 = (strip + 1) * length / LEAF_STRIPS;
            float u0 = faceShown ? s0 : length - s1;
            float u1 = faceShown ? s1 : length - s0;
            float x0 = faceShown ? edges[strip] : edges[strip + 1];
            float x1 = faceShown ? edges[strip + 1] : edges[strip];
            float stretch = (x1 - x0) / (u1 - u0);
            if (stretch <= 0.01F) continue;
            int clipFrom = Math.round(Math.min(x0, x1));
            int clipTo = Math.max(clipFrom + 1, Math.round(Math.max(x0, x1)));
            if (strip + 1 < LEAF_STRIPS) {
                if (edges[strip + 1] >= edges[strip]) clipTo++;
                else clipFrom--;
            }
            graphics.enableScissor(clipFrom, top, clipTo, top + ph);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x0, top);
            graphics.pose().scale(stretch, 1);
            graphics.pose().translate(-u0, 0);
            this.leafPage(graphics, palette, shown, spot, faceShown, fold, stretch > 0.15F);
            graphics.fill(0, 0, Math.round(length), ph, Math.round(0x60 * (1 - Math.abs(Mth.cos(turns[strip])))) << 24);
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }
        float outer = edges[LEAF_STRIPS];
        int edgeX = Math.round(outer) - (outer > spine ? 1 : 0);
        graphics.fill(edgeX, top, edgeX + 1, top + ph, FoliosPalette.mix(palette.paper(), palette.line(), 0.8F));
    }

    private void leafPage(GuiGraphicsExtractor graphics, FoliosPalette palette, FoliosPageElement.@Nullable Laid shown, FoliosReader.Spot spot,
                          boolean faceShown, int fold, boolean content) {
        graphics.fill(0, 0, pw + SPINE / 2, ph, palette.paper());
        layer(graphics, this.book.textures().page(), 0, 0, pw + SPINE / 2, ph, palette.paper());
        for (int step = 0; step < 8; step++) {
            int color = Math.round(fold * (1 - step / 8.0F)) << 24;
            int at = faceShown ? step : pw + SPINE / 2 - step - 1;
            graphics.fill(at, 0, at + 1, ph, color);
        }
        if (!content) return;
        this.unpressable.clear();
        ScreenPainter painter = new ScreenPainter(graphics, faceShown ? SPINE / 2.0F : 0, 0, 1, palette, this.reader.session, this.reader,
                this.reader.seconds(), Float.NaN, Float.NaN, this.unpressable);
        try {
            Shapes.pageFrame(painter, this.book.textures().frame(), pw, ph, shown, faceShown);
            if (shown != null) this.reader.drawRevealed(painter, shown, spot, faceShown);
            this.reader.chrome(painter, spot, faceShown, false, null);
        } finally {
            painter.end();
        }
    }

    private static final int LEAF_STRIPS = 18;
    private static final float LEAF_BEND = 1.0F;

    private static final int TAB_SMALL = 18;

    private record TabSpot(boolean here, Component name, Runnable action, @Nullable FoliosCategory category, String part, int order) {}

    private void drawTabs(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        FoliosPalette palette = this.palette();
        int x = bookWidth - 2 + this.book.layout().tabX();
        int gap = this.book.layout().tabSpacing();
        String chapter = this.reader.spot.chapter();
        FoliosCategory open = this.reader.category();
        int foot = bookHeight - 6;
        int order = 99;
        foot = this.footTab(graphics, mouseX, mouseY, x, foot, palette, new TabSpot("index".equals(chapter),
                Component.translatableWithFallback("folios.index", "Index"), this.reader::index, null, "index", order--)) - gap;
        if (!this.reader.memory.bookmarks().isEmpty()) {
            foot = this.footTab(graphics, mouseX, mouseY, x, foot, palette, new TabSpot("bookmarks".equals(chapter),
                    Component.translatableWithFallback("folios.bookmarks", "Bookmarks"), this.reader::bookmarks, null, "bookmarks", order--)) - gap;
        }
        int y = BORDER + this.book.layout().tabY();
        int top = y + TAB + gap;
        order = 0;
        y = this.tab(graphics, mouseX, mouseY, x, y, palette, new TabSpot("home".equals(chapter),
                Component.translatableWithFallback("folios.introduction", "Introduction"), this.reader::home, null, "home", order++)) + gap;
        List<FoliosCategory> categories = this.book.visibleCategories().stream().filter(FoliosCategory::isOpen).toList();
        int room = foot - top;
        boolean fits = categories.size() * (TAB_SMALL + gap) + TAB - TAB_SMALL <= room;
        int shown = fits ? categories.size() : Math.max(1, (room - TAB_SMALL - PAGER_LABEL - (TAB - TAB_SMALL)) / (TAB_SMALL + gap));
        int pages = (categories.size() + shown - 1) / shown;
        int last = (pages - 1) * shown;
        this.tabShown = shown;
        String openId = open != null ? open.id() : null;
        if (openId != null && !openId.equals(this.tabFollowed)) {
            int at = categories.indexOf(open);
            if (at >= 0 && (at < this.tabFirst || at >= this.tabFirst + shown)) this.tabFirst = at;
        }
        this.tabFollowed = openId;
        this.tabFirst = Mth.clamp(this.tabFirst, 0, Math.max(0, last)) / shown * shown;
        this.tabColumn = fits ? null : new float[]{x, y, x + 38, foot};
        float phase = this.tabSwapPhase();
        boolean leaving = phase < 0.5F;
        int first = Mth.clamp(this.tabShownFirst, 0, Math.max(0, last));
        List<FoliosCategory> window = categories.subList(first, Math.min(categories.size(), first + shown));
        float step = Math.min(TAB_STAGGER, 0.4F / Math.max(1, window.size() - 1));
        float half = leaving ? phase * 2 : (phase - 0.5F) * 2;
        for (int index = 0; index < window.size(); index++) {
            FoliosCategory category = window.get(index);
            float t = Mth.clamp((half - index * step) / (1 - (window.size() - 1) * step), 0, 1);
            float slide = phase >= 1 ? 1 : leaving ? 1 - t * t * (3 - 2 * t) : 1 - (1 - t) * (1 - t) * (1 - t);
            y = this.tab(graphics, mouseX, mouseY, x, y, palette, new TabSpot(open != null && open.id().equals(category.id()), category.title(),
                    () -> this.reader.category(category.id()), category, "category", order++), slide) + gap;
        }
        if (!fits) {
            this.tabPager(graphics, mouseX, mouseY, x, foot - TAB_SMALL, this.tabFirst > 0, this.tabFirst < last, this.tabFirst / shown + 1, pages, palette);
        }
    }

    private static final int PAGER_PLATE = 12;
    private static final int PAGER_LABEL = 10;
    private static final float TAB_SWAP_MILLIS = 380;
    private static final float TAB_STAGGER = 0.08F;
    private int tabFirst;
    private int tabShown = 1;
    private int tabShownFirst = -1;
    private long tabSwapAt = -1;
    private @Nullable String tabFollowed;
    private float @Nullable [] tabColumn;

    private float tabSwapPhase() {
        long now = net.ixdarklord.folios.internal.client.FoliosClock.millis();
        if (this.tabShownFirst < 0 || !StyleSettings.transitions()) {
            this.tabShownFirst = this.tabFirst;
            this.tabSwapAt = -1;
            return 1;
        }
        if (this.tabSwapAt < 0) {
            if (this.tabShownFirst == this.tabFirst) return 1;
            this.tabSwapAt = now;
            this.tabSwapped = false;
        }
        float phase = (now - this.tabSwapAt) / TAB_SWAP_MILLIS;
        if (!this.tabSwapped && phase >= 0.5F) {
            this.tabShownFirst = this.tabFirst;
            this.tabSwapped = true;
        } else if (this.tabSwapped && this.tabShownFirst != this.tabFirst && phase < 1) {
            this.tabSwapAt = now - Math.round((1 - phase) * TAB_SWAP_MILLIS);
            this.tabSwapped = false;
            phase = 1 - phase;
        }
        if (phase >= 1) {
            this.tabSwapAt = -1;
            if (this.tabShownFirst == this.tabFirst) return 1;
            this.tabSwapAt = now;
            this.tabSwapped = false;
            return 0;
        }
        return phase;
    }

    private boolean tabSwapped;

    private void tabPager(GuiGraphicsExtractor graphics, float mouseX, float mouseY, int x, int y, boolean earlier, boolean later, int page, int pages, FoliosPalette palette) {
        int reach = 2 * PAGER_PLATE + 10;
        float moved = this.reader.tabs.out("pager", false, true);
        float pressed = this.reader.tabs.pressed("pager");
        int push = Math.round(4 * pressed + 4 * (1 - moved));
        int firstX = x + 1 - push;
        int secondX = firstX + PAGER_PLATE + 2;
        int depth = Shapes.tabDepth(6, moved);
        int edge = bookWidth - BORDER;
        int out = Math.round(reach + 4) - depth - push;
        int color = FoliosPalette.mix(this.reader.tabColor(null, palette, moved), 0xFF000000, 0.12F + 0.15F * pressed + 0.06F * depth);
        int root = Shapes.ribbonRoot(edge, depth);
        layer(graphics, this.book.textures().tab(), root, y, x - root + out, TAB_SMALL, color);
        ScreenPainter tuck = new ScreenPainter(graphics, 0, 0, 1, palette, this.reader.session, this.reader, this.reader.seconds(), Float.NaN, Float.NaN, this.unpressable);
        Shapes.tuck(tuck, edge, y, TAB_SMALL, depth, color);
        tuck.end();
        int plateY = y + (TAB_SMALL - PAGER_PLATE) / 2;
        this.pagerPlate(graphics, mouseX, mouseY, firstX, plateY, false, earlier, palette);
        this.pagerPlate(graphics, mouseX, mouseY, secondX, plateY, true, later, palette);
        String label = page + "/" + pages;
        int middle = firstX + push + PAGER_PLATE + 1;
        graphics.text(this.font, label, middle - this.font.width(label) / 2, y - PAGER_LABEL + 1, 0xFFF2EEE6, true);
    }

    private void pagerPlate(GuiGraphicsExtractor graphics, float mouseX, float mouseY, int x, int y, boolean next, boolean enabled, FoliosPalette palette) {
        boolean hovered = enabled && over(mouseX, mouseY, x - 1, y - 1, x + PAGER_PLATE + 1, y + PAGER_PLATE + 1);
        int plate = palette.paper() | 0xFF000000;
        if (hovered) plate = FoliosPalette.mix(plate, palette.accent() | 0xFF000000, 0.2F);
        if (!enabled) plate = FoliosPalette.alpha(plate, 0x80);
        layer(graphics, this.book.textures().tabPlate(), x, y, PAGER_PLATE, PAGER_PLATE, plate);
        net.ixdarklord.folios.internal.client.paint.PixelIcon arrow = (next ? Shapes.Icon.NEXT : Shapes.Icon.PREVIOUS).image();
        int ink = !enabled ? FoliosPalette.alpha(palette.muted(), 0x70)
                : hovered ? palette.accent() | 0xFF000000 : FoliosPalette.mix(palette.muted(), palette.text(), 0.35F);
        arrow.draw(graphics, x + (PAGER_PLATE - arrow.width()) / 2, y + (PAGER_PLATE - arrow.height()) / 2, ink);
        if (!enabled) return;
        if (hovered) this.tooltip = List.of(next ? Component.translatableWithFallback("folios.tabs.next", "Next")
                : Component.translatableWithFallback("folios.tabs.previous", "Back"));
        this.hit(x - 1, y - 1, x + PAGER_PLATE + 1, y + PAGER_PLATE + 1, () -> this.scrollTabs(next ? this.tabShown : -this.tabShown));
    }

    private void scrollTabs(int by) {
        this.tabFirst += by;
        this.reader.tabs.press("pager");
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.5F));
    }

    private static final float HOVER_OUT = 0.55F;

    private int footTab(GuiGraphicsExtractor graphics, float mouseX, float mouseY, int x, int bottom, FoliosPalette palette, TabSpot spot) {
        boolean hovered = over(mouseX, mouseY, x, bottom - TAB_SMALL, x + 38, bottom);
        float moved = this.reader.tabs.out(FoliosReader.tabKey(spot.category, spot.part), hovered, spot.here);
        int tall = tabTall(moved);
        this.tab(graphics, x, bottom - tall, tall, spot, palette, moved, hovered, 1);
        return bottom - tall;
    }

    private int tab(GuiGraphicsExtractor graphics, float mouseX, float mouseY, int x, int top, FoliosPalette palette, TabSpot spot) {
        return this.tab(graphics, mouseX, mouseY, x, top, palette, spot, 1);
    }

    private int tab(GuiGraphicsExtractor graphics, float mouseX, float mouseY, int x, int top, FoliosPalette palette, TabSpot spot, float slide) {
        boolean hovered = slide >= 1 && over(mouseX, mouseY, x, top, x + 38, top + TAB_SMALL);
        float moved = this.reader.tabs.out(FoliosReader.tabKey(spot.category, spot.part), hovered, spot.here);
        int tall = tabTall(moved);
        this.tab(graphics, x, top, tall, spot, palette, moved, hovered, slide);
        return top + tall;
    }

    private static float tabGrow(float moved) {
        return Mth.clamp((moved - HOVER_OUT) / (1 - HOVER_OUT), 0, 1);
    }

    private static int tabTall(float moved) {
        return Math.round(Mth.lerp(tabGrow(moved), TAB_SMALL, TAB));
    }

    private @Nullable Component tabDetail(@Nullable FoliosCategory category, String part) {
        return this.reader.tabDetail(category, part);
    }

    private void tab(GuiGraphicsExtractor graphics, int x, int y, int tall, TabSpot spot, FoliosPalette palette, float moved, boolean hovered, float slide) {
        String key = FoliosReader.tabKey(spot.category, spot.part);
        float pressed = this.reader.tabs.pressed(key);
        float grow = tabGrow(moved);
        int depth = Shapes.tabDepth(Math.min(spot.order, 6), moved);
        int edge = bookWidth - BORDER;
        int full = Math.round(24 + 15 * moved - 4 * pressed) - depth;
        int out = slide >= 1 ? full : Math.round(Mth.lerp(slide, edge - x, full));
        if (slide <= 0) return;
        int color = FoliosPalette.mix(this.reader.tabColor(spot.category, palette, moved), 0xFF000000, 0.15F * pressed + 0.06F * depth);
        int root = Shapes.ribbonRoot(edge, depth);
        layer(graphics, this.book.textures().tab(), root, y, x - root + out, tall, color);
        ScreenPainter tuck = new ScreenPainter(graphics, 0, 0, 1, palette, this.reader.session, this.reader, this.reader.seconds(), Float.NaN, Float.NaN, this.unpressable);
        Shapes.tuck(tuck, edge, y, tall, depth, color);
        tuck.end();
        int plate = Math.round(Mth.lerp(grow, 14, 18));
        int icon = plate - 2;
        int plateX = Math.round(x + 3 + (full * 0.8F - 3 - plate) / 2.0F) - (full - out);
        int plateY = y + (tall - plate) / 2;
        if (slide < 1) graphics.enableScissor(edge, y - 2, x + out + 2, y + tall + 2);
        layer(graphics, this.book.textures().tabPlate(), plateX, plateY, plate, plate, palette.paper() | 0xFF000000);
        if (spot.category != null) spot.category.icon().draw(graphics, plateX + 1, plateY + 1, icon, this.reader.seconds());
        else if (spot.part.equals("bookmarks")) FoliosBar.pixels(graphics, FoliosBar.STAR, plateX + 1, plateY + 1, icon, palette.accent());
        else if (spot.part.equals("index")) FoliosBar.pixels(graphics, FoliosBar.INDEX, plateX + 1, plateY + 1, icon, palette.accent());
        else this.book.icon().draw(graphics, plateX + 1, plateY + 1, icon, this.reader.seconds());
        if (slide < 1) {
            graphics.disableScissor();
            return;
        }
        if (hovered) this.tooltip = net.ixdarklord.folios.internal.client.FoliosTooltips.of(spot.name, this.tabDetail(spot.category, spot.part));
        this.hit(x, y, x + 38, y + tall, () -> {
            if (!spot.here) this.reader.tabs.press(key);
            spot.action.run();
        });
    }
}
