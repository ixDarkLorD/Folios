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
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            this.normal.set(nx, ny, nz);
            if (relight != null) relight.transform(this.normal).normalize();
            this.inner.addVertex(x, y, z, FastColor.ARGB32.multiply(color, argb), u, v, overlay, light, this.normal.x, this.normal.y, this.normal.z);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            int tinted = FastColor.ARGB32.multiply(FastColor.ARGB32.color(alpha, red, green, blue), argb);
            this.inner.setColor(FastColor.ARGB32.red(tinted), FastColor.ARGB32.green(tinted), FastColor.ARGB32.blue(tinted), FastColor.ARGB32.alpha(tinted));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.normal.set(x, y, z);
            if (relight != null) relight.transform(this.normal).normalize();
            this.inner.setNormal(this.normal.x, this.normal.y, this.normal.z);
            return this;
        }
    }
}
