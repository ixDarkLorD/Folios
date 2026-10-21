package net.ixdarklord.folios.internal.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;

// Minecraft 26.1's submit calls, on 1.21.1's buffers: 1.21.1 draws into a MultiBufferSource as it's called, so what's
// submitted goes straight into the buffer of its render type.
public final class SubmitNodeCollector {
    private final MultiBufferSource buffers;

    private SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public static SubmitNodeCollector of(MultiBufferSource buffers) {
        return new SubmitNodeCollector(buffers);
    }

    public MultiBufferSource buffers() {
        return this.buffers;
    }

    public void submitCustomGeometry(PoseStack pose, RenderType renderType, CustomGeometryRenderer renderer) {
        renderer.render(pose.last(), this.buffers.getBuffer(renderType));
    }

    public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean dropShadow, Font.DisplayMode displayMode,
                           int lightCoords, int color, int backgroundColor, int outlineColor) {
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, dropShadow, pose.last().pose(), this.buffers, displayMode, backgroundColor, lightCoords);
    }

    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}
