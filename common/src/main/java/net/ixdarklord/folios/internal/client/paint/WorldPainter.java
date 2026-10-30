package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.RenderType;
import net.ixdarklord.folios.internal.compat.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class WorldPainter extends AbstractPainter {
    public static final ResourceLocation WHITE = Folios.rl("textures/book/white.png");
    private static final float DEPTH = 0.004F;
    private static final float ITEM_LIFT = 0.45F;
    private static final float ITEM_DEPTH = 0.6F;
    public static final float HEIGHT = 1.8F;

    public interface Surface {
        void place(float x, float y, float z, Vector3f out);

        void axes(float x, Vector3f along, Vector3f out);

        float step();
    }

    private static final float SURFACE_LIFT = 0.15F;

    private final PoseStack pose;
    private final SubmitNodeCollector collector;
    private final int light;
    private final @Nullable Surface surface;
    private float[] shapes = new float[13 * 32];
    private int shapeCount;
    private final Vector3f scratch = new Vector3f();
    private final Vector3f along = new Vector3f();
    private final Vector3f out = new Vector3f();
    private float depth = 0.02F;
    private float itemTop;
    private @Nullable Vector3f normal;

    public WorldPainter(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, FoliosSession session, Navigator navigator,
                        float time, float pointerX, float pointerY, List<Hit> hits) {
        this(pose, collector, light, palette, session, navigator, time, pointerX, pointerY, hits, null);
    }

    public WorldPainter(PoseStack pose, SubmitNodeCollector collector, int light, FoliosPalette palette, FoliosSession session, Navigator navigator,
                        float time, float pointerX, float pointerY, List<Hit> hits, @Nullable Surface surface) {
        super(palette, session, navigator, time, pointerX, pointerY, hits);
        this.pose = pose;
        this.collector = collector;
        this.light = light;
        this.surface = surface;
        pose.pushPose();
    }

    public WorldPainter lightAs(Vector3f normal) {
        this.normal = new Vector3f(normal);
        return this;
    }

    private boolean printing;

    public void printing(boolean printing) {
        this.printing = printing;
    }

    @Override
    public int pageWidth() {
        return printing ? super.pageWidth() : net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_WIDTH;
    }

    @Override
    public int pageHeight() {
        return printing ? super.pageHeight() : net.ixdarklord.folios.api.client.page.FoliosLayout.PAGE_HEIGHT;
    }

    public void end() {
        this.flush();
        pose.popPose();
    }

    @Override
    public boolean flat() {
        return false;
    }

    @Override
    public void above(Runnable draw) {
        float before = depth;
        depth = Math.max(depth, itemTop);
        draw.run();
        depth = before;
    }

    private float next() {
        depth += DEPTH;
        return depth;
    }

    public void paper(float x0, float y0, float x1, float y1, int color) {
        this.shape(x0, y0, x1, y1, 0, color);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || (argb >>> 24) == 0) return;
        int alpha = argb >>> 24;
        int color = alpha >= 0xFF ? argb : FoliosPalette.mix(palette.paper() | 0xFF000000, argb | 0xFF000000, alpha / 255.0F);
        if (surface == null) this.shape(x0, y0, x1, y1, this.next(), color);
        else this.shape(this.pageX(x0), this.pageY(y0), this.pageX(x1), this.pageY(y1), this.next(), color);
    }

    private void shape(float x0, float y0, float x1, float y1, float z, int color) {
        Matrix4f matrix = pose.last().pose();
        float[] cuts = this.cuts(x0, x1);
        for (int strip = 0; strip + 1 < cuts.length; strip++) {
            float left = cuts[strip];
            float right = cuts[strip + 1];
            if (shapes.length < shapeCount * 13 + 13) shapes = Arrays.copyOf(shapes, shapes.length * 2);
            int at = shapeCount * 13;
            at = corner(matrix, left, y1, z, at);
            at = corner(matrix, right, y1, z, at);
            at = corner(matrix, right, y0, z, at);
            at = corner(matrix, left, y0, z, at);
            shapes[at] = Float.intBitsToFloat(color);
            shapeCount++;
        }
    }

    private float[] cuts(float x0, float x1) {
        if (surface == null || x1 <= x0) return new float[]{x0, x1};
        float step = surface.step();
        int first = (int) Math.floor(x0 / step) + 1;
        int last = (int) Math.ceil(x1 / step) - 1;
        float[] cuts = new float[Math.max(0, last - first + 1) + 2];
        cuts[0] = x0;
        for (int index = first; index <= last; index++) cuts[index - first + 1] = index * step;
        cuts[cuts.length - 1] = x1;
        return cuts;
    }

    private static float[] withCut(float[] cuts, float at) {
        if (at <= cuts[0] || at >= cuts[cuts.length - 1]) return cuts;
        for (float cut : cuts) if (Math.abs(cut - at) < 1.0E-3F) return cuts;
        float[] more = Arrays.copyOf(cuts, cuts.length + 1);
        more[more.length - 1] = at;
        Arrays.sort(more);
        return more;
    }

    private int corner(Matrix4f matrix, float x, float y, float z, int at) {
        if (surface != null) surface.place(x, y, z, scratch);
        else scratch.set(x, y, z);
        matrix.transformPosition(scratch);
        shapes[at] = scratch.x;
        shapes[at + 1] = scratch.y;
        shapes[at + 2] = scratch.z;
        return at + 3;
    }

    private void flush() {
        if (shapeCount == 0) return;
        float[] drawn = Arrays.copyOf(shapes, shapeCount * 13);
        int count = shapeCount;
        shapeCount = 0;
        int light = this.light;
        Vector3f normal = this.normal;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (last, consumer) -> {
            for (int shape = 0; shape < count; shape++) {
                int base = shape * 13;
                int color = Float.floatToRawIntBits(drawn[base + 12]);
                for (int corner = 0; corner < 4; corner++) {
                    int at = base + corner * 3;
                    var vertex = consumer.addVertex(drawn[at], drawn[at + 1], drawn[at + 2]).setColor(color).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light);
                    if (normal != null) vertex.setNormal(normal.x, normal.y, normal.z);
                    else vertex.setNormal(last, 0, 0, 1);
                }
            }
        });
    }

    private void onSurface(Surface surface, float x, float y, float z) {
        surface.place(x, y, z, scratch);
        surface.axes(x, along, out);
        pose.translate(scratch.x, scratch.y, scratch.z);
        pose.mulPose(new Matrix4f(along.x, along.y, along.z, 0, 0, 1, 0, 0, out.x, out.y, out.z, 0, 0, 0, 0, 1));
    }

    @Override
    public void text(FormattedCharSequence text, int x, int y, int argb) {
        int color = (argb >>> 24) == 0 ? argb | 0xFF000000 : argb;
        float z = this.next();
        Surface surface = this.surface;
        if (surface == null) {
            pose.pushPose();
            pose.translate(0, 0, z);
            collector.submitText(pose, x, y, text, false, Font.DisplayMode.NORMAL, light, color, 0, 0);
            pose.popPose();
            return;
        }
        float scale = this.pageScale();
        float[] advance = {0};
        float top = this.pageY(y);
        float lifted = z + SURFACE_LIFT;
        text.accept((position, style, codepoint) -> {
            FormattedCharSequence glyph = FormattedCharSequence.codepoint(codepoint, style);
            float width = font.width(glyph);
            if (codepoint != ' ') {
                pose.pushPose();
                this.onSurface(surface, this.pageX(x + advance[0]), top, lifted);
                pose.scale(scale, scale, 1);
                collector.submitText(pose, 0, 0, glyph, false, Font.DisplayMode.NORMAL, light, color, 0, 0);
                pose.popPose();
            }
            advance[0] += width;
            return true;
        });
    }

    @Override
    public void texture(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        this.picture(RenderTypes.entityTranslucent(texture), x, y, width, height, u0, v0, u1, v1, argb);
    }

    public void cutout(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        this.picture(RenderTypes.entityCutout(texture), x, y, width, height, u0, v0, u1, v1, argb | 0xFF000000);
    }

    private void picture(RenderType type, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        float z = this.next();
        float x0 = surface == null ? x : this.pageX(x);
        float y0 = surface == null ? y : this.pageY(y);
        float x1 = surface == null ? x + width : this.pageX(x + width);
        float y1 = surface == null ? y + height : this.pageY(y + height);
        this.picture(type, x0, y0, x1, y1, z, u0, v0, u1, v1, argb, 0, 0, false);
    }

    public void paper(float x0, float y0, float x1, float y1, int color, ResourceLocation texture) {
        this.picture(RenderTypes.entityCutout(texture), x0, y0, x1, y1, 0, 0, 0, 1, 1, color, 0, 0, false);
    }

    public void paper(float x0, float y0, float x1, float y1, int color, ResourceLocation texture, float fold, int shade, boolean spineRight) {
        this.picture(RenderTypes.entityCutout(texture), x0, y0, x1, y1, 0, 0, 0, 1, 1, color, fold, shade, spineRight);
    }

    private void picture(RenderType type, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1, int argb,
                         float fold, int shade, boolean spineRight) {
        int light = this.light;
        Vector3f normal = this.normal;
        Matrix4f matrix = pose.last().pose();
        float[] cuts = this.cuts(x0, x1);
        if (fold > 0) cuts = withCut(cuts, spineRight ? x1 - fold : x0 + fold);
        int strips = cuts.length - 1;
        float[] corners = new float[strips * 24];
        for (int strip = 0; strip < strips; strip++) {
            float a = (cuts[strip] - x0) / (x1 - x0);
            float b = (cuts[strip + 1] - x0) / (x1 - x0);
            float left = cuts[strip];
            float right = cuts[strip + 1];
            float uLeft = u0 + (u1 - u0) * a;
            float uRight = u0 + (u1 - u0) * b;
            float[][] points = {{left, y1, uLeft, v1}, {right, y1, uRight, v1}, {right, y0, uRight, v0}, {left, y0, uLeft, v0}};
            for (int corner = 0; corner < 4; corner++) {
                float[] point = points[corner];
                if (surface != null) surface.place(point[0], point[1], z, scratch);
                else scratch.set(point[0], point[1], z);
                matrix.transformPosition(scratch);
                int at = strip * 24 + corner * 6;
                corners[at] = scratch.x;
                corners[at + 1] = scratch.y;
                corners[at + 2] = scratch.z;
                corners[at + 3] = point[2];
                corners[at + 4] = point[3];
                int color = argb;
                if (fold > 0) {
                    float fromSpine = spineRight ? x1 - point[0] : point[0] - x0;
                    if (fromSpine < fold) color = FoliosPalette.mix(shade, argb, Math.max(0, fromSpine) / fold);
                }
                corners[at + 5] = Float.intBitsToFloat(color);
            }
        }
        collector.submitCustomGeometry(pose, type, (last, consumer) -> {
            for (int at = 0; at < corners.length; at += 6) {
                var vertex = consumer.addVertex(corners[at], corners[at + 1], corners[at + 2]).setColor(Float.floatToRawIntBits(corners[at + 5]))
                        .setUv(corners[at + 3], corners[at + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light);
                if (normal != null) vertex.setNormal(normal.x, normal.y, normal.z);
                else vertex.setNormal(last, 0, 0, 1);
            }
        });
    }

    private org.joml.Matrix3f itemLight() {
        org.joml.Matrix3f toItem = new org.joml.Matrix3f(pose.last().normal()).invert();
        Vector3f lit = normal != null ? new Vector3f(normal).normalize() : new Vector3f(0, 0, 1);
        return new org.joml.Matrix3f().rotation(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 0, 1), lit)).mul(toItem);
    }

    @Override
    protected void drawItem(ItemStack stack, int x, int y, int argb) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
        float z = this.next() + ITEM_LIFT;
        pose.pushPose();
        if (surface == null) {
            pose.translate(x + 8, y + 8, z);
            pose.scale(16, -16, ITEM_DEPTH);
        } else {
            this.onSurface(surface, this.pageX(x + 8), this.pageY(y + 8), z + SURFACE_LIFT);
            float scale = this.pageScale();
            pose.scale(16 * scale, -16 * scale, ITEM_DEPTH);
        }
        minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, new PageItemCollector(collector.buffers(), argb, this.itemLight()),
                light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
        itemTop = Math.max(itemTop, z + ITEM_DEPTH / 2);
        if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            float before = depth;
            depth = z + ITEM_DEPTH / 2;
            this.text(FormattedCharSequence.forward(count, Style.EMPTY), x + 17 - font.width(count), y + 9, 0xFFFFFFFF);
            depth = before;
        }
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height) {
        viewport.use();
        if (!viewport.isReady()) return;
        this.picture(RenderTypes.entityTranslucent(viewport.texture()), x, y, width, height, 0, 1, 1, 0, 0xFFFFFFFF);
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float u1) {
        viewport.use();
        if (!viewport.isReady()) return;
        this.picture(RenderTypes.entityTranslucent(viewport.texture()), x, y, width, height, u0, 1, u1, 0, 0xFFFFFFFF);
    }

    @Override
    public void scene(SceneViewport viewport, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int argb) {
        viewport.use();
        if (!viewport.isReady()) return;
        this.picture(RenderTypes.entityTranslucent(viewport.texture()), x, y, width, height, u0, 1 - v0, u1, 1 - v1, argb);
    }

    @Override
    protected void pushed() {
        if (surface == null) pose.pushPose();
    }

    @Override
    protected void popped() {
        if (surface == null) pose.popPose();
    }

    @Override
    protected void translated(float x, float y) {
        if (surface == null) pose.translate(x, y, 0);
    }

    @Override
    protected void scaled(float factor) {
        if (surface == null) pose.scale(factor, factor, 1);
    }
}
