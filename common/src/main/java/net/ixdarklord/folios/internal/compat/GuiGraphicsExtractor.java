package net.ixdarklord.folios.internal.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.ixdarklord.folios.internal.client.screen.BookCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

public final class GuiGraphicsExtractor {
    private static @Nullable GuiGraphicsExtractor last;

    private final GuiGraphics raw;
    private final Pose pose;

    private GuiGraphicsExtractor(GuiGraphics raw) {
        this.raw = raw;
        this.pose = new Pose(raw.pose());
    }

    public static GuiGraphicsExtractor of(GuiGraphics raw) {
        GuiGraphicsExtractor cached = last;
        if (cached != null && cached.raw == raw) return cached;
        return last = new GuiGraphicsExtractor(raw);
    }

    private static @Nullable BufferBuilder blits;
    private static @Nullable ResourceLocation blitting;
    private boolean batching;

    public void batch(Runnable draw) {
        this.batching = true;
        try {
            draw.run();
        } finally {
            this.endBlits();
            this.batching = false;
        }
    }

    private void endBlits() {
        BufferBuilder builder = blits;
        ResourceLocation texture = blitting;
        if (builder == null || texture == null) return;
        blits = null;
        blitting = null;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void quad(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int color) {
        if (blits == null || !texture.equals(blitting)) {
            this.endBlits();
            this.raw.flush();
            blitting = texture;
            blits = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        }
        Matrix4f matrix = this.raw.pose().last().pose();
        blits.addVertex(matrix, x, y, 0).setUv(u0, v0).setColor(color);
        blits.addVertex(matrix, x, y + height, 0).setUv(u0, v1).setColor(color);
        blits.addVertex(matrix, x + width, y + height, 0).setUv(u1, v1).setColor(color);
        blits.addVertex(matrix, x + width, y, 0).setUv(u1, v0).setColor(color);
    }

    public GuiGraphics raw() {
        this.endBlits();
        return this.raw;
    }

    public Pose pose() {
        return this.pose;
    }

    public int guiWidth() {
        return this.raw.guiWidth();
    }

    public int guiHeight() {
        return this.raw.guiHeight();
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        this.endBlits();
        this.raw.fill(x0, y0, x1, y1, color);
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        this.endBlits();
        this.raw.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    public void textHighlight(int x0, int y0, int x1, int y1, boolean focused) {
        this.endBlits();
        this.raw.fill(RenderType.guiTextHighlight(), x0, y0, x1, y1, 0xFF0000FF);
    }

    public void text(Font font, @Nullable String text, int x, int y, int color, boolean shadow) {
        if (text != null && visible(color)) {
            this.endBlits();
            this.raw.drawString(font, text, x, y, color, shadow);
        }
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (visible(color)) {
            text.visit((style, part) -> {
                if (style.isObfuscated()) BookCache.markDynamic();
                return Optional.empty();
            }, Style.EMPTY);
            this.endBlits();
            this.raw.drawString(font, text, x, y, color, shadow);
        }
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (visible(color)) {
            text.accept((index, style, codepoint) -> {
                if (style.isObfuscated()) BookCache.markDynamic();
                return true;
            });
            this.endBlits();
            this.raw.drawString(font, text, x, y, color, shadow);
        }
    }

    public void text(Font font, @Nullable String text, int x, int y, int color) {
        this.text(font, text, x, y, color, true);
    }

    public void text(Font font, Component text, int x, int y, int color) {
        this.text(font, text, x, y, color, true);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.text(font, text, x, y, color, true);
    }

    private static boolean visible(int color) {
        return (color & 0xFC000000) != 0;
    }

    public ActiveTextCollector textRenderer() {
        return this.textRenderer(HoveredTextEffects.TOOLTIP_ONLY);
    }

    public ActiveTextCollector textRenderer(HoveredTextEffects effects) {
        return new RenderingTextCollector(effects);
    }

    public enum HoveredTextEffects {
        NONE(false),
        TOOLTIP_ONLY(true),
        TOOLTIP_AND_CURSOR(true);

        public final boolean allowTooltip;

        HoveredTextEffects(boolean allowTooltip) {
            this.allowTooltip = allowTooltip;
        }
    }

    private final class RenderingTextCollector implements ActiveTextCollector {
        private static final Parameters INITIAL = new Parameters(1);
        private final HoveredTextEffects effects;

        RenderingTextCollector(HoveredTextEffects effects) {
            this.effects = effects;
        }

        @Override
        public Parameters defaultParameters() {
            return INITIAL;
        }

        @Override
        public void accept(TextAlignment alignment, int anchorX, int y, Parameters parameters, FormattedCharSequence text) {
            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            int left = alignment.calculateLeft(anchorX, font, text);
            GuiGraphicsExtractor.this.text(font, text, left, y, Math.round(0xFF * Math.clamp(parameters.opacity(), 0, 1)) << 24 | 0xFFFFFF, true);
            if (!this.effects.allowTooltip || minecraft.screen == null) return;
            double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
            Vector3f local = new Matrix4f(GuiGraphicsExtractor.this.raw.pose().last().pose()).invert().transformPosition(new Vector3f((float) mouseX, (float) mouseY, 0));
            Style style = ActiveTextCollector.styleAt(font, text, left, y, (int) local.x, (int) local.y);
            if (style != null && style.getHoverEvent() != null) {
                List<FormattedCharSequence> lines = hoverLines(font, style);
                if (lines != null) GuiGraphicsExtractor.this.setTooltipForNextFrame(font, lines, (int) mouseX, (int) mouseY);
            }
        }
    }

    private static @Nullable List<FormattedCharSequence> hoverLines(Font font, Style style) {
        HoverEvent hover = style.getHoverEvent();
        if (hover == null) return null;
        Minecraft minecraft = Minecraft.getInstance();
        HoverEvent.ItemStackInfo item = hover.getValue(HoverEvent.Action.SHOW_ITEM);
        if (item != null) return Screen.getTooltipFromItem(minecraft, item.getItemStack()).stream().map(Component::getVisualOrderText).toList();
        HoverEvent.EntityTooltipInfo entity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
        if (entity != null) {
            return minecraft.options.advancedItemTooltips ? entity.getTooltipLines().stream().map(Component::getVisualOrderText).toList() : null;
        }
        Component text = hover.getValue(HoverEvent.Action.SHOW_TEXT);
        return text == null ? null : font.split(text, Math.max(minecraft.getWindow().getGuiScaledWidth() / 2, 200));
    }

    public void flushAll() {
        this.endBlits();
        this.raw.flush();
    }

    public void fakeItem(ItemStack stack, int x, int y) {
        if (stack.hasFoil()) BookCache.markDynamic();
        this.endBlits();
        this.snapped(x, y, () -> this.raw.renderFakeItem(stack, x, y));
        this.raw.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        this.endBlits();
        this.raw.renderItemDecorations(font, stack, x, y);
    }

    public void plainBlit(ResourceLocation texture, int x, int y, int width, int height) {
        this.endBlits();
        this.raw.flush();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = this.raw.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, x, y, 0).setUv(0, 0);
        builder.addVertex(matrix, x, y + height, 0).setUv(0, 1);
        builder.addVertex(matrix, x + width, y + height, 0).setUv(1, 1);
        builder.addVertex(matrix, x + width, y, 0).setUv(1, 0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    public void texture(int textureId, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color) {
        if ((color >>> 24) == 0) return;
        this.endBlits();
        this.raw.flush();
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = this.raw.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(matrix, x0, y0, 0).setUv(u0, v0).setColor(color);
        builder.addVertex(matrix, x0, y1, 0).setUv(u0, v1).setColor(color);
        builder.addVertex(matrix, x1, y1, 0).setUv(u1, v1).setColor(color);
        builder.addVertex(matrix, x1, y0, 0).setUv(u1, v0).setColor(color);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        if ((color >>> 24) == 0) return;
        if (this.batching) {
            this.quad(texture, x, y, width, height, u / textureWidth, v / textureHeight, (u + regionWidth) / textureWidth, (v + regionHeight) / textureHeight, color);
            return;
        }
        this.tint(color);
        this.raw.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        this.untint();
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        this.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int textureWidth, int textureHeight, int color) {
        this.blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
    }

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        this.blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, 0xFFFFFFFF);
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height, int color) {
        if ((color >>> 24) == 0) return;
        this.tint(color);
        this.raw.blitSprite(sprite, x, y, width, height);
        this.untint();
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height, float alpha) {
        this.blitSprite(pipeline, sprite, x, y, width, height, Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255) << 24 | 0xFFFFFF);
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height) {
        this.blitSprite(pipeline, sprite, x, y, width, height, 0xFFFFFFFF);
    }

    private void tint(int color) {
        this.endBlits();
        this.raw.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.raw.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
    }

    private void untint() {
        this.raw.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    private static final float MAX_DRIFT = 2.0F;
    private static @Nullable TextureTarget coarseTarget;
    private static @Nullable RenderTarget drawingTo;

    public static void drawingTo(@Nullable RenderTarget target) {
        drawingTo = target;
    }

    private static int whole(float scale, float gui) {
        float texel = scale * gui;
        int down = Math.max(1, (int) Math.floor(texel));
        int up = Math.max(1, (int) Math.ceil(texel));
        return texel - down < up - texel ? down : up;
    }

    private void coarse(int x, int y, Matrix4f matrix, float scale, float centerX, float centerY, Runnable draw) {
        int pixels = Math.max(1, Math.round(16 * scale));
        this.raw.flush();
        TextureTarget target = coarseTarget;
        if (target == null) target = coarseTarget = new TextureTarget(pixels, pixels, true, Minecraft.ON_OSX);
        else if (target.width != pixels || target.height != pixels) target.resize(pixels, pixels, Minecraft.ON_OSX);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GlStateManager._disableScissorTest();
        float[] tint = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, pixels, pixels, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
        float k = pixels / 16.0F;
        this.raw.pose().pushPose();
        Matrix4f pose = this.raw.pose().last().pose();
        pose.m00(k).m11(k).m22(matrix.m22() * k / scale).m30(-k * x).m31(-k * y);
        try {
            draw.run();
            this.raw.flush();
        } finally {
            this.raw.pose().popPose();
            RenderSystem.restoreProjectionMatrix();
            RenderTarget back = drawingTo != null ? drawingTo : Minecraft.getInstance().getMainRenderTarget();
            back.bindWrite(true);
            RenderSystem.setShaderColor(tint[0], tint[1], tint[2], tint[3]);
            if (scissor) GlStateManager._enableScissorTest();
        }
        int x0 = Math.round(centerX - pixels / 2.0F);
        int y0 = Math.round(centerY - pixels / 2.0F);
        this.raw.pose().pushPose();
        Matrix4f flat = this.raw.pose().last().pose();
        flat.m00(1).m01(0).m10(0).m11(1).m30(0).m31(0);
        this.texture(target.getColorTextureId(), x0, y0, x0 + pixels, y0 + pixels, 0, 1, 1, 0, -1);
        this.raw.pose().popPose();
    }

    private void snapped(int x, int y, Runnable draw) {
        Matrix4f matrix = this.raw.pose().last().pose();
        float scale = matrix.m00();
        if (Math.abs(matrix.m01()) > 1.0E-4F || Math.abs(matrix.m10()) > 1.0E-4F || Math.abs(scale - matrix.m11()) > 1.0E-4F || scale <= 0) {
            draw.run();
            return;
        }
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float centerX = matrix.m30() + scale * (x + 8);
        float centerY = matrix.m31() + scale * (y + 8);
        if (scale < 1 && Math.abs(16 * whole(scale, gui) / gui - 16 * scale) > MAX_DRIFT) {
            this.coarse(x, y, matrix, scale, centerX, centerY, draw);
            return;
        }
        float snapped = whole(scale, gui) / gui;
        float left = Math.round((centerX - 8 * snapped) * gui) / gui;
        float top = Math.round((centerY - 8 * snapped) * gui) / gui;
        this.raw.pose().pushPose();
        Matrix4f pose = this.raw.pose().last().pose();
        pose.m00(snapped).m11(snapped).m22(matrix.m22() * snapped / scale);
        pose.m30(left - snapped * x).m31(top - snapped * y);
        draw.run();
        this.raw.pose().popPose();
    }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        this.endBlits();
        Matrix4f matrix = this.raw.pose().last().pose();
        Vector3f a = matrix.transformPosition(new Vector3f(x0, y0, 0));
        Vector3f b = matrix.transformPosition(new Vector3f(x1, y1, 0));
        this.raw.enableScissor(Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)), Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y)));
    }

    public void disableScissor() {
        this.endBlits();
        this.raw.disableScissor();
    }

    public void nextStratum() {
        this.endBlits();
        this.raw.flush();
    }

    public void blurBeforeThisStratum() {
        this.endBlits();
        this.raw.flush();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.processBlurEffect(0);
        minecraft.getMainRenderTarget().bindWrite(false);
    }

    public void requestCursor(CursorTypes cursor) {
        Cursors.request(cursor);
    }

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
        this.setTooltipForNextFrame(font, lines, DefaultTooltipPositioner.INSTANCE, x, y, false);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        this.setTooltipForNextFrame(font, font.split(text, Math.max(this.guiWidth() / 2, 200)), x, y);
    }

    public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, ClientTooltipPositioner positioner, int x, int y, boolean focused) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || lines.isEmpty()) return;
        screen.setTooltipForNextRenderPass(lines, (screenWidth, screenHeight, mouseX, mouseY, width, height) ->
                positioner.positionTooltip(screenWidth, screenHeight, x, y, width, height), true);
    }

    public static final class Pose {
        private final PoseStack stack;

        Pose(PoseStack stack) {
            this.stack = stack;
        }

        public Pose pushMatrix() {
            this.stack.pushPose();
            return this;
        }

        public Pose popMatrix() {
            this.stack.popPose();
            return this;
        }

        public Pose translate(float x, float y) {
            this.stack.translate(x, y, 0);
            return this;
        }

        public Pose scale(float x, float y) {
            this.stack.scale(x, y, 1);
            return this;
        }

        public Matrix4f snapshot() {
            return new Matrix4f(this.stack.last().pose());
        }

        public Pose scale(float scale) {
            return this.scale(scale, scale);
        }

        public Pose rotate(float radians) {
            this.stack.mulPose(Axis.ZP.rotation(radians));
            return this;
        }
    }
}
