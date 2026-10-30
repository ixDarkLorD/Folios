package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

public final class Cursors {
    private static final Map<CursorTypes, Long> HANDLES = new EnumMap<>(CursorTypes.class);
    private static CursorTypes requested = CursorTypes.ARROW;
    private static CursorTypes shown = CursorTypes.ARROW;
    private static int depth;
    private static long lastFrame;

    private Cursors() {}

    static void begin() {
        if (depth++ == 0) requested = CursorTypes.ARROW;
    }

    static void end() {
        if (--depth > 0) return;
        depth = 0;
        lastFrame = System.currentTimeMillis();
        show(requested);
    }

    static void request(CursorTypes cursor) {
        if (depth > 0) requested = cursor;
    }

    public static void tick() {
        if (shown != CursorTypes.ARROW && System.currentTimeMillis() - lastFrame > 150) show(CursorTypes.ARROW);
    }

    private static void show(CursorTypes cursor) {
        if (cursor == shown) return;
        shown = cursor;
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            long handle = cursor == CursorTypes.ARROW ? 0 : HANDLES.computeIfAbsent(cursor, shape -> GLFW.glfwCreateStandardCursor(shape.shape));
            GLFW.glfwSetCursor(window, handle);
        } catch (RuntimeException | LinkageError e) {
            HANDLES.put(cursor, 0L);
        }
    }
}
