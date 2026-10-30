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

public final class CoverEmblem {
    private static final float ICON_SHARE = 0.75F;
    private static final float DEPTH = 0.04F;

    private CoverEmblem() {}

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
            minecraft.getItemRenderer().renderStatic(null, stack, ItemDisplayContext.GUI, false, pose, collector.buffers(), minecraft.level, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose last, float x, float y, float z, float u, float v, int color, int light) {
        consumer.vertex(last.pose(), x, y, z).color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(last.normal(), 0, 0, 1).endVertex();
    }
}
