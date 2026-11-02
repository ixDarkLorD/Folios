package net.ixdarklord.folios.internal.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final BufferBuilder BLITS = new BufferBuilder(8192);
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
        ResourceLocation texture = blitting;
        if (texture == null) return;
        blitting = null;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferUploader.drawWithShader(BLITS.end());
        RenderSystem.disableBlend();
    }

    private void quad(ResourceLocation texture, int x, int y, int width, int height, float u0, float v0, float u1, float v1, int color) {
        if (!texture.equals(blitting)) {
            this.endBlits();
            this.raw.flush();
            blitting = texture;
            BLITS.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        }
        Matrix4f matrix = this.raw.pose().last().pose();
        BLITS.vertex(matrix, x, y, 0).color(color).uv(u0, v0).endVertex();
        BLITS.vertex(matrix, x, y + height, 0).color(color).uv(u0, v1).endVertex();
        BLITS.vertex(matrix, x + width, y + height, 0).color(color).uv(u1, v1).endVertex();
        BLITS.vertex(matrix, x + width, y, 0).color(color).uv(u1, v0).endVertex();
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
                return java.util.Optional.empty();
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
        ResourceLocation texture = new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
        int[] size = net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle.textureSize(texture);
        int border = nineSliceBorder(texture);
        if (border <= 0) {
            this.raw.blit(texture, x, y, width, height, 0, 0, size[0], size[1], size[0], size[1]);
        } else {
            int b = Math.min(border, Math.min(width, height) / 2);
            int[] xs = {x, x + b, x + width - b, x + width};
            int[] ys = {y, y + b, y + height - b, y + height};
            int[] us = {0, border, size[0] - border, size[0]};
            int[] vs = {0, border, size[1] - border, size[1]};
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    int w = xs[column + 1] - xs[column];
                    int h = ys[row + 1] - ys[row];
                    if (w <= 0 || h <= 0) continue;
                    this.raw.blit(texture, xs[column], ys[row], w, h, us[column], vs[row], us[column + 1] - us[column], vs[row + 1] - vs[row], size[0], size[1]);
                }
            }
        }
        this.untint();
    }

    private static final Map<ResourceLocation, Integer> NINE_SLICE = new ConcurrentHashMap<>();
    private static final MetadataSectionSerializer<Integer> GUI_SCALING = new MetadataSectionSerializer<>() {
        @Override
        public String getMetadataSectionName() {
            return "gui";
        }

        @Override
        public Integer fromJson(JsonObject json) {
            JsonObject scaling = GsonHelper.getAsJsonObject(json, "scaling", new JsonObject());
            if (!"nine_slice".equals(GsonHelper.getAsString(scaling, "type", ""))) return 0;
            JsonElement border = scaling.get("border");
            return border == null ? 0 : border.isJsonObject() ? GsonHelper.getAsInt(border.getAsJsonObject(), "left", 0) : border.getAsInt();
        }
    };

    private static int nineSliceBorder(ResourceLocation texture) {
        return NINE_SLICE.computeIfAbsent(texture, id -> {
            try {
                return Minecraft.getInstance().getResourceManager().getResource(id).flatMap(resource -> {
                    try {
                        return resource.metadata().getSection(GUI_SCALING);
                    } catch (IOException e) {
                        return Optional.empty();
                    }
                }).orElse(0);
            } catch (RuntimeException e) {
                return 0;
            }
        });
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height) {
        this.blitSprite(pipeline, sprite, x, y, width, height, 0xFFFFFFFF);
    }

    public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height, float alpha) {
        this.blitSprite(pipeline, sprite, x, y, width, height, Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24 | 0xFFFFFF);
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
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, x, y, 0).uv(0, 0).endVertex();
        builder.vertex(matrix, x, y + height, 0).uv(0, 1).endVertex();
        builder.vertex(matrix, x + width, y + height, 0).uv(1, 1).endVertex();
        builder.vertex(matrix, x + width, y, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(builder.end());
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
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(matrix, x0, y0, 0).uv(u0, v0).color(color).endVertex();
        builder.vertex(matrix, x0, y1, 0).uv(u0, v1).color(color).endVertex();
        builder.vertex(matrix, x1, y1, 0).uv(u1, v1).color(color).endVertex();
        builder.vertex(matrix, x1, y0, 0).uv(u1, v0).color(color).endVertex();
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
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

    public void flushAll() {
        this.endBlits();
        this.raw.flush();
    }

    public void fakeItem(ItemStack stack, int x, int y) {
        if (stack.hasFoil()) BookCache.markDynamic();
        this.endBlits();
        this.raw.flush();
        this.snapped(x, y, () -> this.raw.renderFakeItem(stack, x, y));
    }

    public void item(ItemStack stack, int x, int y) {
        if (stack.hasFoil()) BookCache.markDynamic();
        this.endBlits();
        this.raw.flush();
        this.snapped(x, y, () -> this.raw.renderItem(stack, x, y));
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
        this.raw.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    public void blurBeforeThisStratum() {
        this.endBlits();
        net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigStyle.blur(this.raw);
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
