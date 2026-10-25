package net.ixdarklord.folios.internal.client.handheld;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.ixdarklord.folios.internal.client.screen.FoliosBar;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// A book held open in the player's hands: a reader of the book, and how far it has been raised and opened. It is
// drawn by HandheldRenderer in place of the hands; HandheldScreen frees the pointer to read it. It comes up from
// below, opens, and is shut and lowered again when put away; it can't be leafed through while it moves.
public final class HandheldBook extends FoliosReader {
    static final float RAISE_TICKS = 8;
    // Ticks to open: its latch first, then the cover's swing (10 of them).
    static final float OPEN_TICKS = 14;

    private static @Nullable HandheldBook current;

    final @Nullable InteractionHand hand;
    // A screen book raised out of the hand and opened before its screen shows: its pages a likeness of pages, not
    // pressable; once it lies open its screen comes up over it, and when that goes the book is shut and lowered.
    final boolean sketch;
    private boolean screenShown;
    private @Nullable CameraType camera;
    private float raise;
    private float raiseO;
    private float open;
    private float openO;
    boolean closing;
    private boolean shut;

    // Filled by the renderer each frame: the pressable places on each page, the pointer on each, the tooltip.
    final List<AbstractPainter.Hit> leftHits = new ArrayList<>();
    final List<AbstractPainter.Hit> rightHits = new ArrayList<>();
    float[] leftPointer = {Float.NaN, Float.NaN};
    float[] rightPointer = {Float.NaN, Float.NaN};
    @Nullable List<Component> tooltip;
    // The popup window of the link under the pointer, if it has one.
    net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup popup;
    // How each page lies on the screen, from the last frame: page pixels to clip space.
    final Matrix4f leftClip = new Matrix4f();
    final Matrix4f rightClip = new Matrix4f();
    boolean placed;
    // Its bar: drawn over it in the world once it lies open (`barPlaced` this frame), pressed through the pointer on
    // its plane; and since when the book has been fully open, for the bar growing out of its pull.
    @Nullable FoliosBar bar;
    final List<AbstractPainter.Hit> barHits = new ArrayList<>();
    float[] barPointer = {Float.NaN, Float.NaN};
    final Matrix4f barClip = new Matrix4f();
    boolean barPlaced;
    long openSince;
    // The spread last told to the server for those near to read along, null while none is shown them.
    private @Nullable Spot shared;
    private boolean sharedShut;

    private HandheldBook(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at, @Nullable InteractionHand hand, boolean sketch) {
        super(book, session, at, hand);
        this.hand = hand;
        this.sketch = sketch;
    }

    /**
     * Opens a screen book used from a hand: raised and opened in the hands first, its screen coming up once it lies
     * open. With no hand (a command, a link), the screen opens at once.
     */
    public static void openScreen(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at, @Nullable InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (hand == null || minecraft.player == null || current != null) {
            minecraft.gui.setScreen(new net.ixdarklord.folios.internal.client.screen.FoliosScreen(book, session, at, hand));
            return;
        }
        HandheldBook opened = new HandheldBook(book, session, at, hand, true);
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            opened.camera = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        current = opened;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.8F));
    }

    public static @Nullable HandheldBook current() {
        return current;
    }

    public static void open(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at, @Nullable InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        HandheldBook previous = current;
        HandheldBook opened = new HandheldBook(book, session, at, hand, false);
        // Read through the reader's own eyes: from outside, the camera is put back as it was afterwards.
        opened.camera = previous != null ? previous.camera : null;
        if (opened.camera == null && !minecraft.options.getCameraType().isFirstPerson()) {
            opened.camera = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        // Picked up, it is raised shut, its cover to the reader, and opened by them, unless it was put away lying open:
        // then it opens by itself where it was left. Sent to a page, it opens there.
        opened.shut = at == null && !opened.memory.leftOpen();
        current = opened;
        // Carried over from a book already open in the hand: it stays marked as being read.
        if (previous != null) opened.marked = previous.marked;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.8F));
        minecraft.gui.setScreen(new HandheldScreen(opened));
    }

    // ---- Time ----

    public static void tick() {
        HandheldBook book = current;
        if (book == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            book.finish();
            return;
        }
        book.raiseO = book.raise;
        book.openO = book.open;
        if (book.closing) {
            // Shut first, then lowered; the two overlap a little, as hands would.
            book.open = Math.max(0, book.open - 1.4F / OPEN_TICKS);
            if (book.open < 0.35F) book.raise = Math.max(0, book.raise - 1 / RAISE_TICKS);
            if (book.raise <= 0 && book.raiseO <= 0) book.finish();
        } else {
            book.raise = Math.min(1, book.raise + 1 / RAISE_TICKS);
            // Opened or shut in the reader's hands, towards whichever it is to be.
            if (book.shut) book.open = Math.max(0, book.open - 1.2F / OPEN_TICKS);
            else if (book.raise > 0.6F) {
                book.open = Math.min(1, book.open + 1 / OPEN_TICKS);
                // Lying open at last: its pages' content shows.
                if (book.open >= 1 && book.openO < 1) book.reveal();
            }
        }
        if (book.sketch) book.tickScreen(minecraft);
        book.mark();
        book.share();
    }

    // A screen book's screen: up once the book lies open; gone (closed, or another screen over it), the book goes.
    private void tickScreen(Minecraft minecraft) {
        if (closing) return;
        if (!screenShown) {
            if (open >= 1) {
                screenShown = true;
                minecraft.gui.setScreen(new net.ixdarklord.folios.internal.client.screen.FoliosScreen(book, session, this.here(), this.hand));
            } else if (minecraft.gui.screen() != null) {
                this.close();
            }
            return;
        }
        if (!(minecraft.gui.screen() instanceof net.ixdarklord.folios.internal.client.screen.FoliosScreen screen)) {
            this.close();
            return;
        }
        // Where the screen has the book open, for those near who see it in the reader's hands: they see a leaf turn
        // as the reader turns one.
        Spot reading = screen.reader().spot;
        if (!reading.chapter().startsWith("search:")) spot = reading;
    }

    // Those near the reader read along while it lies open in their hands: each spread it comes to is told to the
    // server, which tells them; held up shut, they see it held so, shut; put away, it is taken from them.
    // Marked as being read (the server puts the mark on the stack in the hand) only while it lies open: others then
    // see it open in the reader's hands, and shut, raised or lowered, it is the book item, as it is in the hand.
    private boolean marked;

    private void mark() {
        if (hand == null) return;
        boolean lying = this.isOpen() || sketch && this.open >= 1 && !closing;
        if (lying == marked) return;
        marked = lying;
        Network.sendToServer(lying ? new FoliosPayloads.Reading(true, hand.ordinal()) : new FoliosPayloads.Reading(false, -1));
    }

    private void share() {
        if (hand == null) return;
        // A search's long query isn't sent; the spread before it stays shown.
        boolean held = this.isOpen() || this.isRaised();
        Spot now = held ? spot : null;
        boolean shut = held && !this.isOpen();
        if (now != null && now.chapter().length() > 200) return;
        if (java.util.Objects.equals(now, shared) && shut == sharedShut) return;
        shared = now;
        sharedShut = shut;
        Network.sendToServer(now != null ? new FoliosPayloads.ReadingAt(true, book.id(), now.chapter(), now.page(), shut)
                : new FoliosPayloads.ReadingAt(false, FoliosPayloads.NO_BOOK, "", 0, false));
    }

    float raise(float partialTick) {
        return Mth.lerp(partialTick, raiseO, raise);
    }

    float open(float partialTick) {
        return Mth.lerp(partialTick, openO, open);
    }

    boolean isOpen() {
        return open >= 1 && !closing && !shut;
    }

    /** Whether it is held up shut (or shutting), its cover to the reader. */
    boolean isShut() {
        return shut && !closing;
    }

    /** Whether it has been raised all the way. */
    boolean isRaised() {
        return raise >= 1 && !closing;
    }

    /** Opens a book held up shut. */
    void openCover() {
        if (!shut || closing) return;
        shut = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.9F));
    }

    /** Shuts the book in the reader's hands: it stays held up, to be opened again or put away. */
    public void shut() {
        if (shut || closing) return;
        shut = true;
        flip = null;
        this.save();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.75F, 0.9F));
    }

    // Its spread's reveal plays once the cover lies open, not while it swings: until then what is on the pages waits.
    @Override
    protected boolean revealHeld() {
        return !sketch && (open < 1 || shut);
    }

    @Override
    protected boolean canMove() {
        return this.isOpen();
    }

    /** Shuts and lowers the book; it is put away once it's down. */
    void close() {
        if (closing) return;
        // Put away lying open, it opens again by itself next time; shut, it is raised shut.
        if (!sketch) memory.setLeftOpen(this.isOpen());
        closing = true;
        this.save();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.7F, 0.8F));
    }

    private void finish() {
        if (current != this) return;
        current = null;
        this.save();
        if (hand != null && marked) Network.sendToServer(new FoliosPayloads.Reading(false, -1));
        marked = false;
        shared = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (camera != null) minecraft.options.setCameraType(camera);
        if (minecraft.gui.screen() instanceof HandheldScreen) minecraft.gui.setScreen(null);
    }

    /** Forgets the book at once, as when the player leaves the world. */
    public static void drop() {
        if (current != null) current.finish();
    }
}
