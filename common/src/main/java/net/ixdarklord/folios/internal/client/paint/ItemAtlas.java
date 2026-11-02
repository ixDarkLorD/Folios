package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

final class ItemAtlas implements AutoCloseable {
    private static final int SIZE = 1024;
    private static final int GAP = 2;
    private static final int MAX_CELLS = 512;

    private final SubmitNodeStorage submits = new SubmitNodeStorage();
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Folios item atlas");
    private final Map<Key, Cell> cells = new HashMap<>();
    private @Nullable GpuTexture texture;
    private @Nullable GpuTextureView view;
    private @Nullable GpuTexture depth;
    private @Nullable GpuTextureView depthView;
    private int shelfX, shelfY, shelfHeight;
    private boolean full;
    static int revision;

    private record Key(Object model, int size, float scale) {}

    private record Cell(int x, int y, int size) {}

    ItemAtlas() {
    }

    void startFrame() {
        if (full || cells.size() > MAX_CELLS) this.reset();
    }

    boolean prepare(ScaledGuiItems.State state, GuiRenderState screen, FeatureRenderDispatcher features, int guiScale) {
        TrackingItemStackRenderState item = state.item().itemStackRenderState();
        if (item.isAnimated()) return false;
        int width = (state.x1() - state.x0()) * guiScale;
        if (width != (state.y1() - state.y0()) * guiScale) return false;
        Key key = new Key(item.getModelIdentity(), width, state.scale());
        Cell cell = cells.get(key);
        if (cell == null) {
            cell = this.allocate(width);
            if (cell == null) {
                full = true;
                return false;
            }
            this.render(state, cell, features, guiScale);
            cells.put(key, cell);
        }
        float size = SIZE;
        screen.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                state.pose(), state.x0(), state.y0(), state.x1(), state.y1(),
                cell.x / size, (cell.x + cell.size) / size, 1 - cell.y / size, 1 - (cell.y + cell.size) / size,
                TintedGuiItems.premultiplied(state.tint()), state.scissorArea(), null));
        return true;
    }

    private void render(ScaledGuiItems.State state, Cell cell, FeatureRenderDispatcher features, int guiScale) {
        this.textures();
        projection.setupOrtho(-1000.0F, 1000.0F, SIZE, SIZE, true);
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        PoseStack pose = new PoseStack();
        pose.translate(cell.x + cell.size / 2.0F, cell.y + cell.size / 2.0F, 0.0F);
        float scale = guiScale * state.scale();
        pose.scale(scale, scale, -scale);
        ScaledGuiItems.renderItem(state, pose, submits);
        try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(submits);
             RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                     .createRenderPass(() -> "Folios item atlas", view, Optional.empty(), depthView, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
        modelView.popMatrix();
    }

    private @Nullable Cell allocate(int size) {
        int outer = size + GAP;
        if (outer > SIZE) return null;
        if (shelfX + outer > SIZE) {
            shelfY += shelfHeight;
            shelfX = 0;
            shelfHeight = 0;
        }
        if (shelfY + outer > SIZE) return null;
        Cell cell = new Cell(shelfX, shelfY, size);
        shelfX += outer;
        shelfHeight = Math.max(shelfHeight, outer);
        return cell;
    }

    private void textures() {
        if (texture != null) return;
        GpuDevice device = RenderSystem.getDevice();
        texture = device.createTexture(() -> "Folios item atlas", 13, GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
        view = device.createTextureView(texture);
        depth = device.createTexture(() -> "Folios item atlas depth", 9, GpuFormat.D32_FLOAT, SIZE, SIZE, 1, 1);
        depthView = device.createTextureView(depth);
        device.createCommandEncoder().clearColorAndDepthTextures(texture, GuiRenderer.CLEAR_COLOR, depth, 0.0);
    }

    private void reset() {
        revision++;
        cells.clear();
        shelfX = shelfY = shelfHeight = 0;
        full = false;
        if (texture != null) RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(texture, GuiRenderer.CLEAR_COLOR, depth, 0.0);
    }

    @Override
    public void close() {
        if (view != null) view.close();
        if (texture != null) texture.close();
        if (depthView != null) depthView.close();
        if (depth != null) depth.close();
        view = depthView = null;
        texture = depth = null;
        projectionBuffer.close();
    }
}
