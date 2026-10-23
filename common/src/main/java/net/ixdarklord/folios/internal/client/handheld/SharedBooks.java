package net.ixdarklord.folios.internal.client.handheld;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.internal.GuidePayloads;
import net.ixdarklord.folios.internal.client.GuideLibrary;
import net.ixdarklord.folios.internal.client.GuideReader;
import net.ixdarklord.folios.internal.client.GuideSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// The handheld books others near the player are reading, drawn open in their hands for the player to read along:
// each a reader of the same book on this client, kept at the spread its reader has open and turning its leaves as
// theirs turn. The server tells of them while they are near, about once a second; one not heard of for a while is
// let go, as when its reader walks off.
public final class SharedBooks {
    /** How long (ms) a book is kept without word of it. */
    private static final long FORGET_MILLIS = 3000;
    /** How big the book is drawn in its reader's hands, of the size it is read at. */
    private static final float SIZE = 0.95F;

    // A reader's book: shut or open as they last said, and how far open it is drawn now, easing to that.
    private static final class Shared {
        final GuideReader reader;
        long heard;
        boolean shut;
        float open;
        long drawn;

        Shared(GuideReader reader, long heard, boolean shut) {
            this.reader = reader;
            this.heard = heard;
            this.shut = shut;
            this.open = shut ? 0 : 1;
        }

        // How far open it is now, moved on towards shut or open at the pace the reader's own opens.
        float openNow() {
            long now = Util.getMillis();
            float step = drawn == 0 ? 0 : (now - drawn) / (HandheldBook.OPEN_TICKS * 50.0F);
            drawn = now;
            open = shut ? Math.max(0, open - step) : Math.min(1, open + step);
            return open;
        }
    }

    private static final Map<Integer, Shared> BOOKS = new HashMap<>();

    private SharedBooks() {}

    public static void receive(GuidePayloads.SharedReading payload) {
        Minecraft minecraft = Minecraft.getInstance();
        GuidePayloads.ReadingAt at = payload.at();
        if (!at.open() || minecraft.player == null || payload.reader() == minecraft.player.getId()) {
            BOOKS.remove(payload.reader());
            return;
        }
        GuideReader.Spot spot = new GuideReader.Spot(at.chapter(), at.page());
        Shared shared = BOOKS.get(payload.reader());
        long now = Util.getMillis();
        if (shared != null && shared.reader.book.id().equals(at.book())) {
            shared.shut = at.shut();
            if (!spot.equals(shared.reader.spot)) {
                shared.reader.moveAlong(spot);
                // Their page turning, heard where they stand.
                Entity reader = minecraft.level != null ? minecraft.level.getEntity(payload.reader()) : null;
                if (reader != null && minecraft.level != null) {
                    minecraft.level.playLocalSound(reader.getX(), reader.getEyeY(), reader.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.0F, false);
                }
            }
            shared.heard = now;
            return;
        }
        // A book this client has too: read from the spread its reader has open.
        FoliosBook book = GuideLibrary.book(at.book());
        GuideSession session = GuideLibrary.session(at.book());
        if (book == null || session == null) {
            BOOKS.remove(payload.reader());
            return;
        }
        GuideReader reader = new GuideReader(book, session, null);
        reader.spot = spot;
        BOOKS.put(payload.reader(), new Shared(reader, now, at.shut()));
    }

    /** Whether someone is shown reading along with: their held book is then drawn here, not as their item. */
    public static boolean isShown(Entity holder) {
        return isShown(holder.getId());
    }

    public static boolean isShown(int holder) {
        return BOOKS.containsKey(holder) || ownBook(holder) != null;
    }

    // The player's own book held up in their hands, shut or open, while they look at themselves from outside.
    private static @Nullable HandheldBook ownBook(int holder) {
        Minecraft minecraft = Minecraft.getInstance();
        HandheldBook book = HandheldBook.current();
        if (book == null || book.sketch || minecraft.player == null || holder != minecraft.player.getId()) return null;
        if (minecraft.options.getCameraType().isFirstPerson() || !(book.isOpen() || book.isRaised())) return null;
        return book;
    }

    /** Where the book is held, in the reader's frame (blocks: up from their feet, ahead of them). */
    private static final float HELD_UP = 1.2F;
    private static final float HELD_AHEAD = 0.7F;
    private static final float CROUCHED = 0.2F;
    /** How far back the book leans, towards the reader's eyes, in degrees from upright. */
    private static final float LEAN = 50.0F;

    /**
     * How far each arm turns in, in radians, to hold a book held up shut by its lower corners: the shut book is half
     * as wide as the open one and sits in the middle, so each hand reaches in by half a board's width (in model
     * pixels) over the arm's 10.
     */
    private static final float SHUT_IN = (float) Math.atan((FoliosLayout.PAGE_WIDTH + HandheldRenderer.EDGE) / 2.0F
            * HandheldRenderer.SCALE * SIZE * 16 / 10.0F);

    /** How far open the book someone holds up is drawn now, 0 shut to 1 open; 1 for none. */
    public static float openness(int holder) {
        HandheldBook own = ownBook(holder);
        if (own != null) return own.open(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        Shared shared = BOOKS.get(holder);
        return shared != null ? shared.open : 1;
    }

    /** Puts the arms of someone shown reading along with low and forward, angled in under the book's lower edge. */
    // Each arm straight from its shoulder to a lower corner of the book: the corners are as far apart as the shoulders,
    // so the arms reach straight ahead, raised so the hands (an arm's length, 10 of its 12 pixels, from the shoulder)
    // meet the corners where the book is held (HELD_UP, HELD_AHEAD, leaning back by LEAN).
    // Held up shut, the book is narrower and the arms turn in to its corners, easing out as it opens.
    public static void holdArms(net.minecraft.client.model.geom.ModelPart right, net.minecraft.client.model.geom.ModelPart left, boolean crouching, float open) {
        float pitch = crouching ? -0.88F : -1.05F;
        float swing = CoverModel.swing(open);
        float in = SHUT_IN * (1 - swing * swing * (3 - 2 * swing));
        right.xRot = pitch;
        right.yRot = -in;
        right.zRot = 0;
        left.xRot = pitch;
        left.yRot = in;
        left.zRot = 0;
    }

    public static void clear() {
        BOOKS.clear();
    }

    /** Lets go of books not heard of for a while, or whose reader is gone. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (BOOKS.isEmpty()) return;
        long now = Util.getMillis();
        BOOKS.entrySet().removeIf(entry -> now - entry.getValue().heard > FORGET_MILLIS || minecraft.level == null
                || !(minecraft.level.getEntity(entry.getKey()) instanceof AbstractClientPlayer player) || player.isRemoved());
        // A reader faces their book: their body turns with their head, so the book held before it is where they look.
        for (int id : BOOKS.keySet()) {
            if (minecraft.level.getEntity(id) instanceof AbstractClientPlayer player) {
                player.setYBodyRot(net.minecraft.util.Mth.rotLerp(0.5F, player.yBodyRot, player.getYHeadRot()));
            }
        }
    }

    // Drawn with its reader, from where they stand (`pose` is at their feet, the world's way up): in front of them,
    // between their hands at their chest, leaning back towards their eyes, its pages to anyone looking over their
    // shoulder or standing by.
    public static void submit(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        Shared shared = BOOKS.get(state.id);
        HandheldBook own = ownBook(state.id);
        if (shared == null && own == null || state.isInvisible) return;
        GuideReader reader = own != null ? own : shared.reader;
        // Held up shut, then opened, as the reader does.
        float open = own != null ? own.open(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) : shared.openNow();
        pose.pushPose();
        // In the reader's frame, z ahead of them; the book faces back at them, leaning back towards their eyes.
        pose.mulPose(Axis.YP.rotationDegrees(-state.bodyRot));
        pose.scale(state.scale, state.scale, state.scale);
        pose.translate(0.0F, HELD_UP - (state.isCrouching ? CROUCHED : 0), HELD_AHEAD);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-LEAN));
        pose.scale(HandheldRenderer.SCALE * SIZE, -HandheldRenderer.SCALE * SIZE, HandheldRenderer.SCALE * SIZE);
        pose.translate(0.0F, -FoliosLayout.PAGE_HEIGHT / 2.0F, 0.0F);
        // Lit as its reader is, by the world round them.
        HandheldRenderer.drawShared(pose, collector, state.lightCoords, reader, open);
        pose.popPose();
    }
}
