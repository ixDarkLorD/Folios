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

// Minecraft 26.1's GUI drawing calls, on 1.21.1's GuiGraphics, so the screens read the same on every version. 1.21.1
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

    // --- Text: 1.21.1 draws a nearly transparent color opaque, so those are skipped ---

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

    /** A collector that draws the lines handed to it, with the hover of what's under the mouse. */
    public ActiveTextCollector textRenderer() {
        return this.textRenderer(HoveredTextEffects.TOOLTIP_ONLY);
    }

    public ActiveTextCollector textRenderer(HoveredTextEffects effects) {
        return new RenderingTextCollector(effects);
    }

    /** What text shows of the part under the mouse (1.21.1 has no mouse cursor shapes, so only its tooltip). */
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
            // The mouse, through the current transform: the text may be drawn moved.
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

    // A hover event's text, as the game's own tooltip of it has it (an entity's, or plain text); an item's is its own tooltip.
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

    // --- Items ---

    /** An item as an icon, without what a stack in a slot shows over it. */
    public void fakeItem(ItemStack stack, int x, int y) {
        this.raw.renderFakeItem(stack, x, y);
        // 1.21.1 draws an item in depth, well in front of the screen: the depth is cleared, so what's drawn after goes
        // over it, as it does on 26.1.
        this.raw.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** The count and durability bar of a stack in a slot, over its icon. */
    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        this.raw.renderItemDecorations(font, stack, x, y);
    }

    /** A GL texture (a render target's) over a box, with a colour: 26.1's blit of a texture view. */
    public void texture(int textureId, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color) {
        if ((color >>> 24) == 0) return;
        this.raw.flush();
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = this.raw.pose().last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder builder = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(matrix, x0, y0, 0).setUv(u0, v0).setColor(color);
        builder.addVertex(matrix, x0, y1, 0).setUv(u0, v1).setColor(color);
        builder.addVertex(matrix, x1, y1, 0).setUv(u1, v1).setColor(color);
        builder.addVertex(matrix, x1, y0, 0).setUv(u1, v0).setColor(color);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // --- Textures (the pipeline is 26.1's; 1.21.1 blends them all alike) ---

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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.raw.setColor((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F);
    }

    private void untint() {
        this.raw.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    // --- Clipping and layers ---

    /** Clips to a box given in the current transform (1.21.1's scissor ignores the transform, 26.1's doesn't). */
    public void enableScissor(int x0, int y0, int x1, int y1) {
        Matrix4f matrix = this.raw.pose().last().pose();
        Vector3f a = matrix.transformPosition(new Vector3f(x0, y0, 0));
        Vector3f b = matrix.transformPosition(new Vector3f(x1, y1, 0));
        this.raw.enableScissor(Math.round(Math.min(a.x, b.x)), Math.round(Math.min(a.y, b.y)), Math.round(Math.max(a.x, b.x)), Math.round(Math.max(a.y, b.y)));
    }

    public void disableScissor() {
        this.raw.disableScissor();
    }

    /** What's drawn next goes over what came before. */
    public void nextStratum() {
        this.raw.flush();
    }

    /** Blurs what's drawn so far (the panorama or the world), as vanilla's menu background does. */
    public void blurBeforeThisStratum() {
        this.raw.flush();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.processBlurEffect(0);
        minecraft.getMainRenderTarget().bindWrite(false);
    }

    /** Asks for a mouse cursor shape for this frame (1.21.1 has none of its own: Cursors sets it on the window). */
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

    /** 26.1's 2D transform stack, on 1.21.1's PoseStack. */
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
