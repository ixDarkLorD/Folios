package net.ixdarklord.folios.internal.compat;

import org.lwjgl.glfw.GLFW;

// Minecraft 26.1's mouse cursor shapes. 1.20.1 has none of its own, so they're asked of the window system directly
// (Cursors): each is one of its standard shapes.
public enum CursorTypes {
    ARROW(GLFW.GLFW_ARROW_CURSOR),
    IBEAM(GLFW.GLFW_IBEAM_CURSOR),
    CROSSHAIR(GLFW.GLFW_CROSSHAIR_CURSOR),
    POINTING_HAND(GLFW.GLFW_POINTING_HAND_CURSOR),
    RESIZE_NS(GLFW.GLFW_RESIZE_NS_CURSOR),
    RESIZE_EW(GLFW.GLFW_RESIZE_EW_CURSOR),
    RESIZE_ALL(GLFW.GLFW_RESIZE_ALL_CURSOR),
    NOT_ALLOWED(GLFW.GLFW_NOT_ALLOWED_CURSOR);

    final int shape;

    CursorTypes(int shape) {
        this.shape = shape;
    }
}
