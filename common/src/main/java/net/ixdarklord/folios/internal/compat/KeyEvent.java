package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public record KeyEvent(int key, int scancode, int modifiers) implements InputWithModifiers {
    public boolean hasControlDownWithQuirk() {
        return Screen.hasControlDown();
    }

    public boolean isEscape() {
        return this.key == GLFW.GLFW_KEY_ESCAPE;
    }
}
