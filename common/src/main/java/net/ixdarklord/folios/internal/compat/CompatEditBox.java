package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class CompatEditBox extends EditBox {
    public CompatEditBox(Font font, int width, int height, Component narration) {
        super(font, 0, 0, width, height, narration);
    }

    private boolean bordered = true;

    @Override
    public void setBordered(boolean bordered) {
        this.bordered = bordered;
        super.setBordered(bordered);
    }

    public boolean isBordered() {
        return this.bordered;
    }

    public void moveCursorTo(int position, boolean select) {
        int anchor = ((net.ixdarklord.folios.internal.mixin.client.EditBoxAccessor) this).folios$highlightPos();
        this.moveCursorTo(position);
        this.setHighlightPos(select ? anchor : this.getCursorPosition());
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.renderWidget(graphics.raw(), mouseX, mouseY, a);
    }

    public void setTextShadow(boolean shadow) {}

    @Override
    public final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        this.extractWidgetRenderState(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, a);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.mouseClicked(CompatInput.click(mouseX, mouseY, button), CompatInput.doubleClick());
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public final void onClick(double mouseX, double mouseY) {
        this.onClick(CompatInput.at(mouseX, mouseY), CompatInput.doubleClick());
    }

    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event.x(), event.y());
    }

    @Override
    protected final void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        this.onDrag(CompatInput.at(mouseX, mouseY), dragX, dragY);
    }

    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        super.onDrag(event.x(), event.y(), dragX, dragY);
    }

    @Override
    public final void onRelease(double mouseX, double mouseY) {
        this.onRelease(CompatInput.at(mouseX, mouseY));
    }

    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event.x(), event.y());
    }

    @Override
    protected final boolean isValidClickButton(int button) {
        return this.isValidClickButton(new MouseButtonInfo(button, CompatInput.modifiers()));
    }

    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return super.isValidClickButton(buttonInfo.button());
    }

    @Override
    public final boolean keyPressed(int key, int scancode, int modifiers) {
        return this.keyPressed(CompatInput.key(key, scancode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return this.mouseScrolled(mouseX, mouseY, 0, delta);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    public int getRight() {
        return this.getX() + this.getWidth();
    }

    public int getBottom() {
        return this.getY() + this.getHeight();
    }

    public void setHeight(int height) {
        this.height = height;
    }

    protected void handleCursor(GuiGraphicsExtractor graphics) {}
}
