package net.ixdarklord.folios.internal.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

// Minecraft 26.1's GUI drawing calls, on 1.20.1's GuiGraphics, so the screens read the same on every version. 1.20.1
// draws as it's called, so a "stratum" is just what's drawn after the last flush.
public final class GuiGraphicsExtractor {
    private static @Nullable GuiGraphicsExtractor last;

    private final GuiGraphics raw;
    private final Pose pose;

    private GuiGraphicsExtractor(GuiGraphics raw) {
        this.raw = raw;
        this.pose = new Pose(raw.pose());
    }

    /** The 26.1-style view of a GuiGraphics (one per frame's graphics, reused). */
    public static GuiGraphicsExtractor of(GuiGraphics raw) {
        GuiGraphicsExtractor cached = last;
        if (cached != null && cached.raw == raw) return cached;
        return last = new GuiGraphicsExtractor(raw);
    }

    public GuiGraphics raw() {
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

    // --- Shapes ---

    public void fill(int x0, int y0, int x1, int y1, int color) {
        this.raw.fill(x0, y0, x1, y1, color);
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom) {
        this.raw.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    /** A text selection's inverted box. */
    public void textHighlight(int x0, int y0, int x1, int y1, boolean focused) {
        this.raw.fill(RenderType.guiTextHighlight(), x0, y0, x1, y1, 0xFF0000FF);
    }

    // --- Text: 1.20.1 draws a nearly transparent color opaque, so those are skipped ---

    public void text(Font font, @Nullable String text, int x, int y, int color, boolean shadow) {
        if (text != null && visible(color)) this.raw.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (visible(color)) this.raw.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (visible(color)) this.raw.drawString(font, text, x, y, color, shadow);
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

    // --- Textures (the pipeline is 26.1's; 1.20.1 blends them all alike) ---

    public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        if ((color >>> 24) == 0) return;
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
        // 1.20.1 has no GUI sprite atlas: a sprite is its own texture under textures/gui/sprites/, stretched, or in
        // nine pieces when its metadata says so.
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

    private static final java.util.Map<ResourceLocation, Integer> NINE_SLICE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final net.minecraft.server.packs.metadata.MetadataSectionSerializer<Integer> GUI_SCALING = new net.minecraft.server.packs.metadata.MetadataSectionSerializer<>() {
        @Override
        public String getMetadataSectionName() {
            return "gui";
        }

        @Override
        public Integer fromJson(com.google.gson.JsonObject json) {
            com.google.gson.JsonObject scaling = net.minecraft.util.GsonHelper.getAsJsonObject(json, "scaling", new com.google.gson.JsonObject());
            if (!"nine_slice".equals(net.minecraft.util.GsonHelper.getAsString(scaling, "type", ""))) return 0;
            com.google.gson.JsonElement border = scaling.get("border");
            return border == null ? 0 : border.isJsonObject() ? net.minecraft.util.GsonHelper.getAsInt(border.getAsJsonObject(), "left", 0) : border.getAsInt();
        }
    };

    // The border of a sprite's nine-slice scaling (as 1.20.2+ reads it from its .mcmeta), or 0 to stretch it whole.
    private static int nineSliceBorder(ResourceLocation texture) {
        return NINE_SLICE.computeIfAbsent(texture, id -> {
            try {
                return Minecraft.getInstance().getResourceManager().getResource(id).flatMap(resource -> {
                    try {
                        return resource.metadata().getSection(GUI_SCALING);
                    } catch (java.io.IOException e) {
                        return java.util.Optional.empty();
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
        this.blitSprite(pipeline, sprite, x, y, width, height, Math.round(net.minecraft.util.Mth.clamp(alpha, 0.0F, 1.0F) * 255) << 24 | 0xFFFFFF);
    }

    /** The count and durability bar of a stack in a slot, over its icon. */
    public void itemDecorations(Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        this.raw.renderItemDecorations(font, stack, x, y);
    }

    /** A GL texture (a render target's) over a box, with a colour: 26.1's blit of a texture view. */
    public void texture(int textureId, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color) {
        if ((color >>> 24) == 0) return;
        this.raw.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, textureId);
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        org.joml.Matrix4f matrix = this.raw.pose().last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(matrix, x0, y0, 0).uv(u0, v0).color(color).endVertex();
        builder.vertex(matrix, x0, y1, 0).uv(u0, v1).color(color).endVertex();
        builder.vertex(matrix, x1, y1, 0).uv(u1, v1).color(color).endVertex();
        builder.vertex(matrix, x1, y0, 0).uv(u1, v0).color(color).endVertex();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.end());
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private void tint(int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.raw.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
    }

    private void untint() {
        this.raw.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // --- Items ---

    /** An item as an icon: without its count, and unmoved by the player holding one. */
    public void fakeItem(net.minecraft.world.item.ItemStack stack, int x, int y) {
        this.raw.renderFakeItem(stack, x, y);
    }

    public void item(net.minecraft.world.item.ItemStack stack, int x, int y) {
        this.raw.renderItem(stack, x, y);
    }

    // --- Clipping and layers ---

    /** Clips to a box given in the current transform (1.20.1's scissor ignores the transform, 26.1's doesn't). */
    public void enableScissor(int x0, int y0, int x1, int y1) {
        Matrix4f matrix = this.raw.pose().last().pose();
        Vector3f a = matrix.transformPosition(new Vector3f(x0, y0, 0));
        Vector3f b = matrix.transformPosition(new Vector3f(x1, y1, 0));
        this.raw.enableScissor(Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)), Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y)));
    }

    public void disableScissor() {
        this.raw.disableScissor();
    }

    /** What's drawn next goes over what came before: text and items stand off the screen on 1.20.1, so their depth is forgotten too. */
    public void nextStratum() {
        this.raw.flush();
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** 1.20.1 has no menu blur. */
    public void blurBeforeThisStratum() {
        net.ixdarklord.coolcatcore.internal.config.client.gui.style.ConfigStyle.blur(this.raw);
    }

    public void requestCursor(CursorTypes cursor) {
        Cursors.request(cursor);
    }

    // --- Tooltips, drawn by the screen after everything else ---

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

    /** 26.1's 2D transform stack, on 1.20.1's PoseStack. */
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

        public Pose scale(float scale) {
            return this.scale(scale, scale);
        }

        public Pose rotate(float radians) {
            this.stack.mulPose(Axis.ZP.rotation(radians));
            return this;
        }
    }
}
