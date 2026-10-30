package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PageItemCollector implements SubmitNodeCollector {
    private final SubmitNodeCollector root;
    private final OrderedSubmitNodeCollector target;
    private final int argb;
    private final @Nullable Matrix3f relight;

    PageItemCollector(SubmitNodeCollector collector, int argb, @Nullable Matrix3f relight) {
        this(collector, collector, argb, relight);
    }

    private PageItemCollector(SubmitNodeCollector root, OrderedSubmitNodeCollector target, int argb, @Nullable Matrix3f relight) {
        this.root = root;
        this.target = target;
        this.argb = argb;
        this.relight = relight;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return new PageItemCollector(root, root.order(order), argb, relight);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
                           ItemQuads quads, ItemStackRenderState.FoilType foilType) {
        boolean fading = argb != -1;
        Map<RenderType, List<BakedQuad>> byType = new LinkedHashMap<>();
        for (BakedQuad quad : quads.all()) {
            RenderType type = foilType != ItemStackRenderState.FoilType.NONE ? quad.materialInfo().itemGlintRenderType() : quad.materialInfo().itemRenderType();
            if (fading) {
                boolean blocks = quad.materialInfo().sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS);
                type = blocks ? Sheets.translucentBlockItemSheet() : Sheets.translucentItemSheet();
            }
            byType.computeIfAbsent(type, key -> new ArrayList<>()).add(quad);
        }
        byType.forEach((type, faces) -> {
            target.submitCustomGeometry(poseStack, type, (pose, buffer) -> this.faces(pose, buffer, faces, tintLayers, lightCoords, overlayCoords));
        });
    }

    private void faces(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> faces, int[] tintLayers, int lightCoords, int overlayCoords) {
        Matrix4f matrix = pose.pose();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (BakedQuad quad : faces) {
            BakedQuad.MaterialInfo material = quad.materialInfo();
            int layer = material.isTinted() && material.tintIndex() >= 0 && material.tintIndex() < tintLayers.length ? tintLayers[material.tintIndex()] : -1;
            int color = ARGB.multiply(layer, argb);
            pose.transformNormal(quad.direction().getUnitVec3f(), normal);
            if (relight != null) relight.transform(normal).normalize();
            int light = material.lightEmission() > 0 ? LightCoordsUtil.lightCoordsWithEmission(lightCoords, material.lightEmission()) : lightCoords;
            for (int vertex = 0; vertex < 4; vertex++) {
                matrix.transformPosition(quad.position(vertex), position);
                long uv = quad.packedUV(vertex);
                buffer.addVertex(position.x(), position.y(), position.z(), color, UVPair.unpackU(uv), UVPair.unpackV(uv), overlayCoords, light,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        target.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
                              CameraRenderState camera) {
        target.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
                           int lightCoords, int color, int backgroundColor, int outlineColor) {
        target.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
    }

    @Override
    public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode, int lightCoords) {
        target.submitTextBackground(poseStack, x0, y0, x1, y1, color, displayMode, lightCoords);
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        target.submitFlame(poseStack, renderState, rotation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        target.submitLeash(poseStack, leashState);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
                                int tintedColor, @Nullable UvMapping uvMapping, int outlineColor) {
        target.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, uvMapping, outlineColor);
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
                                           int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        target.submitCrumblingOverlay(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, crumblingOverlay);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        target.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
                                 int overlayCoords, int outlineColor) {
        target.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
        target.submitBreakingBlockModel(poseStack, parts, progress, isBlockTranslucent);
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, net.minecraft.world.phys.shapes.VoxelShape shape, RenderType renderType, int color, float width,
                                   boolean afterTerrain) {
        target.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
        target.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
    }

    @Override
    public void submitQuadParticleGroup(net.minecraft.client.renderer.state.level.QuadParticleRenderState particles) {
        target.submitQuadParticleGroup(particles);
    }

    @Override
    public void submitGizmoPrimitives(net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
        target.submitGizmoPrimitives(group, camera, onTop);
    }
}
