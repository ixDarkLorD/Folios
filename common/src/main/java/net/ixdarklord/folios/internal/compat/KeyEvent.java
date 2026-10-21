package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

// A key press (Minecraft 26.1's).
public record KeyEvent(int key, int scancode, int modifiers) implements InputWithModifiers {
    /** Control, or Command on macOS. */
    public boolean hasControlDownWithQuirk() {
        return Screen.hasControlDown();
    }

    public boolean isEscape() {
        return this.key == GLFW.GLFW_KEY_ESCAPE;
    }
}
