package net.ixdarklord.folios.internal.client.handheld;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.ixdarklord.folios.internal.client.screen.FoliosBar;
import net.ixdarklord.folios.internal.mixin.client.MouseHandlerAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class HandheldScreen extends Screen {
    private final net.ixdarklord.folios.internal.client.screen.PopupLayer popups = new net.ixdarklord.folios.internal.client.screen.PopupLayer();
    private final HandheldBook book;
    private final FoliosBar bar;
    private @Nullable StyledEditBox search;

    HandheldScreen(HandheldBook book) {
        super(book.book.title());
        this.book = book;
        this.bar = new FoliosBar(book, true, book::shut, field -> {
            this.setFocused(field);
            field.setFocused(true);
        });
        book.bar = this.bar;
    }

    @Override
    protected void init() {
        hintY = Integer.MIN_VALUE;
        float factor = net.ixdarklord.folios.internal.client.PageSize.handheldFactor();
        net.ixdarklord.folios.internal.client.FoliosSession sized = net.ixdarklord.folios.internal.client.FoliosLibrary.session(book.book.id(),
                net.ixdarklord.folios.internal.client.PageSize.width(factor), net.ixdarklord.folios.internal.client.PageSize.height(factor));
        if (sized != null) book.useSession(sized);
        this.search = this.addRenderableWidget(this.bar.search(this.search));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.search != null && this.search.isFocused() && !book.isOpen()) {
            this.search.setFocused(false);
            this.setFocused(null);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        float[] none = {Float.NaN, Float.NaN};
        book.leftPointer = book.placed ? this.pointer(book.leftClip, mouseX, mouseY) : none;
        book.rightPointer = book.placed ? this.pointer(book.rightClip, mouseX, mouseY) : none.clone();
        book.barPointer = book.barPlaced ? this.pointer(book.barClip, mouseX, mouseY) : none.clone();
        if (outside()) return;
        List<Component> tooltip = book.tooltip;
        int rule = -1;
        if (book.isShut() && book.placed && onCover(book.rightPointer)) {
            List<Component> lines = new java.util.ArrayList<>();
            for (Component said : HandheldRenderer.plateText(book, book.rightPointer)) {
                for (net.minecraft.network.chat.FormattedText row : this.font.getSplitter().splitLines(said, PLATE_WIDTH, net.minecraft.network.chat.Style.EMPTY)) {
                    lines.add(Component.literal(row.getString()));
                }
            }
            if (!lines.isEmpty()) rule = lines.size();
            lines.add(Component.translatableWithFallback("folios.open", "Click to open"));
            tooltip = lines;
        }
        boolean popped = this.popups.draw(graphics, book, HandheldRenderer.palette(book.book), book.popup, mouseX, mouseY, 1, this.width, this.height);
        if (!popped && tooltip != null && !tooltip.isEmpty()) net.ixdarklord.folios.internal.client.FoliosTooltips.show(graphics, this.font, tooltip, mouseX, mouseY, book.book, rule);
        this.lookHint(graphics);
    }

    private static final int PLATE_WIDTH = 180;

    private static final net.minecraft.resources.Identifier RIGHT_CLICK = net.minecraft.resources.Identifier.withDefaultNamespace("toast/right_click");

    private static final long HINT_MILLIS = 6000;
    private static final long HINT_FADE = 600;
    private static boolean lookedAbout;
    private long openedAt;
    private int hintY = Integer.MIN_VALUE;

    private void lookHint(GuiGraphicsExtractor graphics) {
        if (!book.isOpen()) {
            openedAt = 0;
            hintY = Integer.MIN_VALUE;
            return;
        }
        if (openedAt == 0) openedAt = net.ixdarklord.folios.internal.client.FoliosClock.millis();
        if (lookedAbout || looking || !book.placed || !book.book.handling().lookAround()) return;
        long shown = net.ixdarklord.folios.internal.client.FoliosClock.millis() - openedAt;
        if (shown > HINT_MILLIS + HINT_FADE) return;
        float alpha = shown <= HINT_MILLIS ? 1 : 1 - (shown - HINT_MILLIS) / (float) HINT_FADE;
        int fade = Math.round(alpha * 255);
        if (fade < 8) return;
        Component hint = Component.translatableWithFallback("folios.hint.look", "Hold right click to look around");
        int wide = 16 + 4 + this.font.width(hint);
        int tall = 16;
        float[] top = this.onScreen(book.rightClip, 0, -(net.ixdarklord.folios.internal.client.screen.FoliosScreen.BORDER + 5 + FoliosBar.HEIGHT));
        if (top == null) return;
        int x = (this.width - wide) / 2;
        if (hintY == Integer.MIN_VALUE) hintY = Math.max(14, Math.round(top[1]) - tall - 12);
        int y = hintY;
        graphics.nextStratum();
        net.ixdarklord.folios.internal.client.FoliosTooltips.frame(graphics, x, y, wide, tall, book.book, fade);
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, RIGHT_CLICK, x - 2, y - 2, 20, 20, alpha);
        int ink = net.ixdarklord.folios.internal.client.FoliosTooltips.titleColor(book.book);
        graphics.text(this.font, hint, x + 20, y + 4, (fade << 24) | (ink & 0xFFFFFF), true);
    }

    private float @Nullable [] onScreen(org.joml.Matrix4f clip, float x, float y) {
        org.joml.Vector4f at = clip.transform(new org.joml.Vector4f(x, y, 0, 1));
        if (at.w <= 0) return null;
        return new float[]{(at.x / at.w + 1) / 2 * this.width, (1 - at.y / at.w) / 2 * this.height};
    }

    private float[] pointer(org.joml.Matrix4f clip, int mouseX, int mouseY) {
        float[] at = HandheldRenderer.onPage(clip, mouseX, mouseY, this.width, this.height);
        return at != null ? at : new float[]{Float.NaN, Float.NaN};
    }

    @Override
    public void tick() {
        if (HandheldBook.current() != book) this.minecraft.gui.setScreen(null);
        else book.keepCurrent();
    }

    private boolean looking;
    private double lookFromX;
    private double lookFromY;

    private boolean outside() {
        return !this.minecraft.options.getCameraType().isFirstPerson();
    }

    private void turn(double dx, double dy) {
        double scale = this.minecraft.getWindow().getGuiScale();
        double sensitivity = this.minecraft.options.sensitivity().get() * 0.6 + 0.2;
        double speed = sensitivity * sensitivity * sensitivity * 8.0;
        if (this.minecraft.player != null) {
            this.minecraft.player.turn((this.minecraft.options.invertMouseX().get() ? -dx : dx) * scale * speed,
                    (this.minecraft.options.invertMouseY().get() ? -dy : dy) * scale * speed);
        }
    }

    private double lastX = Double.NaN;
    private double lastY = Double.NaN;

    @Override
    public void mouseMoved(double x, double y) {
        if (outside() && book.book.handling().lookAround()) {
            if (!Double.isNaN(lastX)) this.turn(x - lastX, y - lastY);
            lastX = x;
            lastY = y;
            return;
        }
        lastX = Double.NaN;
        super.mouseMoved(x, y);
    }

    private void look(boolean on, double x, double y) {
        if (looking == on) return;
        looking = on;
        com.mojang.blaze3d.platform.Window window = this.minecraft.getWindow();
        if (on) {
            lookFromX = x * window.getGuiScale();
            lookFromY = y * window.getGuiScale();
            ((MouseHandlerAccessor) this.minecraft.mouseHandler).folios$setMouseGrabbed(true);
            InputConstants.grabMouse(window, lookFromX, lookFromY);
        } else {
            ((MouseHandlerAccessor) this.minecraft.mouseHandler).folios$setMouseGrabbed(false);
            InputConstants.releaseMouse(window, lookFromX, lookFromY);
        }
    }

    private boolean walks(KeyEvent event) {
        if (!book.book.handling().walk()) return false;
        var options = this.minecraft.options;
        for (net.minecraft.client.KeyMapping key : new net.minecraft.client.KeyMapping[]{options.keyUp, options.keyDown, options.keyLeft,
                options.keyRight, options.keyJump, options.keyShift, options.keySprint}) {
            if (key.matches(event)) return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (this.walks(event)) net.minecraft.client.KeyMapping.set(com.mojang.blaze3d.platform.InputConstants.getKey(event), false);
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.popups.click(book, event.x(), event.y())) return true;
        if (outside()) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && book.book.handling().lookAround() && !this.bar.typing()) {
            lookedAbout = true;
            this.look(true, event.x(), event.y());
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (this.search != null && this.search.isFocused()) {
            this.search.setFocused(false);
            this.setFocused(null);
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && book.barPlaced && this.press(book.barHits, book.barPointer, book.barClip)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_4) {
            book.turn(-1);
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (book.isShut()) {
            if (!book.placed) return true;
            if (onCover(book.rightPointer)) book.openCover();
            else if (book.book.clickOutsideCloses()) this.onClose();
            return true;
        }
        if (!book.isOpen()) return false;
        if (this.press(book.rightHits, book.rightPointer, book.rightClip) || this.press(book.leftHits, book.leftPointer, book.leftClip)) return true;
        float[] right = book.rightPointer;
        float[] left = book.leftPointer;
        if (inside(right, 0, 300) || inside(left, 0, 300)) {
            if (inside(right, net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_WIDTH * 0.6F, 400)) book.turn(1);
            else if (inside(left, 0, net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_WIDTH * 0.4F)) book.turn(-1);
            return true;
        }
        if (book.book.clickOutsideCloses()) this.onClose();
        return true;
    }

    private static boolean onCover(float[] at) {
        float margin = 6;
        return !Float.isNaN(at[0]) && at[0] >= -margin && at[0] < net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_WIDTH + margin
                && at[1] >= -margin && at[1] < net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_HEIGHT + margin;
    }

    private static boolean inside(float[] at, float x0, float x1) {
        return !Float.isNaN(at[0]) && at[0] >= x0 && at[0] < Math.min(x1, net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_WIDTH)
                && at[1] >= 0 && at[1] < net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_HEIGHT;
    }

    private AbstractPainter.@Nullable Drag dragging;
    private org.joml.@Nullable Matrix4f draggedOn;

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && this.popups.drag(dx, dy)) return true;
        if (looking && event.button() == InputConstants.MOUSE_BUTTON_RIGHT && !outside()) {
            return true;
        }
        if (dragging != null && draggedOn != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            float[] now = HandheldRenderer.onPage(draggedOn, event.x(), event.y(), this.width, this.height);
            float[] before = HandheldRenderer.onPage(draggedOn, event.x() - dx, event.y() - dy, this.width, this.height);
            if (now != null && before != null) dragging.moved(now[0] - before[0], now[1] - before[1]);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) this.popups.release();
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && looking) {
            this.look(false, 0, 0);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = null;
            draggedOn = null;
            AbstractPainter.hold(null);
        }
        return super.mouseReleased(event);
    }

    private boolean press(List<AbstractPainter.Hit> hits, float[] at, org.joml.Matrix4f clip) {
        if (Float.isNaN(at[0])) return false;
        for (int index = hits.size() - 1; index >= 0; index--) {
            AbstractPainter.Hit hit = hits.get(index);
            if (hit.scroll() == null && hit.contains(at[0], at[1])) {
                if (hit.drag() != null) {
                    dragging = hit.drag();
                    AbstractPainter.hold(hit);
                    draggedOn = new org.joml.Matrix4f(clip);
                    return true;
                }
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.5F));
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.popups.scroll(book, x, y, scrollY)) return true;
        if (scrollY == 0) return true;
        if (book.isOpen()) {
            net.ixdarklord.folios.api.client.page.FoliosPainter.Scroll scroll = null;
            if (!Float.isNaN(book.rightPointer[0])) scroll = AbstractPainter.Hit.scrollAt(book.rightHits, book.rightPointer[0], book.rightPointer[1]);
            if (scroll == null && !Float.isNaN(book.leftPointer[0])) scroll = AbstractPainter.Hit.scrollAt(book.leftHits, book.leftPointer[0], book.leftPointer[1]);
            if (scroll != null) {
                scroll.scrolled(scrollY);
                return true;
            }
        }
        book.turn(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.bar.typing() && book.isOpen() && net.ixdarklord.folios.internal.client.FoliosMentions.KEY.matches(event)
                && (!Float.isNaN(book.rightPointer[0]) ? book.mention(true) : !Float.isNaN(book.leftPointer[0]) && book.mention(false))) return true;
        if (event.key() == InputConstants.KEY_ESCAPE && this.popups.escape(book)) return true;
        if (this.minecraft.options.keyTogglePerspective.matches(event) && !this.bar.typing()) {
            var options = this.minecraft.options;
            options.setCameraType(options.getCameraType().cycle());
            boolean free = outside() && book.book.handling().lookAround();
            this.look(free, this.width / 2.0, this.height / 2.0);
            lastX = Double.NaN;
            return true;
        }
        if (event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk() && this.search != null && book.isOpen()) {
            this.setFocused(this.search);
            this.search.setFocused(true);
            return true;
        }
        if (this.bar.typing()) return super.keyPressed(event);
        boolean walking = this.walks(event);
        if (book.isShut()) {
            switch (event.key()) {
                case InputConstants.KEY_RIGHT, InputConstants.KEY_RETURN, InputConstants.KEY_SPACE, InputConstants.KEY_D -> {
                    if (walking) break;
                    book.openCover();
                    return true;
                }
                default -> {}
            }
        }
        if (walking) {
            net.minecraft.client.KeyMapping.set(com.mojang.blaze3d.platform.InputConstants.getKey(event), true);
            return true;
        }
        switch (event.key()) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP, InputConstants.KEY_A -> {
                book.turn(-1);
                return true;
            }
            case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN, InputConstants.KEY_D -> {
                book.turn(1);
                return true;
            }
            case InputConstants.KEY_BACKSPACE -> {
                book.back();
                return true;
            }
            case InputConstants.KEY_HOME -> {
                book.home();
                return true;
            }
            default -> {}
        }
        if (this.minecraft.options.keyInventory.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        book.close();
    }

    @Override
    public void removed() {
        this.look(false, 0, 0);
        net.minecraft.client.KeyMapping.releaseAll();
        if (HandheldBook.current() == book && !book.closing) book.close();
    }
}
