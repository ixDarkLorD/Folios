package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

final class PageItemCollector implements MultiBufferSource {
    private final MultiBufferSource buffers;
    private final int argb;
    private final @Nullable Matrix3f relight;

    PageItemCollector(MultiBufferSource buffers, int argb, @Nullable Matrix3f relight) {
        this.buffers = buffers;
        this.argb = argb;
        this.relight = relight;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (this.argb != -1 && isSheet(type)) type = Sheets.translucentCullBlockSheet();
        return new Tinted(this.buffers.getBuffer(type));
    }

    private static boolean isSheet(RenderType type) {
        return type == Sheets.solidBlockSheet() || type == Sheets.cutoutBlockSheet() || type == Sheets.translucentCullBlockSheet()
                || type == Sheets.translucentItemSheet() || type == RenderType.solid() || type == RenderType.cutout()
                || type == RenderType.cutoutMipped() || type == RenderType.translucent();
    }

    private final class Tinted implements VertexConsumer {
        private final VertexConsumer inner;
        private final Vector3f normal = new Vector3f();

        Tinted(VertexConsumer inner) {
            this.inner = inner;
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            this.normal.set(nx, ny, nz);
            if (relight != null) relight.transform(this.normal).normalize();
            int tinted = FastColor.ARGB32.multiply(FastColor.ARGB32.color(Math.round(alpha * 255), Math.round(red * 255), Math.round(green * 255), Math.round(blue * 255)), argb);
            this.inner.vertex(x, y, z, FastColor.ARGB32.red(tinted) / 255.0F, FastColor.ARGB32.green(tinted) / 255.0F, FastColor.ARGB32.blue(tinted) / 255.0F,
                    FastColor.ARGB32.alpha(tinted) / 255.0F, u, v, overlay, light, this.normal.x, this.normal.y, this.normal.z);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            int tinted = FastColor.ARGB32.multiply(FastColor.ARGB32.color(alpha, red, green, blue), argb);
            this.inner.color(FastColor.ARGB32.red(tinted), FastColor.ARGB32.green(tinted), FastColor.ARGB32.blue(tinted), FastColor.ARGB32.alpha(tinted));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            this.inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            this.inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.normal.set(x, y, z);
            if (relight != null) relight.transform(this.normal).normalize();
            this.inner.normal(this.normal.x, this.normal.y, this.normal.z);
            return this;
        }

        @Override
        public void endVertex() {
            this.inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            this.inner.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            this.inner.unsetDefaultColor();
        }
    }
}
