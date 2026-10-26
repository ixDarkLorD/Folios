package net.ixdarklord.folios.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.paint.WorldPainter;
import net.minecraft.client.Minecraft;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.ixdarklord.folios.internal.compat.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

// The book's icon set in its cover's gem (the box a cover model marks "#icon"): the gem's face covered by a dark inset
// of its own diamond shape, and the icon lying flat on it, a little narrower than the gem is from corner to corner, so
// the inset's diamond shows round it. Drawn by the item's cover (FoliosCoverText) and a handheld book's (CoverModel).
public final class CoverEmblem {
    /** How wide the icon is, of the gem's width from corner to corner (turned 45°, as a gem setting is): small enough that the inset's corners show round it. */
    private static final float ICON_SHARE = 0.75F;
    /** How thick a block's icon stands off the face, of its width: thin, so it lies on the face as an inlay. */
    private static final float DEPTH = 0.04F;

    private CoverEmblem() {}

    /**
     * Draws an icon in a gem {@code side} units square, turned 45°: the pose's origin at the gem's middle on its front
     * face, one unit a model unit, +z out of the cover, y up. {@code scale} sizes the icon from its usual size.
     */
    public static void draw(PoseStack pose, SubmitNodeCollector collector, int light, FoliosIcon icon, float side, float scale, int accent) {
        float corner = side / (float) Math.sqrt(2);
        int inset = FoliosPalette.mix(0xFF000000 | accent, 0xFF000000, 0.55F);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WorldPainter.WHITE), (last, consumer) -> {
            vertex(consumer, last, 0, -corner, 0.005F, 0.5F, 0.5F, inset, light);
            vertex(consumer, last, corner, 0, 0.005F, 0.5F, 0.5F, inset, light);
            vertex(consumer, last, 0, corner, 0.005F, 0.5F, 0.5F, inset, light);
            vertex(consumer, last, -corner, 0, 0.005F, 0.5F, 0.5F, inset, light);
        });
        float size = side * (float) Math.sqrt(2) * ICON_SHARE * scale;
        if (icon.texture() != null) {
            float half = size / 2;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(icon.texture()), (last, consumer) -> {
                vertex(consumer, last, -half, -half, 0.01F, 0, 1, 0xFFFFFFFF, light);
                vertex(consumer, last, half, -half, 0.01F, 1, 1, 0xFFFFFFFF, light);
                vertex(consumer, last, half, half, 0.01F, 1, 0, 0xFFFFFFFF, light);
                vertex(consumer, last, -half, half, 0.01F, 0, 0, 0xFFFFFFFF, light);
            });
        } else if (icon.stack() != null) {
            ItemStack stack = icon.stack().at(net.minecraft.Util.getMillis() / 1000.0F);
            if (stack.isEmpty()) return;
            Minecraft minecraft = Minecraft.getInstance();
            pose.pushPose();
            pose.translate(0, 0, 0.01F + size * DEPTH / 2);
            pose.scale(size, size, size * DEPTH);
            // As in an inventory slot: a flat item faces out, a block shows its three faces.
            minecraft.getItemRenderer().renderStatic(null, stack, ItemDisplayContext.GUI, false, pose, collector.buffers(), minecraft.level, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose last, float x, float y, float z, float u, float v, int color, int light) {
        consumer.vertex(last.pose(), x, y, z).color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(last.normal(), 0, 0, 1).endVertex();
    }
}
