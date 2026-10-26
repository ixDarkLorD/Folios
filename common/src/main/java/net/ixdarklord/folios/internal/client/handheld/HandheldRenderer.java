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
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Draws the handheld book in place of the player's hands: two boards and two blocks of leaves hinged at the spine,
// the open spread on the leaves, a leaf in the air while one turns, ribbon tabs at the edge for the book's parts,
// and the cover with the book's name while it is shut. It is raised from below, opened, and shut and lowered again.
// Everything is in page pixels: x to the right from the spine, y down from the top edge, z towards the reader.
public final class HandheldRenderer {
    /** Blocks to a page pixel. */
    // The book's width in blocks kept as it was with a narrower rim (143 page pixels at 0.0024): its pages are smaller.
    static final float SCALE = 0.0024F * 143 / 149;
    private static final int PW = FoliosLayout.PAGE_WIDTH;
    private static final int PH = FoliosLayout.PAGE_HEIGHT;
    /** How thick a block of leaves is, how thick a board, and how far a board stands out past the leaves. */
    private static float leaves = 17.5F;
    // How the left board's normals turn back to where they lie shut, while it swings; null when not drawing it.
    private static org.joml.@Nullable Matrix3f relight;
    // How far the book being drawn is open, for its cover model's boxes shown only shut or only open.
    private static float opened = 1;
    private static float board = 10.5F;
    /** The book is as thick as the item, shut or open: its boards and leaves in the item's proportions. */
    private static final float LEAVES_SHUT = 17.5F;
    private static final float BOARD_SHUT = 10.5F;
    /** How far the boards stand out past the pages: as the item's covers do past its leaves, so its pages sit inset. */
    static final float EDGE = 13;
    private static final int TAB_HEIGHT = 22;
    /** How many leaves of the block show past a handheld book's pages, at its sides. */
    private static final int LEAVES_SHOWN = 1;

    private HandheldRenderer() {}

    /** The paper and ink of a handheld book's pages: parchment, or in dark mode paper gone dark, the ink pale on it. */
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

    /** Draws the book if one is held open; returns whether it was (the hands are then not drawn). */
    public static boolean draw(float partialTick, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light) {
        HandheldBook book = HandheldBook.current();
        if (book == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        float raise = book.raise(partialTick);
        float open = book.open(partialTick);
        // As thick as the item, shut or open: the same book in the hands as in the hotbar.
        leaves = LEAVES_SHUT;
        board = BOARD_SHUT;
        opened = open;
        // Its pages can always be read: never darker than by a lantern. Its boards, spine and cover take the world's
        // light as the shut book in the hand does, so it doesn't brighten as it opens in the dark.
        int lit = LightCoordsUtil.max(light, LightCoordsUtil.pack(11, 0));
        FoliosPalette palette = palette(book.book);
        book.leftHits.clear();
        book.rightHits.clear();
        book.tooltip = null;
        book.popup = null;
        book.tabs.frame();

        pose.pushPose();
        // Held steady: not swayed as vanilla sways the hands when the view turns (nor bobbed as the reader walks, see
        // GameRendererMixin).
        float seconds = book.seconds();
        boolean held = raise >= 1;
        // Where it is read: as far from the eyes as the book asks, in the middle of the view (the pages a little below
        // its middle, so the bar over them is in view too).
        float distance = book.book.distance();
        Matrix4f reading = new Matrix4f().translate(0, -0.02F * distance / 0.64F, -distance)
                .rotate(Axis.XP.rotationDegrees(-book.book.tilt()));
        // The hands come up from below to take it, and go down again as it is put back.
        if (book.book.models().hands() && !player.isInvisible() && raise > 0) {
            pose.pushPose();
            pose.mulPose(reading);
            pose.translate(0, -(1 - easeOut(raise)) * 0.8F, 0);
            hands(pose, collector, light, player, open);
            pose.popPose();
        }
        // Lifted out of the hand that holds it, from just where the item lies there, along a small arc to where it is
        // read; put away, it goes back the same way and lies in the hand as it was, so the item takes its place unseen.
        float along = easeInOut(raise);
        pose.mulPose(blend(inHand(book, player), reading, along, Mth.sin(along * Mth.PI) * 0.06F));
        // Shut, it is the book item itself, the very model held in the hand, so nothing about it changes as it is
        // raised, held up or put back; its own pages are drawn once its cover starts to open.
        boolean asItem = open <= 0.001F && item(pose, collector, light, player, book);
        pose.scale(SCALE, -SCALE, SCALE);
        // Open, the spine is in the middle of the view; shut, the cover is.
        float shutShift = (1 - Mth.clamp(open, 0, 1)) * (PW + EDGE) / 2.0F;
        pose.translate(-shutShift, -PH / 2.0F, 0.0F);

        CameraRenderState camera = minecraft.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Matrix4f projection = new Matrix4f().setPerspective(camera.hudFov * Mth.DEG_TO_RAD,
                minecraft.getWindow().getWidth() / (float) Math.max(1, minecraft.getWindow().getHeight()), 0.05F, 100.0F, RenderSystem.getDevice().isZZeroToOne());
        Matrix4f toClip = projection.mul(camera.viewRotationMatrix, new Matrix4f());

        boolean readable = book.isOpen();
        float turned = book.flipProgress();
        FoliosReader.Flip flip = book.flip;
        // The leaf's angle about the spine: 0 lying on the right, PI on the left.
        float angle = flip == null ? 0 : easeInOut(flip.forward() ? turned : 1 - turned) * Mth.PI;
        FoliosPageElement.Laid staticLeft = flip == null ? book.page(book.spot, 0) : flip.forward() ? flip.oldLeft() : flip.newLeft();
        FoliosPageElement.Laid staticRight = flip == null ? book.page(book.spot, 1) : flip.forward() ? flip.newRight() : flip.oldRight();
        boolean pressable = readable && flip == null;
        // A page under a leaf lying (almost) flat on it can't be seen, so it isn't drawn.
        boolean rightCovered = flip != null && angle < COVERED;
        boolean leftCovered = flip != null && angle > Mth.PI - COVERED;
        // The cover swings open in one smooth sweep, easing out of shut and into open with no swing past it; a screen
        // book's only part way, its halves left leaning up from the spine as a book held open in the hands is.
        // Its latch undone first (the strap and clasp swing off), then the cover; a screen book's halves lean up only as
        // the cover swings, so while it is still latched its right half lies flat under the cover.
        float swing = easeInOut(CoverModel.swing(open));
        float fold = book.sketch ? SKETCH_FOLD * swing : 0;
        float coverAngle = (1 - swing) * Mth.PI + fold;

        // The right half: its board, its leaves, and its page.
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
        // The tabs are the book's, not the page's: they stay while leaves turn, pressable only once none does.
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

        // The left half, swung over the right while the book is shut.
        pose.pushPose();
        // The cover is lit as it is when shut, as the item is, however far it has swung: its normals turned back by
        // the swing, so it doesn't darken as it turns from the light.
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
            // The fore-edge of the leaves past the left page.
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

        // Drawn now, while the view of the hands is in place, as vanilla does at the end of its hands.
        minecraft.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
        minecraft.renderBuffers().bufferSource().endBatch();
        return true;
    }

    /**
     * Draws a book lying open as someone else reads it, for those around to read along: the spread they have open,
     * leaves turning as they turn them, the tabs, nothing pressable; part open, as a book in the hands lies. A screen
     * book is drawn as its reader holds it before its screen, its pages a likeness of pages. In page pixels from the spine at the head, as the
     * reader's own is (the caller places it and flips y).
     */
    public static void drawShared(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader reader) {
        drawShared(pose, collector, light, reader, 1);
    }

    /**
     * As {@link #drawShared(PoseStack, SubmitNodeCollector, int, FoliosReader)}, {@code open} of the way open: 0 held
     * up shut (its cover to the reader, its strap on), then unlatched and opened, as the reader's own opens.
     */
    public static void drawShared(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader reader, float open) {
        leaves = LEAVES_SHUT;
        board = BOARD_SHUT;
        opened = open;
        FoliosPalette palette = palette(reader.book);
        boolean sketch = isSketched(reader.book);
        reader.tabs.frame();
        // A screen book's leaves turn too, as its reader turns them on their screen: a likeness of pages on each.
        float turned = reader.flipProgress();
        FoliosReader.Flip flip = reader.flip;
        float angle = flip == null ? 0 : easeInOut(flip.forward() ? turned : 1 - turned) * Mth.PI;
        FoliosPageElement.Laid staticLeft = flip == null ? reader.page(reader.spot, 0) : flip.forward() ? flip.oldLeft() : flip.newLeft();
        FoliosPageElement.Laid staticRight = flip == null ? reader.page(reader.spot, 1) : flip.forward() ? flip.newRight() : flip.oldRight();
        Identifier frame = reader.book.textures().frame();
        // Held in someone's hands, any book lies part open, its halves leaning up from the spine; shut, its left half
        // lies over its right, the shut book's middle where the spine of the open one is.
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
        // Its front board's outside faces those in front of the reader: its name and subtitle on it.
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
        // The leaf turns from one leaning half to the other, its sweep narrowed by the fold at each end.
        if (flip != null && pages) leaf(pose, collector, light, palette, reader, flip, fold + angle * (Mth.PI - 2 * fold) / Mth.PI, sketch);
        pose.popPose();
        NO_HITS.clear();
    }

    /** Whether a book is shown in the hands as a likeness, its own pages being on a screen: any but a handheld book. */
    static boolean isSketched(FoliosBook book) {
        return !book.style().equals(net.ixdarklord.folios.api.client.FoliosStyles.HANDHELD);
    }

    /** How far each half of a screen book stays folded up from flat as it is held open before its screen, in radians. */
    private static final float SKETCH_FOLD = 0.42F;

    // The book item in the hand that holds it, at the book's size (its middle at the pose's origin, its cover to the
    // reader); false when that hand no longer holds a book item.
    private static boolean item(PoseStack pose, SubmitNodeCollector collector, int light, LocalPlayer player, HandheldBook book) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(book.hand != null ? book.hand : InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof net.ixdarklord.folios.api.FoliosBookItem)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.renderer.item.ItemStackRenderState state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, net.minecraft.world.item.ItemDisplayContext.NONE, minecraft.level, player, 0);
        pose.pushPose();
        pose.scale(1 / ITEM_FIT, 1 / ITEM_FIT, 1 / ITEM_FIT);
        state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        return true;
    }

    /** How much bigger the item's book is than this one shut, across and through, in the item's own space. */
    // The item's front cover is 10 model units (0.625 of a block) across; the book's board is (PW + EDGE) page pixels.
    private static final float ITEM_FIT = 0.625F / ((PW + EDGE) * SCALE);
    private static final float ITEM_DEPTH = ITEM_FIT;

    // Where the book lies as the item in the hand, as vanilla places it there at rest (the arm's place, then the item
    // model's first-person transform, mirrored for the left hand), sized to the item's book: its middle at the
    // model's middle, its cover to the eyes and its spine to the left, as the item's are.
    private static Matrix4f inHand(HandheldBook book, LocalPlayer player) {
        HumanoidArm arm = book.hand == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
        float invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        // As models/item/guide_book_3d.json's "firstperson_righthand" has it (keep the two together), mirrored for the
        // left hand as the game mirrors it: its x and its turns about y and z the other way.
        return new Matrix4f().translate(invert * 0.56F, -0.52F, -0.72F)
                .translate(invert * -1.5F / 16.0F, 5.2F / 16.0F, 1.8F / 16.0F)
                .rotate(new Quaternionf().rotationXYZ(-12 * Mth.DEG_TO_RAD, invert * -22 * Mth.DEG_TO_RAD, invert * 5 * Mth.DEG_TO_RAD))
                .scale(0.4F * ITEM_FIT, 0.4F * ITEM_FIT, 0.4F * ITEM_DEPTH);
    }

    // A pose part way from one to another: places and sizes eased between, turns slerped, lifted by `arc` on the way.
    private static Matrix4f blend(Matrix4f from, Matrix4f to, float t, float arc) {
        Vector3f place = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), t).add(0, arc, 0);
        Quaternionf turn = from.getUnnormalizedRotation(new Quaternionf()).slerp(to.getUnnormalizedRotation(new Quaternionf()), t);
        Vector3f size = from.getScale(new Vector3f()).lerp(to.getScale(new Vector3f()), t);
        return new Matrix4f().translation(place).rotate(turn).scale(size);
    }

    /** How far the bar's foot is over the book's head, in page pixels. */
    private static final int BAR_GAP = 5;
    /** The screen book's cover rim round its pages, which its bar sits that gap over. */
    private static final int SCREEN_BORDER = net.ixdarklord.folios.internal.client.screen.FoliosScreen.BORDER;

    // The bar over the open book, as wide as the book, in its frame a little over its head: in the world, so a leaf
    // turning past it goes in front of it. It grows out of its pull once the book is fully open, and is gone as soon
    // as it starts to shut.
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
        // As far over the pages as the screen book's bar is over its own (its cover's rim, then the gap), and as wide,
        // centred on the spine.
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

    // The reader's hands under the book's lower corners, holding it as they hold a map in both hands, a little further
    // apart while it is open. In the book's frame before it is scaled to page pixels (blocks, y up), so they follow it
    // wherever it is held and however it leans.
    private static void hands(PoseStack pose, SubmitNodeCollector collector, int light, LocalPlayer player, float open) {
        AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
        Identifier skin = player.getSkin().body().texturePath();
        float halfWide = Mth.lerp(Mth.clamp(open, 0, 1), (PW + EDGE) / 2.0F, PW + EDGE) * SCALE;
        float halfTall = (PH / 2.0F + EDGE) * SCALE;
        for (HumanoidArm arm : HumanoidArm.values()) {
            float invert = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            pose.pushPose();
            // Vanilla's hands for a map, moved under this book's foot: they reach about a third of a block out from
            // where they are put, so they are put inside the corners, less far in as the book narrows to shut.
            pose.translate(invert * (halfWide * 0.58F - 0.306F), -halfTall + 0.38F, -(leaves + board) * SCALE);
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(92.0F));
            pose.mulPose(Axis.XP.rotationDegrees(45.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(invert * -41.0F));
            pose.translate(invert * 0.3F, -1.1F, 0.45F);
            if (arm == HumanoidArm.RIGHT) renderer.renderRightHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
            else renderer.renderLeftHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
            pose.popPose();
        }
    }

    // A leaf in the air: the page it leaves on its face, the page it brings on its back; only the side towards the
    // reader is drawn. It is lifted clear of everything on the pages under it, coming down onto a page only where the
    // page under it is wholly covered and so not drawn, so it settles without a jump. It is lit as the open pages
    // are, so it doesn't dim as it turns, and its writing is left out while it stands edge-on, where it could not be
    // read anyway. A flat leaf swings stiffly; a bending one curls, its free edge trailing as it lifts and following
    // as it comes down.
    private static void leaf(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, FoliosReader book, FoliosReader.Flip flip, float angle) {
        leaf(pose, collector, light, palette, book, flip, angle, false);
    }

    // `sketch`: the leaf of a screen book seen in its reader's hands, a likeness of a page on each side.
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
        // Lit as the open pages are, whichever way it faces as it turns.
        Vector3f facing = new Vector3f(PAGE_LIGHT);
        pose.pushPose();
        // Lifted in the book's own frame, then swung about the spine.
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
        // With the fold's shadow at its spine edge, as the pages it leaves and lands on have: the face's spine is at its
        // left (it is a right-hand page), the back's at its right.
        painter.paper(0, 0, PW, PH, palette.paper(), book.book.textures().page(), FOLD, shown instanceof FoliosPageElement.SpreadHalf ? FoliosPalette.mix(palette.paper(), foldShade(palette), SPREAD_FOLD) : foldShade(palette), !faceShown);
        if (!edgeOn) {
            Shapes.pageFrame(painter, book.book.textures().frame(), PW, PH, shown, faceShown);
            if (sketch) sketch(painter, faceShown);
            else {
                printed(painter, book, shown, () -> {
                    if (shown != null) book.drawRevealed(painter, shown, faceShown ? flip.face() : flip.back(), faceShown);
                });
                // Its number and arrow turn with it.
                book.chrome(painter, faceShown ? flip.face() : flip.back(), faceShown, false, null);
            }
        }
        painter.end();
        pose.popPose();
        NO_HITS.clear();
    }

    /** How near a leaf is to lying on a page (radians) when the page under it is no longer drawn. */
    private static final float COVERED = 0.14F;
    /** How far a bending leaf's free edge curls from its root at the most, in radians. */
    private static final float BEND = 1.15F;

    // A leaf bent along its length: the further from the spine, the more it is turned, by the square of the distance,
    // so it curls smoothly to its edge. Points are worked out at a few places along it and blended between them.
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

        // The leaf's own back is its page turned over: its x runs from the edge to the spine, its face the other way.
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

    /** The book item's leather (models/item/guide_book_3d.json), for the boards. */
    private static final Identifier LEATHER = Identifier.fromNamespaceAndPath("folios", "textures/item/guide_book/leather.png");

    private static final List<AbstractPainter.Hit> NO_HITS = new ArrayList<>();

    // A page's head and foot cut a little rough, the board showing in the bites.
    /** How worn a handheld book's page edges are (the screen book's are 1). */
    private static final float HANDLED = 2.0F;

    /** The colour of the leaves' block, edge on. */
    private static final int LEAF_EDGE = 0xFFE3D6B6;

    private static void deckle(WorldPainter painter, FoliosReader book, int x0, int x1, boolean right) {
        // Its nicks and tears show what is behind the page: the leaves under it, in the shadow of the page over them
        // (warm and dark enough to read as a tear, not the black of a hole).
        int behind = FoliosPalette.mix(LEAF_EDGE, 0xFF5A4028, 0.62F);
        // Worn with age at its head, foot and outer side, more than the screen book's: it is a book carried about.
        // First, so its yellowing doesn't cover the tears (on a page in the world, what is see-through is laid on solid).
        Shapes.wornEdges(painter, x0, 0, x1, PH, right, behind, HANDLED);
        Shapes.deckle(painter, x0, x1, 0, false, behind, HANDLED);
        Shapes.deckle(painter, x0, x1, PH - 1, true, behind, HANDLED);
    }

    // What is printed on a page: drawn at its pages' own size (larger than a leaf at a small GUI scale: PageSize), scaled
    // down onto the leaf, so more of it fits on a leaf of the same size. It keeps within the leaf's own margins, clear
    // of its frame as at the default size, as large as fits them; a spread's half spans the whole leaf, to meet its
    // other half at the fold. The painter keeps where the pointer is and what can be pressed in step with the scale.
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

    // The shadow a rising leaf casts on the page it uncovers, widest when the leaf stands upright.
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

    // ---- What surrounds the pages: numbers, arrows, tabs ----

    /** How tall a tab is at rest; the open one grows to TAB_HEIGHT. */
    private static final int TAB_SMALL = 18;
    /** How far out a tab is past which it grows: under the pointer it swings out, open it grows too. */
    private static final float HOVER_OUT = 0.55F;

    // Ribbon tabs out of the right edge: the front and each open category from the top, the bookmarks and the index at
    // the foot. At rest each is small; the open one grows as it opens. They are laid one under the other with the same
    // gap between each, whatever their sizes, so the column stays even as one grows. Under the pointer a tab only swings
    // further out, so nothing moves beneath it.
    private static void tabs(WorldPainter painter, FoliosReader book) {
        // The fore-edge of the leaves past the right page, the tabs coming out of it.
        Shapes.foreEdge(painter, PW, 1, PH - 1, true, LEAVES_SHOWN);
        int gap = book.book.layout().tabSpacing();
        // From between the leaves, at the page's edge.
        int x = PW - 1 + book.book.layout().tabX();
        // The foot first, from the bottom up: the index, then the bookmarks once there are any.
        int foot = PH - 4;
        foot = tab(painter, book, x, foot, true, null, "index".equals(book.spot.chapter()), "index", 7) - gap;
        if (!book.memory.bookmarks().isEmpty()) foot = tab(painter, book, x, foot, true, null, "bookmarks".equals(book.spot.chapter()), "bookmarks", 6) - gap;
        // Then from the top down, as many as fit above the foot.
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

    // A tab, its top at y (or, `fromFoot`, its foot): pressable (pressed, it is knocked in and springs out again, and its
    // part opens); it swings out under the pointer and further while its part is open, growing as it opens. Returns
    // its other edge.
    private static int tab(WorldPainter painter, FoliosReader book, int x, int y, boolean fromFoot, @Nullable FoliosCategory category, boolean here, String part, int order) {
        FoliosPalette palette = painter.palette();
        Component name = tabName(category, part);
        String key = FoliosReader.tabKey(category, part);
        Runnable go = category != null ? () -> book.category(category.id()) : switch (part) {
            case "bookmarks" -> book::bookmarks;
            case "index" -> book::index;
            default -> book::home;
        };
        // Where it shows clear of the sheets over its root.
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
        // Tucked deeper in the leaves the lower it is, it lies a little in their shade; open, it rises to the surface.
        int depth = Shapes.tabDepth(Math.min(order, 6), moved, LEAVES_SHOWN);
        int length = Math.round(24 + 15 * moved - 4 * pressed) - depth;
        int color = FoliosPalette.mix(book.tabColor(category, palette, moved), 0xFF000000, 0.15F * pressed + 0.06F * depth);
        // The book's ribbon texture, its swallowtail end at the tip, from its root under the page, in the tab's
        // colour; then the leaves over its root.
        int root = Shapes.ribbonRoot(PW, depth);
        painter.cutout(book.book.textures().tab(), root, top, start - root + length, tall, 0, 0, 1, 1, color);
        Shapes.tuck(painter, PW, top, tall, depth, color);
        // A plate centred on the ribbon's body (between the shade at its root and the bottom of its notch, a fifth
        // of its length), and the icon on it, a pixel inside it; both larger as it grows. All as large as the screen
        // book's tabs.
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
        // After the icon, so an item for an icon doesn't show its own tooltip in place of the tab's.
        if (over) painter.tooltip(net.ixdarklord.folios.internal.client.FoliosTooltips.of(name, book.tabDetail(category, part)));
        return fromFoot ? top : top + tall;
    }


    // ---- The book itself ----

    // A half of the book: its board and its block of leaves. `left` for the half on the left of the spine.
    private static void body(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader book, boolean left) {
        int cover = 0xFF000000 | book.book.cover();
        int edge = LEAF_EDGE;
        float sign = left ? -1 : 1;
        // The two boards meet at the spine, neither past it: overlapping, their faces would fight down the middle.
        float x0 = left ? -PW - EDGE : 0;
        float x1 = left ? 0 : PW + EDGE;
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WorldPainter.WHITE), (last, consumer) -> {
            // The board, and the spine's half under the fold.
            // Its outside is the leather face drawn over it below, not a darker face of the box.
            box(consumer, matrix, last, x0, -EDGE, -leaves - board, x1, PH + EDGE, -leaves, cover, light, false);
            // The leaves, a hair inside the board's edge: here only their face and the side at the spine (their back lies on the board); their other
            // sides are the worn edges below, laid where those sides are (both at once, they fought).
            float lx0 = left ? -PW : 0;
            float lx1 = left ? 0 : PW;
            quad(consumer, matrix, last, lx0, 0.5F, lx1, PH - 0.5F, -0.05F, edge, light);
            float inner = left ? lx1 : lx0;
            face(consumer, matrix, last, inner, 0.5F, -leaves, inner, PH - 0.5F, -0.05F, FoliosPalette.mix(edge, 0xFF000000, 0.18F), light);
        });
        // Their edges, worn: the item's page-edge texture (wavering sheets, yellowed, nicked) on the fore-edge, the head
        // and the foot, its sheets running along each.
        float fx0 = left ? -PW : 0;
        float fx1 = left ? 0 : PW;
        float outer = left ? fx0 : fx1;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(PAGES), (last, consumer) -> {
            texFace(consumer, matrix, last, outer, 0.5F, -leaves, outer, PH - 0.5F, -0.05F, light, 0, 0, 1, 1);
            texFace(consumer, matrix, last, fx0, 0.5F, -leaves, fx1, 0.5F, -0.05F, light, 0, 0, 1, 1);
            texFace(consumer, matrix, last, fx0, PH - 0.5F, -leaves, fx1, PH - 0.5F, -0.05F, light, 0, 0, 1, 1);
        });
        // The board's faces, inside and out: the item's own leather, laid on as the item lays it on its boards (its
        // middle ten texels across, fourteen down, the spine's side at the spine), so the book raised out of the hand
        // is the one that was in it. A book that names its own cover texture has that instead.
        boolean own = !book.book.textures().cover().equals(FoliosBook.Textures.DEFAULT.cover());
        Identifier leather = own ? book.book.textures().cover() : LEATHER;
        // The outside lit as if it faced the viewer whichever way it is swung, so it keeps the item's brightness as the
        // cover opens instead of darkening as it turns from the light.
        Vector3f facing = pose.last().transformNormal(0, 0, -1, new Vector3f());
        if (left && relight != null) relight.transform(facing).normalize();
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(leather), (last, consumer) -> {
            // Its own: half of it on each board. The leather: x0 is the outer edge on the left board, the spine on the right.
            float u0 = own ? (left ? 0 : 0.5F) : left ? 13 / 16.0F : 3 / 16.0F;
            float u1 = own ? u0 + 0.5F : left ? 3 / 16.0F : 13 / 16.0F;
            float top = own ? 0 : 1 / 16.0F;
            float foot = own ? 1 : 15 / 16.0F;
            quad(consumer, matrix, last, x0, -EDGE, x1, PH + EDGE, -leaves + 0.25F, cover, light, u0, top, u1, foot);
            // The outside: the cover's colour, as the item is tinted, its normal outwards so it is lit as the item's is.
            // Wound to face out from the board: the entity pass lights a face seen from behind by the opposite of its
            // normal, which darkened it.
            for (float[] corner : new float[][]{{x0, -EDGE, u0, top}, {x1, -EDGE, u1, top}, {x1, PH + EDGE, u1, foot}, {x0, PH + EDGE, u0, foot}}) {
                // Clearly outside the board's box (a hair out fights it at the hand's scale, darkening the cover).
                Vector3f at = matrix.transformPosition(corner[0], corner[1], -leaves - board - 0.25F, new Vector3f());
                consumer.addVertex(at.x, at.y, at.z).setColor(cover).setUv(corner[2], corner[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(facing.x, facing.y, facing.z);
            }
        });
        // What sits on the board's outside: corner caps, a clasp, bands over the spine.
        Identifier ornaments = book.book.models().cover();
        CoverModel model = ornaments != null ? CoverModel.get(ornaments) : null;
        if (model != null) model.draw(pose, collector, light, left, PW + EDGE, -EDGE, PH + EDGE, -leaves - board - 0.26F, cover,
                0xFF000000 | book.book.accent(), opened, left ? relight : null, net.ixdarklord.folios.internal.client.FoliosLibrary.coverIcon(book.book.id()),
                net.ixdarklord.folios.internal.client.FoliosLibrary.coverIconSize(book.book.id()));
    }

    /** How far the spine stands out past the boards when shut, as the item's does (three quarters of a unit). */
    private static final float SPINE_OUT = 0.75F * (PW + EDGE) / 10;
    /** How many pieces the spine's bend is drawn in. */
    private static final int SPINE_STEPS = 12;

    // The spine: one band of the item's leather, from the right board's back edge at the spine to the left one's, the
    // halves at those angles about it. Shut, it is the item's spine, a flat back standing a little out past the boards
    // with rounded shoulders; as the cover opens it bends with it, and open it is drawn in to a ridge behind the fold.
    private static void spine(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader book, float rightAngle, float leftAngle) {
        int cover = 0xFF000000 | book.book.cover();
        boolean own = !book.book.textures().cover().equals(FoliosBook.Textures.DEFAULT.cover());
        Identifier leather = own ? book.book.textures().cover() : LEATHER;
        // The item's spine strip of its leather: its first column and a half, its middle fourteen rows.
        float u0 = own ? 0 : 0, u1 = own ? 0.1F : 1.5F / 16;
        float v0 = own ? 0 : 1 / 16.0F, v1 = own ? 1 : 15 / 16.0F;
        float reach = leaves + board + 0.25F;
        // Where across the band each piece is: (x, z) about the spine, and its outward normal.
        float[][] at = new float[SPINE_STEPS + 1][4];
        float middle = (rightAngle + leftAngle) / 2;
        float outX = -Mth.sin(middle), outZ = -Mth.cos(middle);
        float ax = -reach * Mth.sin(rightAngle), az = -reach * Mth.cos(rightAngle);
        float bx = -reach * Mth.sin(leftAngle), bz = -reach * Mth.cos(leftAngle);
        float mx = (ax + bx) / 2, mz = (az + bz) / 2;
        // How far it stands out: the item's spine shut, little more than the leather open.
        float shut = Mth.clamp((leftAngle - rightAngle) / Mth.PI, 0, 1);
        float out = SPINE_OUT * shut + 1.5F;
        for (int step = 0; step <= SPINE_STEPS; step++) {
            float t = step / (float) SPINE_STEPS * Mth.PI;
            // A squared-off round: flat across its back, rounding at its shoulders.
            float across = Math.signum(Mth.cos(t)) * (float) Math.pow(Math.abs(Mth.cos(t)), 0.35);
            float bulge = (float) Math.pow(Math.max(0, Mth.sin(t)), 0.35);
            at[step][0] = mx + (ax - mx) * across + outX * out * bulge;
            at[step][1] = mz + (az - mz) * across + outZ * out * bulge;
        }
        for (int step = 0; step <= SPINE_STEPS; step++) {
            // Each point's normal: square to the band there, outwards.
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
                // One face, seen from both sides (these faces aren't culled): a second for its inside, in the same
                // place, fought it.
                spineVertex(consumer, matrix, last, a, -EDGE, ua, v0, cover, light, 1);
                spineVertex(consumer, matrix, last, a, PH + EDGE, ua, v1, cover, light, 1);
                spineVertex(consumer, matrix, last, b, PH + EDGE, ub, v1, cover, light, 1);
                spineVertex(consumer, matrix, last, b, -EDGE, ub, v0, cover, light, 1);
            }
        });
        // The cover model's bands round it, bending with it.
        Identifier ornaments = book.book.models().cover();
        CoverModel model = ornaments != null ? CoverModel.get(ornaments) : null;
        if (model == null) return;
        float unit = (PW + EDGE) / 16.0F;
        float height = PH + 2 * EDGE;
        for (CoverModel.Band band : model.bands()) {
            int color = band.tint() == 0 ? cover : band.tint() == 1 ? 0xFF000000 | book.book.accent() : 0xFFFFFFFF;
            float top = PH + EDGE - band.y1() / 16 * height;
            float foot = PH + EDGE - band.y0() / 16 * height;
            float lift = band.out() * unit + 0.3F;
            // Its outer face, a little off the spine all along it.
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
                    // Its head and foot edges, from the spine out to its face.
                    for (float y : new float[]{top, foot}) {
                        float[] in0 = at[step], in1 = at[step + 1];
                        Vector3f normal = last.transformNormal(0, y == top ? -1 : 1, 0, new Vector3f());
                        float[][] edge = y == top ? new float[][]{in0, a, b, in1} : new float[][]{in1, b, a, in0};
                        for (float[] corner : edge) {
                            Vector3f position = matrix.transformPosition(corner[0], y, corner[1], new Vector3f());
                            consumer.addVertex(position.x, position.y, position.z).setColor(FoliosPalette.mix(color, 0xFF000000, 0.2F))
                                    .setUv(ua, band.v0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x, normal.y, normal.z);
                        }
                    }
                }
            });
        }
    }

    private static void spineVertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float[] at, float y, float u, float v, int color,
                                    int light, float side) {
        Vector3f position = matrix.transformPosition(at[0], y, at[1], new Vector3f());
        Vector3f normal = normals.transformNormal(at[2] * side, 0, at[3] * side, new Vector3f());
        consumer.addVertex(position.x, position.y, position.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }

    /** How strong the fold's shadow is under a spread, of its usual strength. */
    private static final float SPREAD_FOLD = 0.35F;
    /** How wide the fold's shadow is on each page, from the spine. */
    private static final float FOLD = 9;

    private static int foldShade(FoliosPalette palette) {
        return FoliosPalette.mix(palette.paper(), 0xFF7A6448, 0.18F);
    }

    // A page's paper from the book's page texture, darkening smoothly into the fold of the spine. The shadow is in the
    // paper itself (its colour fades across a band of it), not laid over it: nothing else may lie in the paper's
    // plane, or it and the page's own first layer fight there.
    /**
     * The way the pages, their paper, tabs and items, and the bar over them are lit: as if facing the viewer, whichever way the book and the
     * camera turn, so nothing on them brightens or darkens as they do.
     */
    private static final Vector3f PAGE_LIGHT = new Vector3f(0, 0, 1);

    private static void paper(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, Identifier texture, float x0, float y0, float x1, float y1, float z,
                              boolean folded) {
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        int paper = palette.paper();
        // Fainter under a spread, which lies over both pages as one.
        int shade = folded ? foldShade(palette) : FoliosPalette.mix(paper, foldShade(palette), SPREAD_FOLD);
        boolean left = x0 < 0;
        float span = x1 - x0;
        float fold = FOLD;
        // The band by the spine, and the rest of the page beside it.
        float bandFrom = left ? x1 - fold : x0;
        float bandTo = left ? x1 : x0 + fold;
        float restFrom = left ? x0 : x0 + fold;
        float restTo = left ? x1 - fold : x1;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (lying, consumer) -> {
            // Lit as facing the viewer, as what is on it is.
            PoseStack.Pose last = lying.copy();
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

    // The cover, on the outside of the left board: seen while the book is shut, with the book's name on it.
    private static void cover(PoseStack pose, SubmitNodeCollector collector, int light, FoliosReader book) {
        pose.pushPose();
        pose.translate(0, 0, -leaves - board - 0.26F);
        pose.mulPose(Axis.YP.rotation(Mth.PI));
        int cover = 0xFF000000 | book.book.cover();
        Identifier texture = book.book.coverTexture();
        if (texture != null) {
            Matrix4f matrix = new Matrix4f(pose.last().pose());
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (last, consumer) ->
                    quad(consumer, matrix, last, -EDGE, -EDGE, PW + EDGE, PH + EDGE, 0, 0xFFFFFFFF, light, 0, 0, 1, 1));
        }
        int gilt = FoliosPalette.mix(0xFFE8C66A, cover, 0.15F);
        FoliosPalette ink = new FoliosPalette(cover, gilt, FoliosPalette.mix(gilt, cover, 0.4F), gilt, gilt, gilt, cover, gilt);
        // Lifted onto the face of the cover model's plates, which stand out from the board.
        pose.translate(0, 0, PLATE_FACE);
        Vector3f facing = pose.last().transformNormal(0, 0, 1, new Vector3f());
        if (relight != null) relight.transform(facing).normalize();
        WorldPainter painter = new WorldPainter(pose, collector, light, ink, book.session, book, book.seconds(), Float.NaN, Float.NaN, new ArrayList<>())
                .lightAs(facing);
        // Laid out as the item's cover is (FoliosCoverText), so the book raised out of the hand is the same book: its
        // name a quarter of the way down, its subtitle three quarters, each on one line shrunk to the room across,
        // the emblem between them (the cover model's).
        // Where the book says, in the item's units carried onto the board (its cover 3 to 13 across, 1 to 15 up).
        net.ixdarklord.folios.internal.client.CoverMarkers.Markers marked = net.ixdarklord.folios.internal.client.CoverMarkers.of(ITEM_MODEL);
        for (net.ixdarklord.folios.internal.client.CoverMarkers.Lettering lettering : net.ixdarklord.folios.internal.client.CoverMarkers.layout(book.book, marked,
                FoliosBook.CoverText.TITLE, FoliosBook.CoverText.SUBTITLE, 0)) {
            if (lettering.kind() == net.ixdarklord.folios.internal.client.CoverMarkers.Kind.RULE) coverRule(painter, lettering, gilt);
            else coverLines(painter, lettering, net.ixdarklord.folios.internal.client.CoverMarkers.color(lettering.kind(), gilt, cover));
        }
        painter.end();
        pose.popPose();
    }

    /** The size of a "pixel" of a screen book's likeness of a page, in page pixels: big, so it reads as pixel art. */
    private static final int BLOCK = 4;

    // A page of a screen book raised to be opened: not its writing, which the screen shows, but a pixel-art likeness of
    // a page of it, in big square pixels: a heading over a rule on the left page, then lines of made-up writing in
    // paragraphs on both, each line's words and length its own but the same every time.
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
            // The heading, a heavier line in the middle, and a rule with a diamond under it.
            int wide = 10 * BLOCK;
            int hx = PW / 2 - wide / 2;
            scribble(painter, hx, hx + wide, y, 5, ink, ink, BLOCK + 2);
            y += 3 * BLOCK;
            painter.fill(x0 + BLOCK, y, PW / 2 - BLOCK, y + BLOCK / 2, accent);
            painter.fill(PW / 2 + BLOCK, y, x1 - BLOCK, y + BLOCK / 2, accent);
            painter.fill(PW / 2 - BLOCK / 2, y - BLOCK / 2, PW / 2 + BLOCK / 2, y + BLOCK, accent);
            y += 3 * BLOCK;
        }
        // Lines of writing in paragraphs, ragged at their ends.
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
        // The page's number, a short dash at its foot.
        painter.fill(PW / 2 - BLOCK, PH - 18, PW / 2 + BLOCK, PH - 18 + BLOCK / 2, faint);
    }

    // One line of made-up writing from x0 to x1, its top at y, in big square pixels `tall` high: words of a few letters
    // a block each with a block's gap between words, now and then a letter with a stroke rising out of it, the odd
    // one fainter.
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

    /** The book item's model, whose marked lettering the cover follows (CoverMarkers). */
    private static final Identifier ITEM_MODEL = Identifier.fromNamespaceAndPath("folios", "item/guide_book_3d");

    /** How far the lettering on the cover stands out from the board: just over its plates' face, in page pixels. */
    private static final float PLATE_FACE = 1.4F;

    // One line of the cover's, centred across it at height y (its middle), as tall as `size` at most and no wider than
    // `room`.
    // The soft rule and its stud, as FoliosCoverText draws them on the item, carried onto the board (whole page pixels).
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
        // The stud: a small diamond, rows widening to its middle and narrowing again.
        int stud = Math.max(2, Math.round(runs[runs.length - 1][0] * up));
        for (int row = -stud; row <= stud; row++) {
            int half = stud - Math.abs(row);
            painter.fill(cx - half, cy + row, cx + half + 1, cy + row + 1, gilt | 0xFF000000);
        }
    }

    // A lettering on the board, laid out as FoliosCoverText lays it on the item (CoverMarkers.rows), carried onto the board.
    /**
     * What the cover's plate under a point says (the point in page pixels on the board, as the pointer is while the
     * book is held shut), placed as {@link #coverLines} letters it; nothing off its plates.
     */
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
        // An item unit across and up the board, in page pixels.
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

    // ---- Geometry ----

    private static void box(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        box(consumer, matrix, normals, x0, y0, z0, x1, y1, z1, color, light, true);
    }

    // `back` false leaves out the face away from the reader, for a box with another face laid over it there.
    private static void box(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light,
                            boolean back) {
        int front = color;
        int side = FoliosPalette.mix(color, 0xFF000000, 0.18F);
        int under = FoliosPalette.mix(color, 0xFF000000, 0.35F);
        // The face towards the reader, the one away, then the four sides.
        quad(consumer, matrix, normals, x0, y0, x1, y1, z1, front, light);
        if (back) quad(consumer, matrix, normals, x0, y0, x1, y1, z0, under, light);
        face(consumer, matrix, normals, x0, y0, z0, x1, y0, z1, side, light);
        face(consumer, matrix, normals, x0, y1, z0, x1, y1, z1, under, light);
        face(consumer, matrix, normals, x0, y0, z0, x0, y1, z1, side, light);
        face(consumer, matrix, normals, x1, y0, z0, x1, y1, z1, side, light);
    }

    /** The item's page edges: its sheets edge on, worn. */
    private static final Identifier PAGES = Identifier.fromNamespaceAndPath("folios", "textures/item/guide_book/pages.png");

    // A face flat in x or in y, between two corners, its texture laid on (u along it, v across, in z). One face only:
    // these aren't culled, so it shows from both sides, and a second in the same place fought it.
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

    // A face flat in x or in y, between two corners.
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

    // The same, its normal along z one way or the other (`nz` 1 towards the reader, -1 away).
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
        consumer.addVertex(at.x, at.y, at.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normals, 0, 0, nz);
    }

    // ---- The pointer ----

    /** Where a point of the screen (in GUI pixels) falls on a page, in page pixels; null when it misses the page's plane. */
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
