package net.ixdarklord.folios.internal.client.handheld;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.Util;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class SharedBooks {
    private static final long FORGET_MILLIS = 3000;
    private static final float SIZE = 0.95F;

    private static final class Shared {
        final FoliosReader reader;
        long heard;
        boolean shut;
        float open;
        long drawn;

        Shared(FoliosReader reader, long heard, boolean shut) {
            this.reader = reader;
            this.heard = heard;
            this.shut = shut;
            this.open = shut ? 0 : 1;
        }

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

    public static void receive(FoliosPayloads.SharedReading payload) {
        Minecraft minecraft = Minecraft.getInstance();
        FoliosPayloads.ReadingAt at = payload.at();
        if (!at.open() || minecraft.player == null || payload.reader() == minecraft.player.getId()) {
            BOOKS.remove(payload.reader());
            return;
        }
        FoliosReader.Spot spot = new FoliosReader.Spot(at.chapter(), at.page());
        Shared shared = BOOKS.get(payload.reader());
        long now = Util.getMillis();
        if (shared != null && shared.reader.book.id().equals(at.book())) {
            shared.shut = at.shut();
            if (!spot.equals(shared.reader.spot)) {
                shared.reader.moveAlong(spot);
                Entity reader = minecraft.level != null ? minecraft.level.getEntity(payload.reader()) : null;
                if (reader != null && minecraft.level != null) {
                    minecraft.level.playLocalSound(reader.getX(), reader.getEyeY(), reader.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.0F, false);
                }
            }
            shared.heard = now;
            return;
        }
        FoliosBook book = FoliosLibrary.book(at.book());
        FoliosSession session = FoliosLibrary.session(at.book());
        if (book == null || session == null) {
            BOOKS.remove(payload.reader());
            return;
        }
        FoliosReader reader = new FoliosReader(book, session, null);
        reader.spot = spot;
        BOOKS.put(payload.reader(), new Shared(reader, now, at.shut()));
    }

    public static boolean isShown(Entity holder) {
        return isShown(holder.getId());
    }

    public static boolean isShown(int holder) {
        return BOOKS.containsKey(holder) || ownBook(holder) != null;
    }

    private static @Nullable HandheldBook ownBook(int holder) {
        Minecraft minecraft = Minecraft.getInstance();
        HandheldBook book = HandheldBook.current();
        if (book == null || book.sketch || minecraft.player == null || holder != minecraft.player.getId()) return null;
        if (minecraft.options.getCameraType().isFirstPerson() || !(book.isOpen() || book.isRaised())) return null;
        return book;
    }

    private static final float HELD_UP = 1.2F;
    private static final float HELD_AHEAD = 0.7F;
    private static final float CROUCHED = 0.2F;
    private static final float LEAN = 50.0F;

    private static final float SHUT_IN = (float) Math.atan((FoliosLayout.PAGE_WIDTH + HandheldRenderer.EDGE) / 2.0F
            * HandheldRenderer.SCALE * SIZE * 16 / 10.0F);

    public static float openness(int holder) {
        HandheldBook own = ownBook(holder);
        if (own != null) return own.open(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        Shared shared = BOOKS.get(holder);
        return shared != null ? shared.open : 1;
    }

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

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (BOOKS.isEmpty()) return;
        long now = Util.getMillis();
        BOOKS.entrySet().removeIf(entry -> now - entry.getValue().heard > FORGET_MILLIS || minecraft.level == null
                || !(minecraft.level.getEntity(entry.getKey()) instanceof AbstractClientPlayer player) || player.isRemoved());
        for (int id : BOOKS.keySet()) {
            if (minecraft.level.getEntity(id) instanceof AbstractClientPlayer player) {
                player.setYBodyRot(net.minecraft.util.Mth.rotLerp(0.5F, player.yBodyRot, player.getYHeadRot()));
            }
        }
    }

    public static void submit(AbstractClientPlayer player, float partialTicks, PoseStack pose, SubmitNodeCollector collector, int light) {
        Shared shared = BOOKS.get(player.getId());
        HandheldBook own = ownBook(player.getId());
        if (shared == null && own == null || player.isInvisible()) return;
        FoliosReader reader = own != null ? own : shared.reader;
        float open = own != null ? own.open(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) : shared.openNow();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-net.minecraft.util.Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot)));
        pose.scale(player.getScale(), player.getScale(), player.getScale());
        pose.translate(0.0F, HELD_UP - (player.isCrouching() ? CROUCHED : 0), HELD_AHEAD);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-LEAN));
        pose.scale(HandheldRenderer.SCALE * SIZE, -HandheldRenderer.SCALE * SIZE, HandheldRenderer.SCALE * SIZE);
        pose.translate(0.0F, -FoliosLayout.PAGE_HEIGHT / 2.0F, 0.0F);
        HandheldRenderer.drawShared(pose, collector, light, reader, open);
        pose.popPose();
    }
}
