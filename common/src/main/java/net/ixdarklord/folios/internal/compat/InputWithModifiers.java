package net.ixdarklord.folios.internal.compat;

import org.lwjgl.glfw.GLFW;

// A click or key press with the modifier keys held, as Minecraft 26.1 passes them.
public interface InputWithModifiers {
    int modifiers();

    default boolean hasShiftDown() {
        return (this.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    default boolean hasControlDown() {
        return (this.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    default boolean hasAltDown() {
        return (this.modifiers() & GLFW.GLFW_MOD_ALT) != 0;
    }
}
