package net.ixdarklord.folios.internal.client.build;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.ixdarklord.folios.internal.client.paint.WorldPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.ixdarklord.coolcatcanvas.api.client.vfx.GhostBlocks;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.ixdarklord.folios.internal.compat.RenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.ixdarklord.folios.internal.compat.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuildGuide {
    private static final int MOST = 512;
    private static final double WANDER = 48;

    private static @Nullable BuildGuide current;

    private record Part(BlockPos offset, BlockChoice choice) {}

    private static final class Need {
        final BlockChoice choice;
        int count;

        Need(BlockChoice choice) {
            this.choice = choice;
        }
    }

    private final Component name;
    private final @Nullable net.ixdarklord.folios.api.client.FoliosBook book;
    private final List<Part> parts = new ArrayList<>();
    private final BlockPos size;
    private @Nullable BlockPos anchor;
    private BlockPos preview = BlockPos.ZERO;
    private Rotation rotation = Rotation.NONE;
    private boolean useHeld = true;
    private boolean handled;
    private int age;
    private boolean[] done = new boolean[0];
    private boolean[] wrong = new boolean[0];
    private int placed;
    private int layer;
    private final Map<Object, Need> needs = new LinkedHashMap<>();

    private BuildGuide(Component name, Map<BlockPos, BlockChoice> blocks) {
        this.name = name;
        this.book = reading();
        int maxX = 0, maxY = 0, maxZ = 0;
        for (Map.Entry<BlockPos, BlockChoice> block : blocks.entrySet()) {
            if (block.getValue().options().isEmpty()) continue;
            parts.add(new Part(block.getKey(), block.getValue()));
            maxX = Math.max(maxX, block.getKey().getX());
            maxY = Math.max(maxY, block.getKey().getY());
            maxZ = Math.max(maxZ, block.getKey().getZ());
        }
        this.size = new BlockPos(maxX + 1, maxY + 1, maxZ + 1);
        this.done = new boolean[parts.size()];
        this.wrong = new boolean[parts.size()];
    }

    public static void start(Component name, Map<BlockPos, BlockChoice> blocks) {
        BuildGuide guide = new BuildGuide(name, blocks);
        if (guide.parts.isEmpty()) return;
        current = guide;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.8F));
    }

    private static @Nullable net.ixdarklord.folios.api.client.FoliosBook reading() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof net.ixdarklord.folios.internal.client.screen.FoliosScreen screen) return screen.reader().book;
        net.ixdarklord.folios.internal.client.handheld.HandheldBook held = net.ixdarklord.folios.internal.client.handheld.HandheldBook.current();
        return held != null ? held.book : null;
    }

    public static boolean isActive() {
        return current != null;
    }

    public static void stop() {
        current = null;
    }

    private BlockPos origin() {
        return anchor != null ? anchor : preview;
    }

    private BlockPos at(Part part) {
        BlockPos centred = new BlockPos(part.offset.getX() - size.getX() / 2, part.offset.getY(), part.offset.getZ() - size.getZ() / 2);
        return origin().offset(centred.rotate(rotation));
    }

    public static void tick(Minecraft minecraft) {
        BuildGuide guide = current;
        if (guide == null) return;
        if (minecraft.player == null || minecraft.level == null) {
            current = null;
            return;
        }
        guide.age++;
        boolean use = minecraft.options.keyUse.isDown();
        boolean pressed = use && !guide.useHeld && !guide.handled;
        guide.useHeld = use;
        guide.handled = false;
        if (pressed && minecraft.screen == null && minecraft.player.getMainHandItem().isEmpty() && minecraft.player.getOffhandItem().isEmpty()) {
            guide.use(minecraft.player.isShiftKeyDown());
            if (current == null) return;
        }
        if (guide.anchor == null) {
            HitResult hit = minecraft.hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                guide.preview = block.getBlockPos().relative(block.getDirection());
            } else {
                Vec3 ahead = minecraft.player.getEyePosition().add(minecraft.player.getLookAngle().scale(4));
                guide.preview = BlockPos.containing(ahead);
            }
            guide.rotation = rotation(minecraft.player.getDirection());
        }
        if (guide.age % 10 == 0 || guide.anchor == null) guide.look(minecraft);
        if (guide.anchor == null) return;
        if (guide.placed >= guide.parts.size()) {
            current = null;
            SystemToast.add(minecraft.getToasts(), new SystemToast.SystemToastId(),
                    Component.translatableWithFallback("folios.build.done", "Build complete: %s", guide.name), null);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4F, 0.5F));
            return;
        }
        if (minecraft.player.position().distanceTo(Vec3.atCenterOf(guide.anchor)) > WANDER) {
            current = null;
            minecraft.player.displayClientMessage(Component.translatableWithFallback("folios.build.left", "You left the build behind"), true);
        }
    }

    public static boolean onUse(boolean sneaking) {
        BuildGuide guide = current;
        if (guide == null || Minecraft.getInstance().screen != null || !sneaking && guide.anchor != null) return false;
        guide.handled = true;
        guide.use(sneaking);
        return true;
    }

    private void use(boolean sneaking) {
        Minecraft minecraft = Minecraft.getInstance();
        if (sneaking) {
            current = null;
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatableWithFallback("folios.build.cancelled", "Build guide put away"), true);
        } else if (anchor == null) {
            anchor = preview;
            age = 0;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F));
        }
    }

    private static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private void look(Minecraft minecraft) {
        placed = 0;
        needs.clear();
        layer = Integer.MAX_VALUE;
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            BlockState there = minecraft.level.getBlockState(this.at(part));
            boolean right = part.choice.matches(there);
            done[index] = right;
            wrong[index] = !right && !there.isAir() && !there.canBeReplaced();
            if (right) {
                placed++;
            } else {
                int items = part.choice.items();
                if (items > 0) needs.computeIfAbsent(part.choice.isTag() ? part.choice.key() : part.choice.stack(0, 1).getItem(),
                        key -> new Need(part.choice)).count += items;
                layer = Math.min(layer, part.offset.getY());
            }
        }
    }

    public static void render(Camera camera, float partialTick) {
        BuildGuide guide = current;
        Minecraft minecraft = Minecraft.getInstance();
        if (guide == null || minecraft.level == null) return;
        float time = (guide.age + partialTick) / 20.0F;
        float breath = 0.5F + 0.5F * Mth.sin(time * 4.0F);
        boolean carried = guide.anchor == null;
        Vec3 eye = camera.getPosition();
        List<GhostBlocks.Placed> now = new ArrayList<>();
        List<GhostBlocks.Placed> later = new ArrayList<>();
        List<float[]> boxes = new ArrayList<>();
        int drawn = 0;
        for (int index = 0; index < guide.parts.size() && drawn < MOST; index++) {
            Part part = guide.parts.get(index);
            if (!carried && guide.done[index]) continue;
            BlockPos place = guide.at(part);
            drawn++;
            if (!carried && guide.wrong[index]) {
                float x = (float) (place.getX() - eye.x);
                float y = (float) (place.getY() - eye.y);
                float z = (float) (place.getZ() - eye.z);
                boxes.add(new float[]{x - 0.01F, y - 0.01F, z - 0.01F, x + 1.01F, y + 1.01F, z + 1.01F, Float.intBitsToFloat(0x70E04040)});
                continue;
            }
            BlockState state = part.choice.shown(time);
            if (state == null) continue;
            GhostBlocks.Placed ghost = new GhostBlocks.Placed(place, state.rotate(guide.rotation));
            if (carried || part.offset.getY() == guide.layer) now.add(ghost);
            else later.add(ghost);
        }
        GhostBlocks.draw(later, 0x30C8E4FF, GhostBlocks.Show.IN_WORLD, true);
        int clear = carried ? 0x90 : Math.round(0x70 + 0x30 * breath);
        GhostBlocks.draw(now, (clear << 24) | 0xE0F0FF, GhostBlocks.Show.IN_WORLD, true);
        if (boxes.isEmpty()) return;
        PoseStack pose = new PoseStack();
        pose.mulPose(new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf())));
        SubmitNodeCollector collector = SubmitNodeCollector.of(minecraft.renderBuffers().bufferSource());
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WorldPainter.WHITE), (last, consumer) -> {
            for (float[] box : boxes) box(consumer, matrix, last, box, Float.floatToRawIntBits(box[6]));
        });
        minecraft.renderBuffers().bufferSource().endBatch();
    }

    private static void box(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, float[] b, int color) {
        float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        face(consumer, matrix, normals, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(consumer, matrix, normals, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(consumer, matrix, normals, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face(consumer, matrix, normals, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(consumer, matrix, normals, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(consumer, matrix, normals, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void face(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normals, int color, float... corners) {
        for (int at = 0; at < 12; at += 3) {
            org.joml.Vector3f point = matrix.transformPosition(corners[at], corners[at + 1], corners[at + 2], new org.joml.Vector3f());
            consumer.addVertex(point.x, point.y, point.z).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(normals, 0, 1, 0);
        }
    }

    private static final int PAD = 4;
    private static final int ROW = 20;
    private static final int MOST_ROWS = 5;
    private static final int MARGIN = 8;

    public static void hud(GuiGraphicsExtractor graphics) {
        BuildGuide guide = current;
        Minecraft minecraft = Minecraft.getInstance();
        if (guide == null || minecraft.options.hideGui) return;
        Font font = minecraft.font;
        net.ixdarklord.folios.api.client.FoliosBook book = guide.book;
        int cover = book != null ? 0xFF000000 | book.cover() : 0xFF3B2618;
        int gilt = book != null ? net.ixdarklord.folios.internal.client.FoliosTooltips.titleColor(book) : 0xFFF2E6CC;
        int frame = book != null ? 0xFF000000 | net.ixdarklord.folios.internal.client.FoliosTooltips.frameColor(book) : 0xFFE8C66A;
        int rule = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(frame, cover, 0.45F);
        int quiet = book != null ? net.ixdarklord.folios.internal.client.FoliosTooltips.detailColor(book)
                : net.ixdarklord.folios.api.client.page.FoliosPalette.mix(gilt, cover, 0.45F);
        int total = guide.parts.size();
        float progress = total == 0 ? 0 : guide.placed / (float) total;
        Component title = guide.name.copy().withStyle(ChatFormatting.BOLD);
        Component count = Component.literal(guide.placed + " / " + total);
        Component hint = guide.anchor == null
                ? Component.translatableWithFallback("folios.build.place", "Right-click to set it here")
                : Component.translatableWithFallback("folios.build.cancel", "Sneak + right-click to put away");
        int rows = Math.min(MOST_ROWS, guide.needs.size());
        int more = guide.needs.size() - rows;
        int wide = Math.max(130, font.width(title) + 10 + font.width(count));
        int row = 0;
        float time = guide.age / 20.0F;
        for (Need need : guide.needs.values()) {
            if (row++ >= rows) break;
            wide = Math.max(wide, 20 + font.width(need.choice.name(time)) + 10 + font.width("×" + need.count));
        }
        wide = Math.min(Math.max(wide, font.width(hint)), graphics.guiWidth() / 3);
        int tall = 9 + 4 + 6 + 4 + rows * ROW + (more > 0 ? 10 : 0) + 4 + 9;
        int inset = 13;
        var corner = net.ixdarklord.folios.internal.config.FoliosClientConfig.BUILD_PANEL_CORNER.get();
        boolean right = corner == net.ixdarklord.folios.internal.config.FoliosClientConfig.Corner.TOP_RIGHT
                || corner == net.ixdarklord.folios.internal.config.FoliosClientConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = corner == net.ixdarklord.folios.internal.config.FoliosClientConfig.Corner.BOTTOM_LEFT
                || corner == net.ixdarklord.folios.internal.config.FoliosClientConfig.Corner.BOTTOM_RIGHT;
        int offsetX = net.ixdarklord.folios.internal.config.FoliosClientConfig.BUILD_PANEL_X.get();
        int offsetY = net.ixdarklord.folios.internal.config.FoliosClientConfig.BUILD_PANEL_Y.get();
        int edge = inset - MARGIN;
        int x = right ? graphics.guiWidth() - edge - wide - offsetX : edge + offsetX;
        int y = bottom ? graphics.guiHeight() - edge - tall - offsetY : edge + offsetY;
        if (book != null) net.ixdarklord.folios.internal.client.FoliosTooltips.frame(graphics, x, y, wide, tall, book, 255, inset);
        else graphics.fill(x - 4, y - 4, x + wide + 4, y + tall + 4, 0xE0201810);
        int nameRoom = wide - 8 - font.width(count);
        graphics.text(font, fit(font, title, nameRoom), x, y, gilt, true);
        graphics.text(font, count, x + wide - font.width(count), y, quiet, true);
        int at = y + 9 + 3;
        int barLeft = x + 1;
        int barRight = x + wide - 1;
        int outline = net.ixdarklord.folios.api.client.page.FoliosPalette.mix(frame, cover, 0.25F);
        progressBar(graphics, barLeft, at - 1, barRight - barLeft, progress, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(frame, cover, 0.45F));
        at += 6 + 4;
        row = 0;
        for (Need need : guide.needs.values()) {
            if (row++ >= rows) break;
            graphics.fill(x, at, x + 18, at + 18, outline);
            graphics.fill(x + 1, at + 1, x + 17, at + 17, 0xFF141010);
            graphics.fakeItem(need.choice.stack(time, 1), x + 1, at + 1);
            String times = "×" + need.count;
            int room = wide - 22 - font.width(times) - 4;
            fitted(graphics, font, need.choice.name(time), x + 22, at + 5, room, gilt);
            graphics.text(font, times, x + wide - font.width(times), at + 5, quiet, true);
            at += ROW;
        }
        if (more > 0) {
            Component also = Component.translatableWithFallback("folios.build.more", "and %s more", more).withStyle(ChatFormatting.ITALIC);
            graphics.text(font, fit(font, also, wide), x, at + 1, quiet, true);
            at += 10;
        }
        for (int px = 0; px < wide; px++) {
            float t = 1 - Math.abs(px + 0.5F - wide / 2.0F) / (wide / 2.0F);
            int alpha = Math.round(255 * t * t * (3 - 2 * t));
            if (alpha > 4) graphics.fill(x + px, at + 1, x + px + 1, at + 2, net.ixdarklord.folios.api.client.page.FoliosPalette.alpha(rule, alpha));
        }
        fitted(graphics, font, hint, x, at + 4, wide, quiet);
    }

    private static void progressBar(GuiGraphicsExtractor graphics, int x, int y, int width, float progress, int rim) {
        int groove = 0xFF141010;
        int x1 = x + width;
        int y1 = y + 7;
        graphics.fill(x + 2, y1, x1, y1 + 1, 0x50000000);
        graphics.fill(x1, y + 2, x1 + 1, y1, 0x50000000);
        graphics.fill(x1, y1, x1 + 1, y1 + 1, 0x28000000);
        graphics.fill(x + 1, y, x1 - 1, y + 1, rim);
        graphics.fill(x + 1, y1 - 1, x1 - 1, y1, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(rim, 0xFF000000, 0.3F));
        graphics.fill(x, y + 1, x + 1, y1 - 1, rim);
        graphics.fill(x1 - 1, y + 1, x1, y1 - 1, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(rim, 0xFF000000, 0.2F));
        graphics.fill(x + 1, y + 1, x1 - 1, y1 - 1, groove);
        graphics.fill(x + 1, y + 1, x1 - 1, y + 2, 0xFF0A0707);
        int inside = width - 2;
        int filled = Math.round(inside * Math.max(0, Math.min(1, progress)));
        for (int tenth = 1; tenth < 10; tenth++) {
            int at = x + 1 + inside * tenth / 10;
            graphics.fill(at, y1 - 3, at + 1, y1 - 1, 0xFF2A2420);
        }
        if (filled <= 0) return;
        int fill = progress >= 1 ? 0xFF6FBF5A : 0xFFE0B04C;
        int left = x + 1;
        int right = left + filled;
        graphics.fill(left, y + 1, right, y1 - 1, fill);
        graphics.fill(left, y + 1, right, y + 2, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(fill, 0xFFFFFFFF, 0.45F));
        graphics.fill(left, y + 2, right, y + 3, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(fill, 0xFFFFFFFF, 0.18F));
        graphics.fill(left, y1 - 2, right, y1 - 1, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(fill, 0xFF000000, 0.3F));
        for (int tenth = 1; tenth < 10; tenth++) {
            int at = left + inside * tenth / 10;
            if (at < right - 1) graphics.fill(at, y + 3, at + 1, y1 - 2, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(fill, 0xFF000000, 0.18F));
        }
        if (filled < inside && filled > 2) graphics.fill(right - 2, y + 2, right - 1, y1 - 2, net.ixdarklord.folios.api.client.page.FoliosPalette.mix(fill, 0xFFFFFFFF, 0.6F));
    }

    private static void fitted(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int room, int color) {
        int wide = font.width(text);
        if (wide <= room) {
            graphics.text(font, text, x, y, color, true);
            return;
        }
        float scale = Math.max(0.75F, room / (float) wide);
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = wide * scale <= room
                ? java.util.List.of(text.getVisualOrderText()) : font.split(text, Math.round(room / scale));
        if (lines.size() > 2) lines = lines.subList(0, 2);
        float height = lines.size() * 9 * scale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + 4 - height / 2 + (lines.size() > 1 ? 0.5F : 1));
        graphics.pose().scale(scale, scale);
        for (int index = 0; index < lines.size(); index++) graphics.text(font, lines.get(index), 0, index * 9, color, true);
        graphics.pose().popMatrix();
    }

    private static net.minecraft.util.FormattedCharSequence fit(Font font, Component text, int room) {
        if (font.width(text) <= room) return text.getVisualOrderText();
        net.minecraft.network.chat.FormattedText head = font.substrByWidth(text, Math.max(0, room - font.width("…")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(head, net.minecraft.network.chat.FormattedText.of("…")));
    }
}
