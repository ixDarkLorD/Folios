package net.ixdarklord.folios.internal.compat;

public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers {
    public int button() {
        return this.buttonInfo.button();
    }

    @Override
    public int modifiers() {
        return this.buttonInfo.modifiers();
    }
}
