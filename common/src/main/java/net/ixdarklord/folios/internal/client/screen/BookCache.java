package net.ixdarklord.folios.internal.client.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.function.Consumer;

public final class BookCache {
    private static final long LIVE_MILLIS = 1000;

    private static @Nullable BookCache recording;
    private static int revision;

    private @Nullable TextureTarget target;
    private boolean valid;
    private boolean dynamic;
    private boolean timed;
    private float until;
    private float recordedUntil;
    private long liveUntil;
    private @Nullable Inputs inputs;
    private @Nullable Environment environment;
    private int seenRevision;

    public record Inputs(int mouseX, int mouseY, int input, int tick, Object spot, Matrix4f pose) {}

    private record Environment(int width, int height, double guiScale) {}

    public boolean draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x0, int y0, int x1, int y1, Inputs inputs, float seconds,
                        Consumer<GuiGraphicsExtractor> content) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now < liveUntil || recording != null) {
            valid = false;
            content.accept(graphics);
            return true;
        }
        Window window = minecraft.getWindow();
        Environment environment = new Environment(window.getWidth(), window.getHeight(), window.getGuiScale());
        if (valid && inputs.equals(this.inputs) && seconds < until && revision == seenRevision && environment.equals(this.environment)) {
            this.blit(graphics, window, x0, y0, x1, y1);
            return false;
        }
        TextureTarget target = this.target(window);
        graphics.flushAll();
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(false);
        dynamic = false;
        timed = false;
        recordedUntil = Float.POSITIVE_INFINITY;
        recording = this;
        GuiGraphicsExtractor.drawingTo(target);
        try {
            content.accept(graphics);
            graphics.flushAll();
        } finally {
            recording = null;
            GuiGraphicsExtractor.drawingTo(null);
            RenderTarget main = minecraft.getMainRenderTarget();
            main.bindWrite(false);
        }
        valid = !dynamic && !timed;
        if (!valid) liveUntil = now + LIVE_MILLIS;
        this.inputs = inputs;
        this.environment = environment;
        until = recordedUntil;
        seenRevision = revision;
        this.blit(graphics, window, x0, y0, x1, y1);
        return true;
    }

    private void blit(GuiGraphicsExtractor graphics, Window window, int x0, int y0, int x1, int y1) {
        TextureTarget target = this.target;
        if (target == null) return;
        graphics.flushAll();
        Matrix4f pose = graphics.pose().snapshot();
        float gui = (float) window.getGuiScale();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        copy(pose, gui, target.height, x0 + 2, y0, x1 - 2, y1);
        copy(pose, gui, target.height, x0 + 1, y0 + 1, x0 + 2, y1 - 1);
        copy(pose, gui, target.height, x1 - 2, y0 + 1, x1 - 1, y1 - 1);
        copy(pose, gui, target.height, x0, y0 + 2, x0 + 1, y1 - 2);
        copy(pose, gui, target.height, x1 - 1, y0 + 2, x1, y1 - 2);
        main.bindWrite(false);
    }

    private static void copy(Matrix4f pose, float gui, int height, int x0, int y0, int x1, int y1) {
        Vector3f from = pose.transformPosition(new Vector3f(x0, y0, 0));
        Vector3f to = pose.transformPosition(new Vector3f(x1, y1, 0));
        int left = covered(Math.min(from.x, to.x) * gui);
        int right = covered(Math.max(from.x, to.x) * gui);
        int top = covered(Math.min(from.y, to.y) * gui);
        int bottom = covered(Math.max(from.y, to.y) * gui);
        if (right <= left || bottom <= top) return;
        GlStateManager._glBlitFrameBuffer(left, height - bottom, right, height - top, left, height - bottom, right, height - top,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static int covered(float edge) {
        return (int) Math.ceil(edge - 0.5F);
    }

    private TextureTarget target(Window window) {
        if (target == null) target = new TextureTarget(window.getWidth(), window.getHeight(), true, Minecraft.ON_OSX);
        else if (target.width != window.getWidth() || target.height != window.getHeight()) target.resize(window.getWidth(), window.getHeight(), Minecraft.ON_OSX);
        return target;
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

    public void close() {
        valid = false;
        if (target != null) target.destroyBuffers();
        target = null;
    }
}
