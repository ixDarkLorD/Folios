package net.ixdarklord.folios.internal.client.handheld;

import net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyledEditBox;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.ixdarklord.folios.internal.client.screen.GuideBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

// Reading a handheld book. The book is in the world, in the reader's hands (HandheldRenderer), with its bar over it
// once it lies open; this screen draws nothing but tooltips. It is here to free the pointer: what is on the pages and
// the bar is pressed with it, the wheel and the arrow keys turn the leaves, and closing it shuts the book and lowers
// it. The bar's search field is a hidden text box here that takes the keys. As the book allows, the reader looks
// about while holding the right button and walks with the movement keys.
public final class HandheldScreen extends Screen {
    private final HandheldBook book;
    private final GuideBar bar;
    private @Nullable StyledEditBox search;

    HandheldScreen(HandheldBook book) {
        super(book.book.title());
        this.book = book;
        this.bar = new GuideBar(book, false, book::shut, field -> {
            this.setFocused(field);
            field.setFocused(true);
        });
        book.bar = this.bar;
    }

    @Override
    protected void init() {
        hintY = Integer.MIN_VALUE;
        this.search = this.addRenderableWidget(this.bar.search(this.search));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The world shows through: the book is in it.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.search != null && this.search.isFocused() && !book.isOpen()) {
            this.search.setFocused(false);
            this.setFocused(null);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        // Where the pointer is on each page and on the bar, for the book's next frame.
        float[] none = {Float.NaN, Float.NaN};
        book.leftPointer = book.placed ? this.pointer(book.leftClip, mouseX, mouseY) : none;
        book.rightPointer = book.placed ? this.pointer(book.rightClip, mouseX, mouseY) : none.clone();
        book.barPointer = book.barPlaced ? this.pointer(book.barClip, mouseX, mouseY) : none.clone();
        // From outside, the mouse turns the view: nothing on the book is pointed at.
        if (outside()) return;
        List<Component> tooltip = book.tooltip;
        if (book.isShut() && book.placed && onCover(book.rightPointer)) {
            tooltip = List.of(Component.translatableWithFallback("folios.open", "Open the book"));
        }
        if (tooltip != null && !tooltip.isEmpty()) net.ixdarklord.folios.internal.client.GuideTooltips.show(graphics, this.font, tooltip, mouseX, mouseY, book.book);
        this.lookHint(graphics);
    }

    /** Minecraft's own right-button icon, from its tutorial toasts. */
    private static final net.minecraft.resources.Identifier RIGHT_CLICK = net.minecraft.resources.Identifier.withDefaultNamespace("toast/right_click");

    /** How long (ms) the hint stays once the book lies open, then how long it takes to fade. */
    private static final long HINT_MILLIS = 6000;
    private static final long HINT_FADE = 600;
    // Once the reader has looked about, they know how: no hint again this game.
    private static boolean lookedAbout;
    private long openedAt;
    private int hintY = Integer.MIN_VALUE;

    // Over the open book (over its bar, while that is out), a hint that the right button held looks about:
    // Minecraft's right-click icon and a line beside it, framed as the book's tooltips are. It fades after a few
    // seconds, and goes for good once the reader looks about; never for a book that doesn't allow it.
    private void lookHint(GuiGraphicsExtractor graphics) {
        if (!book.isOpen()) {
            openedAt = 0;
            hintY = Integer.MIN_VALUE;
            return;
        }
        if (openedAt == 0) openedAt = net.minecraft.util.Util.getMillis();
        if (lookedAbout || looking || !book.placed || !book.book.handling().lookAround()) return;
        long shown = net.minecraft.util.Util.getMillis() - openedAt;
        if (shown > HINT_MILLIS + HINT_FADE) return;
        float alpha = shown <= HINT_MILLIS ? 1 : 1 - (shown - HINT_MILLIS) / (float) HINT_FADE;
        int fade = Math.round(alpha * 255);
        if (fade < 8) return;
        Component hint = Component.translatableWithFallback("folios.hint.look", "Hold right click to look around");
        int wide = 16 + 4 + this.font.width(hint);
        int tall = 16;
        // The top of the bar over the book: its rim, the gap and the bar's height above the pages' head (taken from
        // the page, as the bar may still be growing out of its pull).
        float[] top = this.onScreen(book.rightClip, 0, -(net.ixdarklord.folios.internal.client.screen.GuideScreen.BORDER + 5 + GuideBar.HEIGHT));
        if (top == null) return;
        int x = (this.width - wide) / 2;
        // Placed once, where the book first lies: it stays put as the book bobs with the reader's steps.
        if (hintY == Integer.MIN_VALUE) hintY = Math.max(14, Math.round(top[1]) - tall - 12);
        int y = hintY;
        graphics.nextStratum();
        net.ixdarklord.folios.internal.client.GuideTooltips.frame(graphics, x, y, wide, tall, book.book, fade);
        graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, RIGHT_CLICK, x - 2, y - 2, 20, 20, alpha);
        int ink = net.ixdarklord.folios.internal.client.GuideTooltips.titleColor(book.book);
        graphics.text(this.font, hint, x + 20, y + 4, (fade << 24) | (ink & 0xFFFFFF), true);
    }

    // Where a point of a page or the bar (its own pixels) is on the screen, in GUI pixels; null behind the eye.
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
    }

    // ---- Looking about and walking while reading ----

    // Looking about: the pointer hidden and free while the right button is held, put back where it was after.
    private boolean looking;
    private double lookFromX;
    private double lookFromY;

    // Seen from outside (the perspective key cycles the view while reading), the pointer is let go of the pages and the
    // mouse turns the view freely, as in play; back in the reader's own eyes, the pointer is theirs again.
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
            GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        } else {
            GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            GLFW.glfwSetCursorPos(window.handle(), lookFromX, lookFromY);
        }
    }

    // The movement keys, passed to the player while the book allows walking with it open.
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
        // From outside, the pages can't be pressed; the mouse only turns the view.
        if (outside()) return true;
        if (event.button() == 1 && book.book.handling().lookAround() && !this.bar.typing()) {
            lookedAbout = true;
            this.look(true, event.x(), event.y());
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (this.search != null && this.search.isFocused()) {
            this.search.setFocused(false);
            this.setFocused(null);
        }
        if (event.button() == 0 && book.barPlaced && this.press(book.barHits, book.barPointer, book.barClip)) return true;
        if (event.button() == 3) {
            book.turn(-1);
            return true;
        }
        if (event.button() != 0) return false;
        // Held up shut: a press on its cover opens it, a press off it puts it away.
        if (book.isShut()) {
            if (!book.placed) return true;
            if (onCover(book.rightPointer)) book.openCover();
            else if (book.book.clickOutsideCloses()) this.onClose();
            return true;
        }
        if (!book.isOpen()) return false;
        if (this.press(book.rightHits, book.rightPointer, book.rightClip) || this.press(book.leftHits, book.leftPointer, book.leftClip)) return true;
        // A press on a page's outer half turns it; a press off the book puts it away.
        float[] right = book.rightPointer;
        float[] left = book.leftPointer;
        if (inside(right, 0, 300) || inside(left, 0, 300)) {
            if (inside(right, net.ixdarklord.folios.api.client.page.GuideLayout.PAGE_WIDTH * 0.6F, 400)) book.turn(1);
            else if (inside(left, 0, net.ixdarklord.folios.api.client.page.GuideLayout.PAGE_WIDTH * 0.4F)) book.turn(-1);
            return true;
        }
        if (book.book.clickOutsideCloses()) this.onClose();
        return true;
    }

    // Whether the pointer is over the shut book: its cover lies over the right page, a little larger than it.
    private static boolean onCover(float[] at) {
        float margin = 6;
        return !Float.isNaN(at[0]) && at[0] >= -margin && at[0] < net.ixdarklord.folios.api.client.page.GuideLayout.PAGE_WIDTH + margin
                && at[1] >= -margin && at[1] < net.ixdarklord.folios.api.client.page.GuideLayout.PAGE_HEIGHT + margin;
    }

    // Whether the pointer is on a page (within its height) between two x's.
    private static boolean inside(float[] at, float x0, float x1) {
        return !Float.isNaN(at[0]) && at[0] >= x0 && at[0] < Math.min(x1, net.ixdarklord.folios.api.client.page.GuideLayout.PAGE_WIDTH)
                && at[1] >= 0 && at[1] < net.ixdarklord.folios.api.client.page.GuideLayout.PAGE_HEIGHT;
    }

    // What on a page is being dragged while the left button is held, and how that page lies on the screen.
    private AbstractPainter.@Nullable Drag dragging;
    private org.joml.@Nullable Matrix4f draggedOn;

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (looking && event.button() == 1 && !outside()) {
            // Turned as the view is turned in play, at the player's own sensitivity.
            this.turn(dx, dy);
            return true;
        }
        if (dragging != null && draggedOn != null && event.button() == 0) {
            // How far the pointer moved, carried onto the page's own plane.
            float[] now = HandheldRenderer.onPage(draggedOn, event.x(), event.y(), this.width, this.height);
            float[] before = HandheldRenderer.onPage(draggedOn, event.x() - dx, event.y() - dy, this.width, this.height);
            if (now != null && before != null) dragging.moved(now[0] - before[0], now[1] - before[1]);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 1 && looking) {
            this.look(false, 0, 0);
            return true;
        }
        if (event.button() == 0) {
            dragging = null;
            draggedOn = null;
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
        if (scrollY == 0) return true;
        // Over something on a page that takes the wheel (a scene zooming), it goes there; otherwise it turns the page.
        if (book.isOpen()) {
            net.ixdarklord.folios.api.client.page.GuidePainter.Scroll scroll = null;
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
        // The view cycles as in play; from outside, the mouse turns it freely.
        if (this.minecraft.options.keyTogglePerspective.matches(event) && !this.bar.typing()) {
            var options = this.minecraft.options;
            options.setCameraType(options.getCameraType().cycle());
            boolean free = outside() && book.book.handling().lookAround();
            this.look(free, this.width / 2.0, this.height / 2.0);
            lastX = Double.NaN;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F && event.hasControlDownWithQuirk() && this.search != null && book.isOpen()) {
            this.setFocused(this.search);
            this.search.setFocused(true);
            return true;
        }
        // While searching, keys are the field's (Escape still closes).
        if (this.bar.typing()) return super.keyPressed(event);
        // Walking with it, the movement keys are the player's (so D and Space walk and jump, and the arrows turn).
        boolean walking = this.walks(event);
        if (book.isShut()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_D -> {
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
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_A -> {
                book.turn(-1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_D -> {
                book.turn(1);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                book.back();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
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
        // The book shuts and is lowered first; the screen goes once it is down (HandheldBook.finish).
        book.close();
    }

    @Override
    public void removed() {
        // Done looking about and walking: the pointer back, the keys let go.
        this.look(false, 0, 0);
        net.minecraft.client.KeyMapping.releaseAll();
        // Closed some other way (another screen opened over it): the book goes too.
        if (HandheldBook.current() == book && !book.closing) book.close();
    }
}
