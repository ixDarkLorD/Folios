package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class CompatInput {
    private static final long DOUBLE_CLICK_MILLIS = 250;
    private static final long SAME_CLICK_MILLIS = 40;

    private static double clickX = Double.NaN;
    private static double clickY = Double.NaN;
    private static int clickButton = -1;
    private static long clickTime;
    private static boolean doubleClick;
    private static int lastButton;
    private static InputWithModifiers lastInput = new KeyEvent(0, 0, 0);

    private CompatInput() {}

    public static int modifiers() {
        int modifiers = 0;
        if (Screen.hasShiftDown()) modifiers |= GLFW.GLFW_MOD_SHIFT;
        if (Screen.hasControlDown()) modifiers |= GLFW.GLFW_MOD_CONTROL;
        if (Screen.hasAltDown()) modifiers |= GLFW.GLFW_MOD_ALT;
        return modifiers;
    }

    public static MouseButtonEvent click(double x, double y, int button) {
        long now = System.currentTimeMillis();
        boolean same = x == clickX && y == clickY && button == clickButton && now - clickTime < SAME_CLICK_MILLIS;
        if (!same) {
            doubleClick = button == clickButton && now - clickTime < DOUBLE_CLICK_MILLIS && Math.abs(x - clickX) < 3 && Math.abs(y - clickY) < 3;
            clickX = x;
            clickY = y;
            clickButton = button;
            clickTime = now;
        }
        lastButton = button;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers()));
        lastInput = event;
        return event;
    }

    public static boolean doubleClick() {
        return doubleClick;
    }

    public static MouseButtonEvent at(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(lastButton, modifiers()));
    }

    public static MouseButtonEvent release(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers()));
    }

    public static KeyEvent key(int key, int scancode, int modifiers) {
        KeyEvent event = new KeyEvent(key, scancode, modifiers);
        lastInput = event;
        return event;
    }

    public static InputWithModifiers lastInput() {
        return lastInput;
    }
}
