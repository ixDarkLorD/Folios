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

public final class HandheldBook extends FoliosReader {
    static final float RAISE_TICKS = 8;
    static final float OPEN_TICKS = 14;

    private static @Nullable HandheldBook current;

    final @Nullable InteractionHand hand;
    final boolean sketch;
    private boolean screenShown;
    private @Nullable CameraType camera;
    private float raise;
    private float raiseO;
    private float open;
    private float openO;
    boolean closing;
    private boolean shut;

    final List<AbstractPainter.Hit> leftHits = new ArrayList<>();
    final List<AbstractPainter.Hit> rightHits = new ArrayList<>();
    float[] leftPointer = {Float.NaN, Float.NaN};
    float[] rightPointer = {Float.NaN, Float.NaN};
    @Nullable List<Component> tooltip;
    net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup popup;
    final Matrix4f leftClip = new Matrix4f();
    final Matrix4f rightClip = new Matrix4f();
    boolean placed;
    @Nullable FoliosBar bar;
    final List<AbstractPainter.Hit> barHits = new ArrayList<>();
    float[] barPointer = {Float.NaN, Float.NaN};
    final Matrix4f barClip = new Matrix4f();
    boolean barPlaced;
    long openSince;
    private @Nullable Spot shared;
    private boolean sharedShut;

    private HandheldBook(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at, @Nullable InteractionHand hand, boolean sketch) {
        super(book, session, at, hand);
        this.hand = hand;
        this.sketch = sketch;
    }

    public static void openScreen(FoliosBook book, FoliosSession session, FoliosBook.@Nullable Location at, @Nullable InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (hand == null || minecraft.player == null || current != null) {
            minecraft.setScreen(new net.ixdarklord.folios.internal.client.screen.FoliosScreen(book, session, at, hand));
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
        opened.camera = previous != null ? previous.camera : null;
        if (opened.camera == null && !minecraft.options.getCameraType().isFirstPerson()) {
            opened.camera = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        opened.shut = at == null && !opened.memory.leftOpen();
        current = opened;
        if (previous != null) opened.marked = previous.marked;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.8F));
        minecraft.setScreen(new HandheldScreen(opened));
    }

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
            book.open = Math.max(0, book.open - 1.4F / OPEN_TICKS);
            if (book.open < 0.35F) book.raise = Math.max(0, book.raise - 1 / RAISE_TICKS);
            if (book.raise <= 0 && book.raiseO <= 0) book.finish();
        } else {
            book.raise = Math.min(1, book.raise + 1 / RAISE_TICKS);
            if (book.shut) book.open = Math.max(0, book.open - 1.2F / OPEN_TICKS);
            else if (book.raise > 0.6F) {
                book.open = Math.min(1, book.open + 1 / OPEN_TICKS);
                if (book.open >= 1 && book.openO < 1) book.reveal();
            }
        }
        if (book.sketch) book.tickScreen(minecraft);
        book.mark();
        book.share();
    }

    private void tickScreen(Minecraft minecraft) {
        if (closing) return;
        if (!screenShown) {
            if (open >= 1) {
                screenShown = true;
                minecraft.setScreen(new net.ixdarklord.folios.internal.client.screen.FoliosScreen(book, session, this.here(), this.hand));
            } else if (minecraft.screen != null) {
                this.close();
            }
            return;
        }
        if (!(minecraft.screen instanceof net.ixdarklord.folios.internal.client.screen.FoliosScreen screen)) {
            this.close();
            return;
        }
        Spot reading = screen.reader().spot;
        if (!reading.chapter().startsWith("search:")) spot = reading;
    }

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

    boolean isShut() {
        return shut && !closing;
    }

    boolean isRaised() {
        return raise >= 1 && !closing;
    }

    void openCover() {
        if (!shut || closing) return;
        shut = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.9F));
    }

    public void shut() {
        if (shut || closing) return;
        shut = true;
        flip = null;
        this.save();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.75F, 0.9F));
    }

    @Override
    protected boolean revealHeld() {
        return !sketch && (open < 1 || shut);
    }

    @Override
    protected boolean canMove() {
        return this.isOpen();
    }

    void close() {
        if (closing) return;
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
        if (minecraft.screen instanceof HandheldScreen) minecraft.setScreen(null);
    }

    public static void drop() {
        if (current != null) current.finish();
    }
}
