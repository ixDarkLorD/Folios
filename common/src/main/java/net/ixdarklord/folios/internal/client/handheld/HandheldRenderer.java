package net.ixdarklord.folios.internal.client.handheld;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.ixdarklord.folios.internal.client.screen.FoliosBar;
import net.ixdarklord.folios.internal.client.page.Shapes;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.ixdarklord.folios.internal.client.paint.WorldPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.ixdarklord.folios.internal.compat.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.ixdarklord.folios.internal.compat.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class HandheldRenderer {
    static final float SCALE = 0.0024F * 143 / 149;
    private static final int PW = FoliosLayout.PAGE_WIDTH;
    private static final int PH = FoliosLayout.PAGE_HEIGHT;
    private static float leaves = 17.5F;
    private static org.joml.@Nullable Matrix3f relight;
    private static float opened = 1;
    private static float board = 10.5F;
    private static final float LEAVES_SHUT = 17.5F;
    private static final float BOARD_SHUT = 10.5F;
    static final float EDGE = 13;
    private static final int TAB_HEIGHT = 22;
    private static final int LEAVES_SHOWN = 1;

    private HandheldRenderer() {}

    public static FoliosPalette palette(FoliosBook book) {
        int accent = 0xFF000000 | book.accent();
        if (net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle.mode() == net.ixdarklord.coolcatcore.api.config.ConfigTheme.Mode.DARK) {
            int bright = FoliosPalette.mix(accent, 0xFFF1E7D0, 0.3F);
            return book.colors().over(new FoliosPalette(0xFF2B2620, 0xFFE2D8C4, 0xFF9C8F7C, 0xFFF6EEDC, bright,
                    FoliosPalette.mix(bright, 0xFFFFFFFF, 0.25F), 0xFF353028, 0xFF4B4337), true);
        }
        int ink = 0xFF2E241B;
        int deep = FoliosPalette.mix(accent, ink, 0.35F);
        return book.colors().over(new FoliosPalette(0xFFF1E7D0, 0xFF3B3026, 0xFF8B7B66, ink, deep, FoliosPalette.mix(deep, 0xFF1A3A8A, 0.25F), 0xFFE6D8BA, 0xFFC4B08C), false);
    }

    public static boolean draw(float partialTick, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light) {
        HandheldBook book = HandheldBook.current();
        if (book == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        com.mojang.blaze3d.systems.RenderSystem.setShaderLights(LIGHT_0, LIGHT_1);
        float raise = book.raise(partialTick);
        float open = book.open(partialTick);
        leaves = LEAVES_SHUT;
        board = BOARD_SHUT;
        opened = open;
        int lit = LightCoordsUtil.max(light, LightCoordsUtil.pack(11, 0));
        FoliosPalette palette = palette(book.book);
        book.leftHits.clear();
        book.rightHits.clear();
        book.tooltip = null;
        book.popup = null;
        book.tabs.frame();

        pose.pushPose();
        float seconds = book.seconds();
        boolean held = raise >= 1;
        float distance = book.book.distance();
        Matrix4f reading = new Matrix4f().translate(0, -0.02F * distance / 0.64F, -distance)
                .rotate(Axis.XP.rotationDegrees(-book.book.tilt()));
        if (book.book.models().hands() && !player.isInvisible() && raise > 0) {
            pose.pushPose();
            net.ixdarklord.folios.internal.compat.Compat.mulPose(pose, reading);
            pose.translate(0, -(1 - easeOut(raise)) * 0.8F, 0);
            hands(pose, collector, light, player, open);
            pose.popPose();
        }
        float along = easeInOut(raise);
        net.ixdarklord.folios.internal.compat.Compat.mulPose(pose, blend(inHand(book, player), reading, along, Mth.sin(along * Mth.PI) * 0.06F));
        boolean asItem = open <= 0.001F && item(pose, collector, light, player, book);
        net.ixdarklord.folios.internal.compat.Compat.scale(pose, SCALE, -SCALE, SCALE);
        float shutShift = (1 - Mth.clamp(open, 0, 1)) * (PW + EDGE) / 2.0F;
        pose.translate(-shutShift, -PH / 2.0F, 0.0F);

        Matrix4f toClip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());

        boolean readable = book.isOpen();
        float turned = book.flipProgress();
        FoliosReader.Flip flip = book.flip;
        float angle = flip == null ? 0 : easeInOut(flip.forward() ? turned : 1 - turned) * Mth.PI;
        FoliosPageElement.Laid staticLeft = flip == null ? book.page(book.spot, 0) : flip.forward() ? flip.oldLeft() : flip.newLeft();
        FoliosPageElement.Laid staticRight = flip == null ? book.page(book.spot, 1) : flip.forward() ? flip.newRight() : flip.oldRight();
        boolean pressable = readable && flip == null;
        boolean rightCovered = flip != null && angle < COVERED;
        boolean leftCovered = flip != null && angle > Mth.PI - COVERED;
        float swing = easeInOut(CoverModel.swing(open));
        float fold = book.sketch ? SKETCH_FOLD * swing : 0;
        float coverAngle = (1 - swing) * Mth.PI + fold;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(-fold));
        if (!asItem) {
            body(pose, collector, light, book, false);
            paper(pose, collector, lit, palette, book.book.textures().page(), 0, 0, PW, PH, 0, !(staticRight instanceof FoliosPageElement.SpreadHalf));
        }
        book.rightClip.set(toClip).mul(pose.last().pose());
        if (open > 0.4F && !rightCovered) {
            WorldPainter right = painter(pose, collector, lit, palette, book, pressable ? book.rightPointer : null, book.rightHits);
            Shapes.pageFrame(right, book.book.textures().frame(), PW, PH, staticRight, true);
            deckle(right, book, 0, PW, true);
            if (book.sketch) {
                Shapes.foreEdge(right, PW, 1, PH - 1, true, LEAVES_SHOWN);
                sketch(right, true);
            }
            else {
                printed(right, book, staticRight, () -> {
                    if (staticRight != null) book.drawRevealed(right, staticRight, flip == null ? book.spot : flip.staticRight(), true);
                });
                book.chrome(right, flip == null ? book.spot : flip.staticRight(), true, pressable, null);
            }
            right.end();
            mergeTooltip(book, right);
        }
        if (open > 0.4F && !book.sketch) {
            WorldPainter tabs = painter(pose, collector, lit, palette, book, pressable ? book.rightPointer : null, pressable ? book.rightHits : NO_HITS);
            tabs(tabs, book);
            tabs.end();
            mergeTooltip(book, tabs);
            NO_HITS.clear();
        }
        bar(pose, collector, lit, book, toClip);
        if (flip != null && angle < Mth.HALF_PI) shadow(pose, collector, lit, angle, false);
        pose.popPose();
        if (!asItem) spine(pose, collector, light, book, -fold, coverAngle);

        pose.pushPose();
        org.joml.Matrix3f shutNormals = new org.joml.Matrix3f(pose.last().normal()).rotateY(Mth.PI);
        pose.mulPose(Axis.YP.rotation(coverAngle));
        relight = shutNormals.mul(new org.joml.Matrix3f(pose.last().normal()).invert());
        if (!asItem) {
            body(pose, collector, light, book, true);
            paper(pose, collector, lit, palette, book.book.textures().page(), -PW, 0, 0, PH, 0, !(staticLeft instanceof FoliosPageElement.SpreadHalf));
        }
        if (open > 0.4F && !leftCovered) {
            pose.pushPose();
            pose.translate(-PW, 0, 0);
            book.leftClip.set(toClip).mul(pose.last().pose());
            WorldPainter left = painter(pose, collector, lit, palette, book, pressable ? book.leftPointer : null, book.leftHits);
            Shapes.pageFrame(left, book.book.textures().frame(), PW, PH, staticLeft, false);
            deckle(left, book, 0, PW, false);
            Shapes.foreEdge(left, 0, 1, PH - 1, false, LEAVES_SHOWN);
            if (book.sketch) sketch(left, false);
            else {
                printed(left, book, staticLeft, () -> {
                    if (staticLeft != null) book.drawRevealed(left, staticLeft, flip == null ? book.spot : flip.staticLeft(), false);
                });
                book.chrome(left, flip == null ? book.spot : flip.staticLeft(), false, pressable, null);
            }
            left.end();
            mergeTooltip(book, left);
            pose.popPose();
        }
        if (flip != null && angle > Mth.HALF_PI) shadow(pose, collector, lit, angle, true);
        if (open < 1 && !asItem) cover(pose, collector, light, book);
        relight = null;
        pose.popPose();
        book.placed = book.isRaised();

        if (flip != null) leaf(pose, collector, lit, palette, book, flip, angle);
        pose.popPose();

        minecraft.renderBuffers().bufferSource().endBatch();
        return true;
    }

    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    public static void drawShared(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader reader) {
        drawShared(pose, collector, light, reader, 1);
    }

    public static void drawShared(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader reader, float open) {
        leaves = LEAVES_SHUT;
        board = BOARD_SHUT;
        opened = open;
        FoliosPalette palette = palette(reader.book);
        boolean sketch = isSketched(reader.book);
        reader.tabs.frame();
        float turned = reader.flipProgress();
        FoliosReader.Flip flip = reader.flip;
        float angle = flip == null ? 0 : easeInOut(flip.forward() ? turned : 1 - turned) * Mth.PI;
        FoliosPageElement.Laid staticLeft = flip == null ? reader.page(reader.spot, 0) : flip.forward() ? flip.oldLeft() : flip.newLeft();
        FoliosPageElement.Laid staticRight = flip == null ? reader.page(reader.spot, 1) : flip.forward() ? flip.newRight() : flip.oldRight();
        ResourceLocation frame = reader.book.textures().frame();
        float fold = SKETCH_FOLD;
        float swing = easeInOut(CoverModel.swing(open));
        float rightAngle = -fold * swing;
        float leftAngle = fold * swing + Mth.PI * (1 - swing);
        boolean pages = swing > 0.05F;
        pose.pushPose();
        pose.translate(-(1 - Mth.clamp(open, 0, 1)) * (PW + EDGE) / 2.0F, 0, 0);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(rightAngle));
        body(pose, collector, light, reader, false);
        paper(pose, collector, light, palette, reader.book.textures().page(), 0, 0, PW, PH, 0, !(staticRight instanceof FoliosPageElement.SpreadHalf));
        if (pages && !(flip != null && angle < COVERED)) {
            WorldPainter right = painter(pose, collector, light, palette, reader, null, NO_HITS);
            Shapes.pageFrame(right, frame, PW, PH, staticRight, true);
            deckle(right, reader, 0, PW, true);
            if (sketch) {
                Shapes.foreEdge(right, PW, 1, PH - 1, true, LEAVES_SHOWN);
                sketch(right, true);
            } else {
                printed(right, reader, staticRight, () -> {
                    if (staticRight != null) reader.drawRevealed(right, staticRight, flip == null ? reader.spot : flip.staticRight(), true);
                });
                reader.chrome(right, flip == null ? reader.spot : flip.staticRight(), true, false, null);
            }
            right.end();
        }
        if (!sketch && pages) {
            WorldPainter tabs = painter(pose, collector, light, palette, reader, null, NO_HITS);
            tabs(tabs, reader);
            tabs.end();
        }
        if (flip != null && angle < Mth.HALF_PI) shadow(pose, collector, light, angle, false);
        pose.popPose();
        spine(pose, collector, light, reader, rightAngle, leftAngle);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(leftAngle));
        body(pose, collector, light, reader, true);
        cover(pose, collector, light, reader);
        paper(pose, collector, light, palette, reader.book.textures().page(), -PW, 0, 0, PH, 0, !(staticLeft instanceof FoliosPageElement.SpreadHalf));
        if (pages && !(flip != null && angle > Mth.PI - COVERED)) {
            pose.pushPose();
            pose.translate(-PW, 0, 0);
            WorldPainter left = painter(pose, collector, light, palette, reader, null, NO_HITS);
            Shapes.pageFrame(left, frame, PW, PH, staticLeft, false);
            deckle(left, reader, 0, PW, false);
            Shapes.foreEdge(left, 0, 1, PH - 1, false, LEAVES_SHOWN);
            if (sketch) sketch(left, false);
            else {
                printed(left, reader, staticLeft, () -> {
                    if (staticLeft != null) reader.drawRevealed(left, staticLeft, flip == null ? reader.spot : flip.staticLeft(), false);
                });
                reader.chrome(left, flip == null ? reader.spot : flip.staticLeft(), false, false, null);
            }
            left.end();
            pose.popPose();
        }
        if (flip != null && angle > Mth.HALF_PI) shadow(pose, collector, light, angle, true);
        pose.popPose();
        if (flip != null && pages) leaf(pose, collector, light, palette, reader, flip, fold + angle * (Mth.PI - 2 * fold) / Mth.PI, sketch);
        pose.popPose();
        NO_HITS.clear();
    }

    static boolean isSketched(FoliosBook book) {
        return !book.style().equals(net.ixdarklord.folios.api.client.FoliosStyles.HANDHELD);
    }

    private static final float SKETCH_FOLD = 0.42F;

    private static boolean item(PoseStack pose, SubmitNodeCollector collector, int light, LocalPlayer player, HandheldBook book) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(book.hand != null ? book.hand : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof net.ixdarklord.folios.api.FoliosBookItem)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        pose.pushPose();
        pose.scale(1 / ITEM_FIT, 1 / ITEM_FIT, 1 / ITEM_FIT);
        minecraft.getItemRenderer().renderStatic(player, stack, net.minecraft.world.item.ItemDisplayContext.NONE, false, pose, collector.buffers(), minecraft.level,
                light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        return true;
    }

    private static final float ITEM_FIT = 0.625F / ((PW + EDGE) * SCALE);
    private static final float ITEM_DEPTH = ITEM_FIT;

    private static Matrix4f inHand(HandheldBook book, LocalPlayer player) {
        HumanoidArm arm = book.hand == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
        float invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        return new Matrix4f().translate(invert * 0.56F, -0.52F, -0.72F)
                .translate(invert * -1.5F / 16.0F, 5.2F / 16.0F, 1.8F / 16.0F)
                .rotate(new Quaternionf().rotationXYZ(-12 * Mth.DEG_TO_RAD, invert * -22 * Mth.DEG_TO_RAD, invert * 5 * Mth.DEG_TO_RAD))
                .scale(0.4F * ITEM_FIT, 0.4F * ITEM_FIT, 0.4F * ITEM_DEPTH);
    }

    private static Matrix4f blend(Matrix4f from, Matrix4f to, float t, float arc) {
        Vector3f place = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), t).add(0, arc, 0);
        Quaternionf turn = from.getUnnormalizedRotation(new Quaternionf()).slerp(to.getUnnormalizedRotation(new Quaternionf()), t);
        Vector3f size = from.getScale(new Vector3f()).lerp(to.getScale(new Vector3f()), t);
        return new Matrix4f().translation(place).rotate(turn).scale(size);
    }

    private static final int BAR_GAP = 5;
    private static final int SCREEN_BORDER = net.ixdarklord.folios.internal.client.screen.FoliosScreen.BORDER;

    private static void bar(PoseStack pose, SubmitNodeCollector collector, int light, HandheldBook book, Matrix4f toClip) {
        book.barHits.clear();
        book.barPlaced = false;
        FoliosBar bar = book.bar;
        if (bar == null || !book.isOpen()) {
            book.openSince = 0;
            return;
        }
        long now = net.ixdarklord.folios.internal.client.FoliosClock.millis();
        if (book.openSince == 0) book.openSince = now;
        float appear = net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings.transitions() ? easeOut(Math.min(1, (now - book.openSince) / 220.0F)) : 1;
        pose.pushPose();
        int wide = net.ixdarklord.folios.internal.client.screen.FoliosScreen.BOOK_WIDTH;
        pose.translate(-wide / 2.0F, -SCREEN_BORDER - BAR_GAP - FoliosBar.HEIGHT, 0);
        book.barClip.set(toClip).mul(pose.last().pose());
        WorldPainter painter = new WorldPainter(pose, collector, light, bar.palette(), book.session, book, book.seconds(),
                book.barPointer[0], book.barPointer[1], book.barHits).lightAs(PAGE_LIGHT);
        bar.draw(painter, wide, appear);
        painter.end();
        mergeTooltip(book, painter);
        pose.popPose();
        book.barPlaced = true;
    }

    public static boolean drawingHands;

    private static void hands(PoseStack pose, SubmitNodeCollector collector, int light, LocalPlayer player, float open) {
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        float halfWide = Mth.lerp(Mth.clamp(open, 0, 1), (PW + EDGE) / 2.0F, PW + EDGE) * SCALE;
        float halfTall = (PH / 2.0F + EDGE) * SCALE;
        for (HumanoidArm arm : HumanoidArm.values()) {
            float invert = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            pose.pushPose();
            pose.translate(invert * (halfWide * 0.58F - 0.306F), -halfTall + 0.38F, -(leaves + board) * SCALE);
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(92.0F));
            pose.mulPose(Axis.XP.rotationDegrees(45.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(invert * -41.0F));
            pose.translate(invert * 0.3F, -1.1F, 0.45F);
            drawingHands = true;
            try {
                if (arm == HumanoidArm.RIGHT) renderer.renderRightHand(pose, collector.buffers(), light, player);
                else renderer.renderLeftHand(pose, collector.buffers(), light, player);
            } finally {
                drawingHands = false;
            }
            pose.popPose();
        }
    }

    private static void leaf(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, FoliosReader book, FoliosReader.Flip flip, float angle) {
        leaf(pose, collector, light, palette, book, flip, angle, false);
    }

    private static void leaf(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, FoliosReader book, FoliosReader.Flip flip, float angle,
                             boolean sketch) {
        FoliosPageElement.Laid face = flip.forward() ? flip.oldRight() : flip.newRight();
        FoliosPageElement.Laid back = flip.forward() ? flip.newLeft() : flip.oldLeft();
        float upright = Mth.sin(angle);
        boolean edgeOn = Math.abs(Mth.cos(angle)) < 0.09F;
        boolean faceShown = angle < Mth.HALF_PI;
        float settle = Mth.clamp(Math.min(angle, Mth.PI - angle) / COVERED, 0, 1);
        float lift = 0.05F + (WorldPainter.HEIGHT + 0.2F) * settle * settle * (3 - 2 * settle) + upright * 2.0F;
        FoliosPageElement.Laid shown = faceShown ? face : back;
        boolean bend = book.book.pageTurn() == FoliosBook.PageTurn.BEND;
        Vector3f facing = new Vector3f(PAGE_LIGHT);
        pose.pushPose();
        pose.translate(0, 0, lift);
        pose.mulPose(Axis.YP.rotation(-angle));
        WorldPainter.Surface surface = null;
        if (bend) {
            surface = new Curl((flip.forward() ? -1 : 1) * BEND * upright, !faceShown);
        } else if (!faceShown) {
            pose.mulPose(Axis.YP.rotation(Mth.PI));
            pose.translate(-PW, 0, 0);
        }
        WorldPainter painter = new WorldPainter(pose, collector, light, palette, book.session, book, book.seconds(), Float.NaN, Float.NaN, NO_HITS, surface)
                .lightAs(facing);
        painter.paper(0, 0, PW, PH, palette.paper(), book.book.textures().page(), FOLD, shown instanceof FoliosPageElement.SpreadHalf ? FoliosPalette.mix(palette.paper(), foldShade(palette), SPREAD_FOLD) : foldShade(palette), !faceShown);
        if (!edgeOn) {
            Shapes.pageFrame(painter, book.book.textures().frame(), PW, PH, shown, faceShown);
            if (sketch) sketch(painter, faceShown);
            else {
                printed(painter, book, shown, () -> {
                    if (shown != null) book.drawRevealed(painter, shown, faceShown ? flip.face() : flip.back(), faceShown);
                });
                book.chrome(painter, faceShown ? flip.face() : flip.back(), faceShown, false, null);
            }
        }
        painter.end();
        pose.popPose();
        NO_HITS.clear();
    }

    private static final float COVERED = 0.14F;
    private static final float BEND = 1.15F;

    private static final class Curl implements WorldPainter.Surface {
        private static final int SAMPLES = 24;
        private static final float STEP = PW / (float) SAMPLES;
        private final float[] xs = new float[SAMPLES + 1];
        private final float[] zs = new float[SAMPLES + 1];
        private final float[] turns = new float[SAMPLES + 1];
        private final boolean back;

        Curl(float bend, boolean back) {
            this.back = back;
            float x = 0;
            float z = 0;
            for (int sample = 0; sample <= SAMPLES; sample++) {
                xs[sample] = x;
                zs[sample] = z;
                float along = sample / (float) SAMPLES;
                turns[sample] = bend * along * along;
                float middle = (sample + 0.5F) / SAMPLES;
                float turn = bend * middle * middle;
                x += STEP * Mth.cos(turn);
                z += STEP * Mth.sin(turn);
            }
        }

        @Override
        public void place(float x, float y, float z, Vector3f out) {
            float s = back ? PW - x : x;
            float up = back ? -z : z;
            float turn;
            float baseX;
            float baseZ;
            if (s <= 0 || s >= PW) {
                int end = s <= 0 ? 0 : SAMPLES;
                turn = turns[end];
                float past = s <= 0 ? s : s - PW;
                baseX = xs[end] + past * Mth.cos(turn);
                baseZ = zs[end] + past * Mth.sin(turn);
            } else {
                float at = s / STEP;
                int sample = Math.min(SAMPLES - 1, (int) at);
                float t = at - sample;
                turn = Mth.lerp(t, turns[sample], turns[sample + 1]);
                baseX = Mth.lerp(t, xs[sample], xs[sample + 1]);
                baseZ = Mth.lerp(t, zs[sample], zs[sample + 1]);
            }
            out.set(baseX - up * Mth.sin(turn), y, baseZ + up * Mth.cos(turn));
        }

        @Override
        public float step() {
            return STEP;
        }

        @Override
        public void axes(float x, Vector3f along, Vector3f out) {
            float s = Mth.clamp(back ? PW - x : x, 0, PW);
            float at = s / STEP;
            int sample = Math.min(SAMPLES - 1, (int) at);
            float turn = Mth.lerp(at - sample, turns[sample], turns[sample + 1]);
            float sign = back ? -1 : 1;
            along.set(sign * Mth.cos(turn), 0, sign * Mth.sin(turn));
            out.set(-sign * Mth.sin(turn), 0, sign * Mth.cos(turn));
        }
    }

    private static final ResourceLocation LEATHER = new ResourceLocation("folios", "textures/item/guide_book/leather.png");

    private static final List<AbstractPainter.Hit> NO_HITS = new ArrayList<>();

    private static final float HANDLED = 2.0F;

    private static final int LEAF_EDGE = 0xFFE3D6B6;

    private static void deckle(WorldPainter painter, FoliosReader book, int x0, int x1, boolean right) {
        int behind = FoliosPalette.mix(LEAF_EDGE, 0xFF5A4028, 0.62F);
        Shapes.wornEdges(painter, x0, 0, x1, PH, right, behind, HANDLED);
        Shapes.deckle(painter, x0, x1, 0, false, behind, HANDLED);
        Shapes.deckle(painter, x0, x1, PH - 1, true, behind, HANDLED);
    }

    private static void printed(WorldPainter painter, FoliosReader book, FoliosPageElement.@Nullable Laid page, Runnable draw) {
        FoliosLayout layout = book.session.layout();
        if (layout.pageWidth() <= PW) {
            draw.run();
            return;
        }
        painter.printing(true);
        painter.push();
        if (page instanceof FoliosPageElement.SpreadHalf) painter.scale(PW / (float) layout.pageWidth());
        else {
            float across = FoliosLayout.CONTENT_WIDTH / (float) layout.contentWidth();
            float down = (FoliosLayout.CONTENT_BOTTOM - FoliosLayout.CONTENT_TOP) / (float) (layout.contentBottom() - FoliosLayout.CONTENT_TOP);
            float scale = Math.min(across, down);
            painter.translate(FoliosLayout.CONTENT_LEFT + (FoliosLayout.CONTENT_WIDTH - layout.contentWidth() * scale) / 2, FoliosLayout.CONTENT_TOP);
            painter.scale(scale);
            painter.translate(-FoliosLayout.CONTENT_LEFT, -FoliosLayout.CONTENT_TOP);
        }
        draw.run();
        painter.pop();
        painter.printing(false);
    }

    private static WorldPainter painter(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, FoliosReader book,
                                        float @Nullable [] pointer, List<AbstractPainter.Hit> hits) {
        float x = pointer != null ? pointer[0] : Float.NaN;
        float y = pointer != null ? pointer[1] : Float.NaN;
        return new WorldPainter(pose, collector, light, palette, book.session, book, book.seconds(), x, y, hits).lightAs(PAGE_LIGHT);
    }

    private static void mergeTooltip(HandheldBook book, WorldPainter painter) {
        if (painter.tooltipLines() != null) book.tooltip = painter.tooltipLines();
        if (painter.popup() != null) book.popup = painter.popup();
    }

    private static void shadow(PoseStack pose, SubmitNodeCollector collector, int light, float angle, boolean left) {
        float strength = Mth.sin(angle);
        float width = PW * (0.15F + 0.35F * strength);
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        int paper = 0xFFF1E7D0;
        int dark = FoliosPalette.mix(paper, 0xFF5A4630, 0.35F * strength);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WorldPainter.WHITE), (last, consumer) -> {
            int steps = 8;
            for (int step = 0; step < steps; step++) {
                float from = width * step / steps;
                float to = width * (step + 1) / steps;
                int color = FoliosPalette.mix(dark, paper, step / (float) steps);
                if (left) quad(consumer, matrix, last, -to, 0, -from, PH, 0.01F, color, light);
                else quad(consumer, matrix, last, from, 0, to, PH, 0.01F, color, light);
            }
        });
    }

    private static float easeOut(float t) {
        t = Mth.clamp(t, 0, 1);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    private static float easeInOut(float t) {
        t = Mth.clamp(t, 0, 1);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    private static final int TAB_SMALL = 18;
    private static final float HOVER_OUT = 0.55F;

    private static void tabs(WorldPainter painter, FoliosReader book) {
        Shapes.foreEdge(painter, PW, 1, PH - 1, true, LEAVES_SHOWN);
        int gap = book.book.layout().tabSpacing();
        int x = PW - 1 + book.book.layout().tabX();
        int foot = PH - 4;
        foot = tab(painter, book, x, foot, true, null, "index".equals(book.spot.chapter()), "index", 7) - gap;
        if (!book.memory.bookmarks().isEmpty()) foot = tab(painter, book, x, foot, true, null, "bookmarks".equals(book.spot.chapter()), "bookmarks", 6) - gap;
        int y = book.book.layout().tabY();
        int order = 0;
        y = tab(painter, book, x, y, false, null, "home".equals(book.spot.chapter()), "home", order++) + gap;
        for (FoliosCategory category : book.book.visibleCategories()) {
            if (y + TAB_HEIGHT > foot) break;
            if (!category.isOpen()) continue;
            y = tab(painter, book, x, y, false, category, ("category:" + category.id()).equals(book.spot.chapter()) || isIn(book, category), "category", order++) + gap;
        }
    }

    private static boolean isIn(FoliosReader book, FoliosCategory category) {
        if (!book.spot.chapter().startsWith("entry:")) return false;
        var entry = book.book.entry(book.spot.chapter().substring(6));
        return entry != null && entry.category().equals(category.id());
    }

    private static Component tabName(@Nullable FoliosCategory category, String part) {
        return category != null ? category.title() : switch (part) {
            case "bookmarks" -> Component.translatableWithFallback("folios.bookmarks", "Bookmarks");
            case "index" -> Component.translatableWithFallback("folios.index", "Index");
            default -> Component.translatableWithFallback("folios.introduction", "Introduction");
        };
    }

    private static int tab(WorldPainter painter, FoliosReader book, int x, int y, boolean fromFoot, @Nullable FoliosCategory category, boolean here, String part, int order) {
        FoliosPalette palette = painter.palette();
        Component name = tabName(category, part);
        String key = FoliosReader.tabKey(category, part);
        Runnable go = category != null ? () -> book.category(category.id()) : switch (part) {
            case "bookmarks" -> book::bookmarks;
            case "index" -> book::index;
            default -> book::home;
        };
        int start = Math.max(x, PW + LEAVES_SHOWN + 1);
        boolean over = painter.action(x, fromFoot ? y - TAB_SMALL : y, start - x + 38, TAB_SMALL, name, () -> {
            book.tabs.press(key);
            go.run();
        });
        float moved = book.tabs.out(key, over, here);
        float pressed = book.tabs.pressed(key);
        float grow = Mth.clamp((moved - HOVER_OUT) / (1 - HOVER_OUT), 0, 1);
        int tall = Math.round(Mth.lerp(grow, TAB_SMALL, TAB_HEIGHT));
        int top = fromFoot ? y - tall : y;
        int depth = Shapes.tabDepth(Math.min(order, 6), moved, LEAVES_SHOWN);
        int length = Math.round(24 + 15 * moved - 4 * pressed) - depth;
        int color = FoliosPalette.mix(book.tabColor(category, palette, moved), 0xFF000000, 0.15F * pressed + 0.06F * depth);
        int root = Shapes.ribbonRoot(PW, depth);
        painter.cutout(book.book.textures().tab(), root, top, start - root + length, tall, 0, 0, 1, 1, color);
        Shapes.tuck(painter, PW, top, tall, depth, color);
        int plate = Math.round(Mth.lerp(grow, 14, 18));
        int icon = plate - 2;
        float plateX = start + 1 + (length * 0.8F - 1 - plate) / 2.0F;
        float plateY = top + (tall - plate) / 2.0F;
        painter.push();
        painter.translate(plateX, plateY);
        painter.cutout(book.book.textures().tabPlate(), 0, 0, plate, plate, 0, 0, 1, 1, palette.paper() | 0xFF000000);
        painter.translate(1, 1);
        if (part.equals("bookmarks")) FoliosBar.pixels(painter, FoliosBar.STAR, 0, 0, icon, palette.accent() | 0xFF000000);
        else if (part.equals("index")) FoliosBar.pixels(painter, FoliosBar.INDEX, 0, 0, icon, palette.accent() | 0xFF000000);
        else {
            painter.scale(icon / 16.0F);
            if (category != null) category.icon().draw(painter, 0, 0);
            else book.book.icon().draw(painter, 0, 0);
        }
        painter.pop();
        if (over) painter.tooltip(net.ixdarklord.folios.internal.client.FoliosTooltips.of(name, book.tabDetail(category, part)));
        return fromFoot ? top : top + tall;
    }

    private static void body(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader book, boolean left) {
        int cover = 0xFF000000 | book.book.cover();
        int edge = LEAF_EDGE;
        float sign = left ? -1 : 1;
        float x0 = left ? -PW - EDGE : 0;
        float x1 = left ? 0 : PW + EDGE;
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WorldPainter.WHITE), (last, consumer) -> {
            box(consumer, matrix, last, x0, -EDGE, -leaves - board, x1, PH + EDGE, -leaves, cover, light, false);
            float lx0 = left ? -PW : 0;
            float lx1 = left ? 0 : PW;
            quad(consumer, matrix, last, lx0, 0.5F, lx1, PH - 0.5F, -0.05F, edge, light);
            float inner = left ? lx1 : lx0;
            face(consumer, matrix, last, inner, 0.5F, -leaves, inner, PH - 0.5F, -0.05F, FoliosPalette.mix(edge, 0xFF000000, 0.18F), light);
        });
        float fx0 = left ? -PW : 0;
        float fx1 = left ? 0 : PW;
        float outer = left ? fx0 : fx1;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(PAGES), (last, consumer) -> {
            texFace(consumer, matrix, last, outer, 0.5F, -leaves, outer, PH - 0.5F, -0.05F, light, 0, 0, 1, 1);
            texFace(consumer, matrix, last, fx0, 0.5F, -leaves, fx1, 0.5F, -0.05F, light, 0, 0, 1, 1);
            texFace(consumer, matrix, last, fx0, PH - 0.5F, -leaves, fx1, PH - 0.5F, -0.05F, light, 0, 0, 1, 1);
        });
        boolean own = !book.book.textures().cover().equals(FoliosBook.Textures.DEFAULT.cover());
        ResourceLocation leather = own ? book.book.textures().cover() : LEATHER;
        Vector3f facing = net.ixdarklord.folios.internal.compat.Compat.transformNormal(pose.last(), 0, 0, -1, new Vector3f());
        if (left && relight != null) relight.transform(facing).normalize();
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(leather), (last, consumer) -> {
            float u0 = own ? (left ? 0 : 0.5F) : left ? 13 / 16.0F : 3 / 16.0F;
            float u1 = own ? u0 + 0.5F : left ? 3 / 16.0F : 13 / 16.0F;
            float top = own ? 0 : 1 / 16.0F;
            float foot = own ? 1 : 15 / 16.0F;
            quad(consumer, matrix, last, x0, -EDGE, x1, PH + EDGE, -leaves + 0.25F, cover, light, u0, top, u1, foot);
            for (float[] corner : new float[][]{{x0, -EDGE, u0, top}, {x1, -EDGE, u1, top}, {x1, PH + EDGE, u1, foot}, {x0, PH + EDGE, u0, foot}}) {
                Vector3f at = matrix.transformPosition(corner[0], corner[1], -leaves - board - 0.25F, new Vector3f());
                consumer.vertex(at.x, at.y, at.z).color(cover).uv(corner[2], corner[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(facing.x, facing.y, facing.z).endVertex();
            }
        });
        ResourceLocation ornaments = book.book.models().cover();
        CoverModel model = ornaments != null ? CoverModel.get(ornaments) : null;
        if (model != null) model.draw(pose, collector, light, left, PW + EDGE, -EDGE, PH + EDGE, -leaves - board - 0.26F, cover,
                0xFF000000 | book.book.accent(), opened, left ? relight : null, net.ixdarklord.folios.internal.client.FoliosLibrary.coverIcon(book.book.id()),
                net.ixdarklord.folios.internal.client.FoliosLibrary.coverIconSize(book.book.id()));
    }

    private static final float SPINE_OUT = 0.75F * (PW + EDGE) / 10;
    private static final int SPINE_STEPS = 12;

    private static void spine(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader book, float rightAngle, float leftAngle) {
        int cover = 0xFF000000 | book.book.cover();
        boolean own = !book.book.textures().cover().equals(FoliosBook.Textures.DEFAULT.cover());
        ResourceLocation leather = own ? book.book.textures().cover() : LEATHER;
        float u0 = own ? 0 : 0, u1 = own ? 0.1F : 1.5F / 16;
        float v0 = own ? 0 : 1 / 16.0F, v1 = own ? 1 : 15 / 16.0F;
        float reach = leaves + board + 0.25F;
        float[][] at = new float[SPINE_STEPS + 1][4];
        float middle = (rightAngle + leftAngle) / 2;
        float outX = -Mth.sin(middle), outZ = -Mth.cos(middle);
        float ax = -reach * Mth.sin(rightAngle), az = -reach * Mth.cos(rightAngle);
        float bx = -reach * Mth.sin(leftAngle), bz = -reach * Mth.cos(leftAngle);
        float mx = (ax + bx) / 2, mz = (az + bz) / 2;
        float shut = Mth.clamp((leftAngle - rightAngle) / Mth.PI, 0, 1);
        float out = SPINE_OUT * shut + 1.5F;
        for (int step = 0; step <= SPINE_STEPS; step++) {
            float t = step / (float) SPINE_STEPS * Mth.PI;
            float across = Math.signum(Mth.cos(t)) * (float) Math.pow(Math.abs(Mth.cos(t)), 0.35);
            float bulge = (float) Math.pow(Math.max(0, Mth.sin(t)), 0.35);
            at[step][0] = mx + (ax - mx) * across + outX * out * bulge;
            at[step][1] = mz + (az - mz) * across + outZ * out * bulge;
        }
        for (int step = 0; step <= SPINE_STEPS; step++) {
            float[] before = at[Math.max(0, step - 1)];
            float[] after = at[Math.min(SPINE_STEPS, step + 1)];
            float dx = after[0] - before[0], dz = after[1] - before[1];
            float nx = dz, nz = -dx;
            if (nx * outX + nz * outZ < 0) {
                nx = -nx;
                nz = -nz;
            }
            float length = Mth.sqrt(nx * nx + nz * nz);
            at[step][2] = length > 0 ? nx / length : outX;
            at[step][3] = length > 0 ? nz / length : outZ;
        }
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(leather), (last, consumer) -> {
            for (int step = 0; step < SPINE_STEPS; step++) {
                float[] a = at[step], b = at[step + 1];
                float ua = u0 + (u1 - u0) * step / SPINE_STEPS, ub = u0 + (u1 - u0) * (step + 1) / SPINE_STEPS;
                spineVertex(consumer, matrix, last, a, -EDGE, ua, v0, cover, light, 1);
                spineVertex(consumer, matrix, last, a, PH + EDGE, ua, v1, cover, light, 1);
                spineVertex(consumer, matrix, last, b, PH + EDGE, ub, v1, cover, light, 1);
                spineVertex(consumer, matrix, last, b, -EDGE, ub, v0, cover, light, 1);
            }
        });
        ResourceLocation ornaments = book.book.models().cover();
        CoverModel model = ornaments != null ? CoverModel.get(ornaments) : null;
        if (model == null) return;
        float unit = (PW + EDGE) / 16.0F;
        float height = PH + 2 * EDGE;
        for (CoverModel.Band band : model.bands()) {
            int color = band.tint() == 0 ? cover : band.tint() == 1 ? 0xFF000000 | book.book.accent() : 0xFFFFFFFF;
            float top = PH + EDGE - band.y1() / 16 * height;
            float foot = PH + EDGE - band.y0() / 16 * height;
            float lift = band.out() * unit + 0.3F;
            float[][] off = new float[SPINE_STEPS + 1][4];
            for (int step = 0; step <= SPINE_STEPS; step++) {
                float[] p = at[step];
                off[step] = new float[]{p[0] + p[2] * lift, p[1] + p[3] * lift, p[2], p[3]};
            }
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(band.texture()), (last, consumer) -> {
                for (int step = 0; step < SPINE_STEPS; step++) {
                    float[] a = off[step], b = off[step + 1];
                    float ua = band.u0() + (band.u1() - band.u0()) * step / SPINE_STEPS;
                    float ub = band.u0() + (band.u1() - band.u0()) * (step + 1) / SPINE_STEPS;
                    spineVertex(consumer, matrix, last, a, top, ua, band.v0(), color, light, 1);
                    spineVertex(consumer, matrix, last, a, foot, ua, band.v1(), color, light, 1);
                    spineVertex(consumer, matrix, last, b, foot, ub, band.v1(), color, light, 1);
                    spineVertex(consumer, matrix, last, b, top, ub, band.v0(), color, light, 1);
                    for (float y : new float[]{top, foot}) {
                        float[] in0 = at[step], in1 = at[step + 1];
                        Vector3f normal = net.ixdarklord.folios.internal.compat.Compat.transformNormal(last, 0, y == top ? -1 : 1, 0, new Vector3f());
                        float[][] edge = y == top ? new float[][]{in0, a, b, in1} : new float[][]{in1, b, a, in0};
                        for (float[] corner : edge) {
                            Vector3f position = matrix.transformPosition(corner[0], y, corner[1], new Vector3f());
                            consumer.vertex(position.x, position.y, position.z).color(FoliosPalette.mix(color, 0xFF000000, 0.2F))
                                    .uv(ua, band.v0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal.x, normal.y, normal.z).endVertex();
                        }
                    }
                }
            });
        }
    }

    private static void spineVertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float[] at, float y, float u, float v, int color,
                                    int light, float side) {
        Vector3f position = matrix.transformPosition(at[0], y, at[1], new Vector3f());
        Vector3f normal = net.ixdarklord.folios.internal.compat.Compat.transformNormal(normals, at[2] * side, 0, at[3] * side, new Vector3f());
        consumer.vertex(position.x, position.y, position.z).color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal.x, normal.y, normal.z).endVertex();
    }

    private static final float SPREAD_FOLD = 0.35F;
    private static final float FOLD = 9;

    private static int foldShade(FoliosPalette palette) {
        return FoliosPalette.mix(palette.paper(), 0xFF7A6448, 0.18F);
    }

    private static final Vector3f PAGE_LIGHT = new Vector3f(0, 0, 1);

    private static void paper(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, ResourceLocation texture, float x0, float y0, float x1, float y1, float z,
                              boolean folded) {
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        int paper = palette.paper();
        int shade = folded ? foldShade(palette) : FoliosPalette.mix(paper, foldShade(palette), SPREAD_FOLD);
        boolean left = x0 < 0;
        float span = x1 - x0;
        float fold = FOLD;
        float bandFrom = left ? x1 - fold : x0;
        float bandTo = left ? x1 : x0 + fold;
        float restFrom = left ? x0 : x0 + fold;
        float restTo = left ? x1 - fold : x1;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (lying, consumer) -> {
            PoseStack.Pose last = net.ixdarklord.folios.internal.compat.Compat.copy(lying);
            last.normal().identity();
            quad(consumer, matrix, last, restFrom, y0, restTo, y1, z, paper, light, (restFrom - x0) / span, 0, (restTo - x0) / span, 1);
            int inner = left ? paper : shade;
            int outer = left ? shade : paper;
            float u0 = (bandFrom - x0) / span;
            float u1 = (bandTo - x0) / span;
            vertex(consumer, matrix, last, bandFrom, y1, z, u0, 1, inner, light);
            vertex(consumer, matrix, last, bandTo, y1, z, u1, 1, outer, light);
            vertex(consumer, matrix, last, bandTo, y0, z, u1, 0, outer, light);
            vertex(consumer, matrix, last, bandFrom, y0, z, u0, 0, inner, light);
        });
    }

    private static void cover(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader book) {
        pose.pushPose();
        pose.translate(0, 0, -leaves - board - 0.26F);
        pose.mulPose(Axis.YP.rotation(Mth.PI));
        int cover = 0xFF000000 | book.book.cover();
        ResourceLocation texture = book.book.coverTexture();
        if (texture != null) {
            Matrix4f matrix = new Matrix4f(pose.last().pose());
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (last, consumer) ->
                    quad(consumer, matrix, last, -EDGE, -EDGE, PW + EDGE, PH + EDGE, 0, 0xFFFFFFFF, light, 0, 0, 1, 1));
        }
        int gilt = FoliosPalette.mix(0xFFE8C66A, cover, 0.15F);
        FoliosPalette ink = new FoliosPalette(cover, gilt, FoliosPalette.mix(gilt, cover, 0.4F), gilt, gilt, gilt, cover, gilt);
        pose.translate(0, 0, PLATE_FACE);
        Vector3f facing = net.ixdarklord.folios.internal.compat.Compat.transformNormal(pose.last(), 0, 0, 1, new Vector3f());
        if (relight != null) relight.transform(facing).normalize();
        WorldPainter painter = new WorldPainter(pose, collector, light, ink, book.session, book, book.seconds(), Float.NaN, Float.NaN, new ArrayList<>())
                .lightAs(facing);
        net.ixdarklord.folios.internal.client.CoverMarkers.Markers marked = net.ixdarklord.folios.internal.client.CoverMarkers.of(ITEM_MODEL);
        for (net.ixdarklord.folios.internal.client.CoverMarkers.Lettering lettering : net.ixdarklord.folios.internal.client.CoverMarkers.layout(book.book, marked,
                FoliosBook.CoverText.TITLE, FoliosBook.CoverText.SUBTITLE, 0)) {
            if (lettering.kind() == net.ixdarklord.folios.internal.client.CoverMarkers.Kind.RULE) coverRule(painter, lettering, gilt);
            else coverLines(painter, lettering, net.ixdarklord.folios.internal.client.CoverMarkers.color(lettering.kind(), gilt, cover));
        }
        painter.end();
        pose.popPose();
    }

    private static final int BLOCK = 4;

    private static void sketch(WorldPainter painter, boolean right) {
        FoliosPalette palette = painter.palette();
        int paper = palette.paper() | 0xFF000000;
        int ink = FoliosPalette.mix(paper, palette.text(), 0.34F);
        int faint = FoliosPalette.mix(paper, palette.text(), 0.2F);
        int accent = FoliosPalette.mix(paper, palette.accent(), 0.55F);
        int x0 = 16;
        int x1 = PW - 16;
        int y = 20;
        if (!right) {
            int wide = 10 * BLOCK;
            int hx = PW / 2 - wide / 2;
            scribble(painter, hx, hx + wide, y, 5, ink, ink, BLOCK + 2);
            y += 3 * BLOCK;
            painter.fill(x0 + BLOCK, y, PW / 2 - BLOCK, y + BLOCK / 2, accent);
            painter.fill(PW / 2 + BLOCK, y, x1 - BLOCK, y + BLOCK / 2, accent);
            painter.fill(PW / 2 - BLOCK / 2, y - BLOCK / 2, PW / 2 + BLOCK / 2, y + BLOCK, accent);
            y += 3 * BLOCK;
        }
        int line = 0;
        while (y < PH - 30) {
            int h = mix(line * 73 + (right ? 31 : 7));
            boolean last = (h & 3) == 0 || line % 4 == 3;
            int span = (x1 - x0) / BLOCK;
            int blocks = last ? span * (35 + (h >>> 2) % 40) / 100 : span - (h >>> 2) % 3;
            scribble(painter, x0, x0 + blocks * BLOCK, y, line * 131 + (right ? 977 : 0), ink, faint, BLOCK);
            y += last ? 5 * BLOCK : 3 * BLOCK;
            line++;
        }
        painter.fill(PW / 2 - BLOCK, PH - 18, PW / 2 + BLOCK, PH - 18 + BLOCK / 2, faint);
    }

    private static void scribble(WorldPainter painter, int x0, int x1, int y, int seed, int ink, int faint, int tall) {
        int x = x0;
        int letter = 0;
        while (x + BLOCK <= x1) {
            int word = 2 + mix(seed + letter * 7) % 4;
            for (int at = 0; at < word && x + BLOCK <= x1; at++, letter++) {
                int h = mix(seed * 31 + letter);
                int color = (h >>> 3) % 6 == 0 ? faint : ink;
                painter.fill(x, y, x + BLOCK, y + tall, color);
                if ((h >>> 5) % 5 == 0) painter.fill(x, y - BLOCK / 2, x + BLOCK, y, color);
                x += BLOCK;
            }
            x += BLOCK;
        }
    }

    private static int mix(int value) {
        int h = value * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & 0x7FFFFFFF;
    }

    private static final ResourceLocation ITEM_MODEL = new ResourceLocation("folios", "item/guide_book_3d");

    private static final float PLATE_FACE = 1.4F;

    private static void coverRule(WorldPainter painter, net.ixdarklord.folios.internal.client.CoverMarkers.Lettering lettering, int gilt) {
        FoliosBook.CoverText.Line line = lettering.line();
        float across = (PW + EDGE) / 10.0F;
        float up = (PH + 2 * EDGE) / 14.0F;
        float[][] runs = net.ixdarklord.folios.internal.client.CoverMarkers.rule(line);
        int cx = Math.round((line.x() - 3) * across);
        int cy = Math.round(-EDGE + (15 - line.y()) * up);
        int thick = Math.max(1, Math.round(line.size() * up));
        for (int index = 0; index < runs.length - 1; index++) {
            float[] run = runs[index];
            int x0 = cx + Math.round(run[0] * across);
            int x1 = cx + Math.round(run[1] * across);
            if (x1 > x0) painter.fill(x0, cy, x1, cy + thick, FoliosPalette.alpha(gilt, Math.round(255 * run[2])));
        }
        int stud = Math.max(2, Math.round(runs[runs.length - 1][0] * up));
        for (int row = -stud; row <= stud; row++) {
            int half = stud - Math.abs(row);
            painter.fill(cx - half, cy + row, cx + half + 1, cy + row + 1, gilt | 0xFF000000);
        }
    }

    static List<net.minecraft.network.chat.Component> plateText(HandheldBook book, float[] at) {
        if (Float.isNaN(at[0])) return List.of();
        float across = (PW + EDGE) / 10.0F;
        float up = (PH + 2 * EDGE) / 14.0F;
        net.ixdarklord.folios.internal.client.CoverMarkers.Kind plate = net.ixdarklord.folios.internal.client.CoverMarkers.plateAt(
                net.ixdarklord.folios.internal.client.CoverMarkers.of(ITEM_MODEL), 3 + at[0] / across, 15 - (at[1] + EDGE) / up);
        return plate == null ? List.of() : net.ixdarklord.folios.internal.client.CoverMarkers.plateText(book.book, plate);
    }

    private static void coverLines(WorldPainter painter, net.ixdarklord.folios.internal.client.CoverMarkers.Lettering lettering, int color) {
        FoliosBook.CoverText.Line line = lettering.line();
        float across = (PW + EDGE) / 10.0F;
        float up = (PH + 2 * EDGE) / 14.0F;
        net.ixdarklord.folios.internal.client.CoverMarkers.Rows rows = net.ixdarklord.folios.internal.client.CoverMarkers.rows(painter.font(), lettering);
        painter.push();
        painter.translate((line.x() - 3) * across, -EDGE + (15 - line.y()) * up);
        painter.scale(rows.scale() * up);
        for (int index = 0; index < rows.lines().size(); index++) {
            FormattedCharSequence row = rows.lines().get(index);
            painter.text(row, -painter.font().width(row) / 2, Math.round(rows.top()) + index * 9, color);
        }
        painter.pop();
    }

    private static void box(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        box(consumer, matrix, normals, x0, y0, z0, x1, y1, z1, color, light, true);
    }

    private static void box(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light,
                            boolean back) {
        int front = color;
        int side = FoliosPalette.mix(color, 0xFF000000, 0.18F);
        int under = FoliosPalette.mix(color, 0xFF000000, 0.35F);
        quad(consumer, matrix, normals, x0, y0, x1, y1, z1, front, light);
        if (back) quad(consumer, matrix, normals, x0, y0, x1, y1, z0, under, light);
        face(consumer, matrix, normals, x0, y0, z0, x1, y0, z1, side, light);
        face(consumer, matrix, normals, x0, y1, z0, x1, y1, z1, under, light);
        face(consumer, matrix, normals, x0, y0, z0, x0, y1, z1, side, light);
        face(consumer, matrix, normals, x1, y0, z0, x1, y1, z1, side, light);
    }

    private static final ResourceLocation PAGES = new ResourceLocation("folios", "textures/item/guide_book/pages.png");

    private static void texFace(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float z0, float x1, float y1, float z1, int light,
                                float u0, float v0, float u1, float v1) {
        int white = 0xFFFFFFFF;
        if (x0 == x1) {
            vertex(consumer, matrix, normals, x0, y0, z0, u0, v0, white, light);
            vertex(consumer, matrix, normals, x0, y1, z0, u1, v0, white, light);
            vertex(consumer, matrix, normals, x0, y1, z1, u1, v1, white, light);
            vertex(consumer, matrix, normals, x0, y0, z1, u0, v1, white, light);
        } else {
            vertex(consumer, matrix, normals, x0, y0, z0, u0, v0, white, light);
            vertex(consumer, matrix, normals, x1, y0, z0, u1, v0, white, light);
            vertex(consumer, matrix, normals, x1, y0, z1, u1, v1, white, light);
            vertex(consumer, matrix, normals, x0, y0, z1, u0, v1, white, light);
        }
    }

    private static void face(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        if (x0 == x1) {
            vertex(consumer, matrix, normals, x0, y0, z0, 0, 0, color, light);
            vertex(consumer, matrix, normals, x0, y1, z0, 0, 1, color, light);
            vertex(consumer, matrix, normals, x0, y1, z1, 1, 1, color, light);
            vertex(consumer, matrix, normals, x0, y0, z1, 1, 0, color, light);
        } else {
            vertex(consumer, matrix, normals, x0, y0, z0, 0, 0, color, light);
            vertex(consumer, matrix, normals, x1, y0, z0, 1, 0, color, light);
            vertex(consumer, matrix, normals, x1, y0, z1, 1, 1, color, light);
            vertex(consumer, matrix, normals, x0, y0, z1, 0, 1, color, light);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float x1, float y1, float z, int color, int light) {
        quad(consumer, matrix, normals, x0, y0, x1, y1, z, color, light, 0, 0, 1, 1);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float x1, float y1, float z, int color, int light,
                             float u0, float v0, float u1, float v1) {
        quad(consumer, matrix, normals, x0, y0, x1, y1, z, color, light, u0, v0, u1, v1, 1);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float x1, float y1, float z, int color, int light,
                             float u0, float v0, float u1, float v1, float nz) {
        vertex(consumer, matrix, normals, x0, y1, z, u0, v1, color, light, nz);
        vertex(consumer, matrix, normals, x1, y1, z, u1, v1, color, light, nz);
        vertex(consumer, matrix, normals, x1, y0, z, u1, v0, color, light, nz);
        vertex(consumer, matrix, normals, x0, y0, z, u0, v0, color, light, nz);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x, float y, float z, float u, float v, int color, int light) {
        vertex(consumer, matrix, normals, x, y, z, u, v, color, light, 1);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x, float y, float z, float u, float v, int color, int light, float nz) {
        Vector3f at = matrix.transformPosition(x, y, z, new Vector3f());
        consumer.vertex(at.x, at.y, at.z).color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normals.normal(), 0, 0, nz).endVertex();
    }

    static float @Nullable [] onPage(Matrix4f clip, double mouseX, double mouseY, int width, int height) {
        float ndcX = (float) (mouseX / width * 2 - 1);
        float ndcY = (float) (1 - mouseY / height * 2);
        Matrix4f inverse = clip.invert(new Matrix4f());
        Vector3f near = inverse.transformProject(ndcX, ndcY, 0.0F, new Vector3f());
        Vector3f far = inverse.transformProject(ndcX, ndcY, 0.5F, new Vector3f());
        float dz = far.z - near.z;
        if (Math.abs(dz) < 1.0E-6F) return null;
        float t = -near.z / dz;
        return new float[]{near.x + (far.x - near.x) * t, near.y + (far.y - near.y) * t};
    }
}
