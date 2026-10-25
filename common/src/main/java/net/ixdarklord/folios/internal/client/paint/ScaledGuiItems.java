package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.folios.internal.mixin.client.PictureInPictureRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Items a book draws larger or smaller than 16 pixels. The game draws an item on a screen from a picture of it made at
// 16 pixels (times the GUI scale) and stretches that picture to the size asked for, so a spotlight's large item is
// blurred and a small one loses pixels unevenly. Such an item is instead drawn on its own, at the size it is shown, as
// the game draws its oversized items.
public final class ScaledGuiItems {
    private static boolean sharp;
    // One drawing each for the items of a frame, as each keeps its picture until the frame is drawn.
    private static final List<Renderer> RENDERERS = new ArrayList<>();
    private static int used;

    private ScaledGuiItems() {}

    /** Draws what {@code draw} hands the screen with its scaled items drawn at the size they are shown. */
    public static void sharp(Runnable draw) {
        boolean before = sharp;
        sharp = true;
        try {
            draw.run();
        } finally {
            sharp = before;
        }
    }

    /** An item handed to the screen as one drawn on its own at its size, or null to draw it as the game does. */
    public static @Nullable PictureInPictureRenderState scaled(GuiItemRenderState item) {
        if (!sharp || item.oversizedItemBounds() != null) return null;
        Matrix3x2f pose = item.pose();
        // Only one scaled the same both ways and not turned.
        if (Math.abs(pose.m01) > 1.0E-4F || Math.abs(pose.m10) > 1.0E-4F || Math.abs(pose.m00 - pose.m11) > 1.0E-4F) return null;
        float scale = pose.m00;
        if (Math.abs(scale - 1) < 1.0E-3F || scale <= 0) return null;
        Vector2f corner = pose.transformPosition(item.x(), item.y(), new Vector2f());
        float size = 16 * scale;
        int box = Math.max(1, (int) Math.ceil(size));
        // A whole number of pixels across, centred where the item is, the rest of the way there by the pose.
        float left = corner.x + (size - box) / 2;
        float top = corner.y + (size - box) / 2;
        int x0 = (int) Math.floor(left);
        int y0 = (int) Math.floor(top);
        Matrix3x2f shift = new Matrix3x2f().translation(left - x0, top - y0);
        int tint = ((TintedGuiItems.Tinted) (Object) item).folios$tint();
        return new State(item, x0, y0, x0 + box, y0 + box, size, shift, tint);
    }

    /** The frame's scaled items are about to be drawn: their drawings are free again. */
    public static void startFrame() {
        used = 0;
    }

    public static void prepare(State state, GuiRenderState screen, FeatureRenderDispatcher features, int guiScale) {
        if (used == RENDERERS.size()) RENDERERS.add(new Renderer());
        RENDERERS.get(used++).prepare(state, screen, features, guiScale);
    }

    public static void close() {
        RENDERERS.forEach(Renderer::close);
        RENDERERS.clear();
        used = 0;
    }

    public record State(GuiItemRenderState item, int x0, int y0, int x1, int y1, float scale, Matrix3x2f pose, int tint) implements PictureInPictureRenderState {
        @Override
        public @Nullable ScreenRectangle scissorArea() {
            return item.scissorArea();
        }

        @Override
        public @Nullable ScreenRectangle bounds() {
            return item.bounds();
        }
    }

    private static final class Renderer extends PictureInPictureRenderer<State> {
        private @Nullable Object shown;
        private float shownScale;


        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector submits) {
            pose.scale(1.0F, -1.0F, -1.0F);
            TrackingItemStackRenderState item = state.item().itemStackRenderState();
            Minecraft.getInstance().gameRenderer.lighting().setupFor(item.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            item.submit(pose, submits, 15728880, OverlayTexture.NO_OVERLAY, 0);
            shown = item.getModelIdentity();
            shownScale = state.scale();
        }

        @Override
        protected boolean textureIsReadyToBlit(State state) {
            TrackingItemStackRenderState item = state.item().itemStackRenderState();
            return !item.isAnimated() && shownScale == state.scale() && item.getModelIdentity().equals(shown);
        }

        @Override
        protected void blitTexture(State state, GuiRenderState screen) {
            // As the game's own, with the colour the item was handed with (a page fading in).
            screen.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    TextureSetup.singleTexture(((PictureInPictureRendererAccessor) (Object) this).folios$view(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                    state.pose(), state.x0(), state.y0(), state.x1(), state.y1(), 0.0F, 1.0F, 1.0F, 0.0F,
                    TintedGuiItems.premultiplied(state.tint()), state.scissorArea(), null));
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected String getTextureLabel() {
            return "folios_item";
        }
    }
}
