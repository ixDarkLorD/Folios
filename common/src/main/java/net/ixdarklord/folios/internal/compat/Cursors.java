package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

// The mouse cursor's shape over Glazed Menu's screens, as Minecraft 26.1 has it: what's drawn in a frame asks for a
// shape (the last to ask has it), and the frame's end shows it; a frame nothing asked in shows the arrow. 1.21.1 has no
// such thing, so the shape is set on the window itself, and put back to the arrow once none of these screens is drawn
// any more (tick), so the game's own screens and other mods' never keep a shape from here.
public final class Cursors {
    // The shapes as the window system made them; 0 for one it doesn't have (the arrow is shown instead).
    private static final Map<CursorTypes, Long> HANDLES = new EnumMap<>(CursorTypes.class);
    private static CursorTypes requested = CursorTypes.ARROW;
    private static CursorTypes shown = CursorTypes.ARROW;
    private static int depth;
    private static long lastFrame;

    private Cursors() {}

    /** A screen begins drawing (one drawn inside another, a popup's parent, counts with it). */
    static void begin() {
        if (depth++ == 0) requested = CursorTypes.ARROW;
    }

    /** The screen has drawn: the shape last asked for is shown. */
    static void end() {
        if (--depth > 0) return;
        depth = 0;
        lastFrame = System.currentTimeMillis();
        show(requested);
    }

    static void request(CursorTypes cursor) {
        if (depth > 0) requested = cursor;
    }

    /** Every client tick: the arrow again, once no screen that asks for shapes has been drawn for a moment. */
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
            // A window system without the shape: the arrow stays.
            HANDLES.put(cursor, 0L);
        }
    }
}
