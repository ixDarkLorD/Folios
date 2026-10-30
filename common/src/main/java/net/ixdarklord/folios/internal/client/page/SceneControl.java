package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.minecraft.network.chat.Component;

final class SceneControl {
    record Extra(Component tooltip, Runnable run) {}

    private final SceneViewport view;
    private final float yaw;
    private final float pitch;
    private final float spin;
    private final float zoom;
    private boolean held;
    private static final float ZOOM_MIN = 0.5F;
    private static final float ZOOM_MAX = 4.0F;
    private static final float ZOOM_STEP = 1.15F;

    SceneControl(SceneViewport view, float yaw, float pitch, float spin) {
        this.view = view;
        this.yaw = yaw;
        this.pitch = pitch;
        this.spin = spin;
        this.zoom = view.zoom();
        view.camera(yaw, pitch).spin(spin);
    }

    private void pause() {
        if (!held) view.spin(net.ixdarklord.folios.internal.client.FoliosClock.paused() ? 0 : spin);
    }

    private void zoomed(double notches) {
        float factor = (float) Math.pow(ZOOM_STEP, notches);
        view.zoom(Math.max(zoom * ZOOM_MIN, Math.min(zoom * ZOOM_MAX, view.zoom() * factor)));
        held = true;
    }

    void look(FoliosPainter painter, int x, int y, int width, int height) {
        this.pause();
        painter.scene(view, x, y, width, height);
    }

    void draw(FoliosPainter painter, int x, int y, int width, int height) {
        this.draw(painter, x, y, width, height, null, null);
    }

    void drawPart(FoliosPainter painter, int x, int y, int width, int height, float u0, float u1, boolean first, boolean last,
                  Shapes.@org.jspecify.annotations.Nullable Icon extra, @org.jspecify.annotations.Nullable Extra action) {
        painter.scroll(x, y, width, height, this::zoomed);
        painter.drag(x, y, width, height, (dx, dy) -> {
            held = true;
            view.spin(0);
            view.camera(view.yaw() + dx * 2.5F, Math.max(-10, Math.min(80, view.pitch() + dy * 1.5F)));
        });
        this.pause();
        painter.scene(view, x, y, width, height, u0, u1);
        int size = Shapes.BUTTON;
        if (first && extra != null && action != null) {
            boolean over = painter.action(x + 2, y + 1, size, size + 1, action.tooltip(), action.run());
            Shapes.button(painter, x + 2, y + 2, extra, over);
        }
        if (!last || !held) return;
        int bx = x + width - size - 2;
        boolean over = painter.action(bx, y + 1, size, size + 1, Component.translatableWithFallback("folios.reset_view", "Reset view"), () -> {
            held = false;
            view.camera(yaw, pitch).spin(spin).zoom(zoom);
        });
        Shapes.button(painter, bx, y + 2, Shapes.Icon.RESET, over);
    }

    void draw(FoliosPainter painter, int x, int y, int width, int height, Shapes.@org.jspecify.annotations.Nullable Icon extra,
              @org.jspecify.annotations.Nullable Extra action) {
        painter.scroll(x, y, width, height, this::zoomed);
        painter.drag(x, y, width, height, (dx, dy) -> {
            held = true;
            view.spin(0);
            view.camera(view.yaw() + dx * 2.5F, Math.max(-10, Math.min(80, view.pitch() + dy * 1.5F)));
        });
        this.pause();
        painter.scene(view, x, y, width, height);
        if (extra != null && action != null) {
            int size = Shapes.BUTTON;
            boolean over = painter.action(x + 2, y + 1, size, size + 1, action.tooltip(), action.run());
            Shapes.button(painter, x + 2, y + 2, extra, over);
        }
        if (!held) return;
        int size = Shapes.BUTTON;
        int bx = x + width - size - 2;
        int by = y + 2;
        boolean over = painter.action(bx, by - 1, size, size + 1, Component.translatableWithFallback("folios.reset_view", "Reset view"), () -> {
            held = false;
            view.camera(yaw, pitch).spin(spin).zoom(zoom);
        });
        Shapes.button(painter, bx, by, Shapes.Icon.RESET, over);
    }
}
