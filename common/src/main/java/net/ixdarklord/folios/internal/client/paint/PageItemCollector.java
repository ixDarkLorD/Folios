package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
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

// An item on a page in the world. Passes everything on as it is but the item's own faces, which it draws itself: lit
// as they would be on a page facing the viewer (turned with the page, as the paper's light is, so the item's top
// is lit as its top, not bright or dark as the book tilts), and tinted by a colour, see-through when it fades in
// with the rest of the page. (Items drawn by a renderer of their own, a chest's say, are passed on as they are.)
final class PageItemCollector implements SubmitNodeCollector {
    private final SubmitNodeCollector root;
    private final OrderedSubmitNodeCollector target;
    private final int argb;
    // Turns a face's normal as the pose has it into the one to light it by; null to light it as it lies.
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
                           List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        // The faces on each sheet together: see-through ones while fading, else as the item draws them.
        boolean fading = argb != -1;
        Map<RenderType, List<BakedQuad>> byType = new LinkedHashMap<>();
        for (BakedQuad quad : quads) {
            RenderType type = quad.materialInfo().itemRenderType();
            if (fading) {
                boolean blocks = quad.materialInfo().sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS);
                type = blocks ? Sheets.translucentBlockItemSheet() : Sheets.translucentItemSheet();
            }
            byType.computeIfAbsent(type, key -> new ArrayList<>()).add(quad);
        }
        byType.forEach((type, faces) -> {
            target.submitCustomGeometry(poseStack, type, (pose, buffer) -> this.faces(pose, buffer, faces, tintLayers, lightCoords, overlayCoords));
            // The shimmer of an enchanted item over it, while it isn't fading.
            if (foilType != ItemStackRenderState.FoilType.NONE && !fading) {
                target.submitCustomGeometry(poseStack, ItemFeatureRenderer.getFoilRenderType(type, true),
                        (pose, buffer) -> this.faces(pose, buffer, faces, tintLayers, lightCoords, overlayCoords));
            }
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

    // ---- The rest passed on ----

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        target.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
                              double distanceToCameraSq, CameraRenderState camera) {
        target.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera);
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
                           int lightCoords, int color, int backgroundColor, int outlineColor) {
        target.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
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
                                int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        target.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay);
    }

    @Override
    public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
                                @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintedColor,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        target.submitModelPart(modelPart, poseStack, renderType, lightCoords, overlayCoords, sprite, sheeted, hasFoil, tintedColor, crumblingOverlay, outlineColor);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
        target.submitMovingBlock(poseStack, movingBlockRenderState);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
                                 int overlayCoords, int outlineColor) {
        target.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
        target.submitBreakingBlockModel(poseStack, model, seed, progress);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
        target.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
    }

    @Override
    public void submitParticleGroup(ParticleGroupRenderer particleGroupRenderer) {
        target.submitParticleGroup(particleGroupRenderer);
    }
}
