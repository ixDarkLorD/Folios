package net.ixdarklord.folios.internal.compat;

// A click at a point (Minecraft 26.1's).
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers {
    public int button() {
        return this.buttonInfo.button();
    }

    @Override
    public int modifiers() {
        return this.buttonInfo.modifiers();
    }
}
