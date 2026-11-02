package net.ixdarklord.folios.internal.client.screen;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.ixdarklord.folios.internal.client.paint.ScaledGuiItems;
import net.ixdarklord.folios.internal.client.paint.ScreenAtlas;
import net.ixdarklord.folios.internal.mixin.client.GuiGraphicsInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class BookCache {
    private static final int CHURN_LIMIT = 6;
    private static final long LIVE_MILLIS = 1000;
    private static final int STRATUM = 1;
    private static final int UP = 2;

    private static @Nullable BookCache recording;
    private static @Nullable BookCache rendering;
    private static @Nullable BookCache pending;
    private static int revision;

    private final GuiRenderState state = new GuiRenderState();
    private @Nullable GuiRenderer renderer;
    private @Nullable TextureTarget target;
    private List<Object> last = new ArrayList<>();
    private List<Object> current = new ArrayList<>();
    private boolean valid;
    private boolean dynamic;
    private boolean unsupported;
    private int churn;
    private long liveUntil;
    private boolean timed;
    private boolean steady;
    private float until;
    private float recordedUntil;
    private @Nullable Inputs inputs;
    private @Nullable Environment environment;
    private int seenRevision;

    public record Inputs(int mouseX, int mouseY, int input, int tick, Object spot, Matrix3x2f pose) {}

    private record Environment(int width, int height, int guiScale, int atlas, int items) {}

    private record Text(Font font, String text, List<Style> styles, Matrix3x2f pose, int x, int y, int color, int background, boolean shadow,
                        @Nullable ScreenRectangle scissor) {}

    private record Item(Object model, Matrix3x2f pose, int x, int y, @Nullable ScreenRectangle scissor) {}

    private record Scaled(Object model, int x0, int y0, int x1, int y1, float scale, Matrix3x2f pose, int tint, boolean coarse,
                          @Nullable ScreenRectangle scissor) {}

    public boolean draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x0, int y0, int x1, int y1, Inputs inputs, float seconds,
                        Consumer<GuiGraphicsExtractor> content) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now < liveUntil || recording != null) {
            steady = false;
            content.accept(graphics);
            return true;
        }
        Window window = minecraft.getWindow();
        Environment environment = new Environment(window.getWidth(), window.getHeight(), window.getGuiScale(), ScreenAtlas.revision(), ScaledGuiItems.revision());
        if (steady && valid && pending != this && inputs.equals(this.inputs) && seconds < until && revision == seenRevision && environment.equals(this.environment)) {
            this.blit(graphics, window, x0, y0, x1, y1);
            return false;
        }
        if (pending == this) {
            pending = null;
            state.reset();
            valid = false;
        }
        GuiGraphicsExtractor offscreen = new GuiGraphicsExtractor(minecraft, state, mouseX, mouseY);
        offscreen.pose().set(graphics.pose());
        current.clear();
        dynamic = false;
        unsupported = false;
        timed = false;
        recordedUntil = Float.POSITIVE_INFINITY;
        recording = this;
        try {
            content.accept(offscreen);
        } finally {
            recording = null;
        }
        if (unsupported) {
            state.reset();
            valid = false;
            steady = false;
            liveUntil = now + LIVE_MILLIS;
            content.accept(graphics);
            return true;
        }
        environment = new Environment(window.getWidth(), window.getHeight(), window.getGuiScale(), ScreenAtlas.revision(), ScaledGuiItems.revision());
        current.add(environment);
        boolean same = valid && !dynamic && current.equals(last);
        if (same) {
            state.reset();
            churn = 0;
        } else {
            List<Object> swap = last;
            last = current;
            current = swap;
            pending = this;
            valid = false;
            if (dynamic || ++churn >= CHURN_LIMIT) {
                churn = 0;
                liveUntil = now + LIVE_MILLIS;
            }
        }
        steady = same && !timed && !dynamic;
        this.inputs = inputs;
        this.environment = environment;
        until = recordedUntil;
        seenRevision = revision;
        this.blit(graphics, window, x0, y0, x1, y1);
        return true;
    }

    private void blit(GuiGraphicsExtractor graphics, Window window, int x0, int y0, int x1, int y1) {
        TextureTarget target = this.target(window);
        float gui = (float) window.getGuiScale();
        Vector2f from = graphics.pose().transformPosition(x0, y0, new Vector2f());
        Vector2f to = graphics.pose().transformPosition(x1, y1, new Vector2f());
        float width = target.width;
        float height = target.height;
        ((GuiGraphicsInvoker) graphics).folios$blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, target.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), x0, y0, x1, y1,
                from.x * gui / width, to.x * gui / width, 1 - from.y * gui / height, 1 - to.y * gui / height, -1);
    }

    private TextureTarget target(Window window) {
        if (target == null) target = new TextureTarget("Folios book cache", window.getWidth(), window.getHeight(), true);
        else if (target.width != window.getWidth() || target.height != window.getHeight()) target.resize(window.getWidth(), window.getHeight());
        return target;
    }

    public static boolean records(GuiRenderState state) {
        return recording != null && recording.state == state;
    }

    public static void timed() {
        if (recording != null) recording.timed = true;
    }

    public static void until(float seconds) {
        if (recording != null) recording.recordedUntil = Math.min(recording.recordedUntil, seconds);
    }

    public static void changed() {
        revision++;
    }

    public static void markDynamic() {
        if (recording != null) recording.dynamic = true;
    }

    public static void stratum() {
        if (recording != null) recording.current.add(STRATUM);
    }

    public static void up() {
        if (recording != null) recording.current.add(UP);
    }

    public static void element(GuiElementRenderState element) {
        if (recording != null) recording.current.add(element);
    }

    public static void text(GuiTextRenderState text) {
        BookCache cache = recording;
        if (cache == null) return;
        StringBuilder chars = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        text.text.accept((index, style, codepoint) -> {
            chars.appendCodePoint(codepoint);
            styles.add(style);
            if (style.isObfuscated()) cache.dynamic = true;
            return true;
        });
        cache.current.add(new Text(text.font, chars.toString(), styles, new Matrix3x2f(text.pose), text.x, text.y, text.color, text.backgroundColor,
                text.dropShadow, text.scissor));
    }

    public static void item(GuiItemRenderState item) {
        BookCache cache = recording;
        if (cache == null) return;
        if (item.itemStackRenderState().isAnimated()) cache.dynamic = true;
        cache.current.add(new Item(item.itemStackRenderState().getModelIdentity(), new Matrix3x2f(item.pose()), item.x(), item.y(), item.scissorArea()));
    }

    public static void picture(PictureInPictureRenderState picture) {
        BookCache cache = recording;
        if (cache == null) return;
        if (!(picture instanceof ScaledGuiItems.State scaled)) {
            cache.unsupported = true;
            return;
        }
        if (scaled.item().itemStackRenderState().isAnimated()) cache.dynamic = true;
        cache.current.add(new Scaled(scaled.item().itemStackRenderState().getModelIdentity(), scaled.x0(), scaled.y0(), scaled.x1(), scaled.y1(),
                scaled.scale(), new Matrix3x2f(scaled.pose()), scaled.tint(), scaled.coarse(), scaled.scissorArea()));
    }

    public static void renderPending(GuiRenderer main, GpuBufferSlice fog) {
        BookCache cache = pending;
        if (cache == null || main == cache.renderer) return;
        pending = null;
        cache.render(fog);
    }

    public static @Nullable RenderTarget target(GuiRenderer renderer) {
        return rendering != null && rendering.renderer == renderer ? rendering.target : null;
    }

    public void close() {
        if (pending == this) pending = null;
        steady = false;
        state.reset();
        valid = false;
        last.clear();
        if (renderer != null) renderer.close();
        renderer = null;
        if (target != null) target.destroyBuffers();
        target = null;
    }

    private void render(GpuBufferSlice fog) {
        TextureTarget target = this.target;
        if (target == null) {
            state.reset();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (renderer == null) {
            renderer = new GuiRenderer(state, minecraft.renderBuffers().bufferSource(), minecraft.gameRenderer.getSubmitNodeStorage(),
                    minecraft.gameRenderer.getFeatureRenderDispatcher(), List.of());
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
        rendering = this;
        try {
            renderer.render(fog);
            renderer.endFrame();
        } finally {
            rendering = null;
        }
        valid = true;
    }
}
