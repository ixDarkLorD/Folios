package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
    protected CompatScreen(Component title) {
        super(title);
        this.minecraft = Minecraft.getInstance();
        this.font = this.minecraft.font;
    }

    private boolean skipBackground;

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        GuiGraphicsExtractor extractor = GuiGraphicsExtractor.of(graphics);
        Cursors.begin();
        try {
            this.extractBackground(extractor, mouseX, mouseY, a);
            this.extractRenderState(extractor, mouseX, mouseY, a);
        } finally {
            Cursors.end();
        }
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.skipBackground = true;
        try {
            super.render(graphics.raw(), mouseX, mouseY, a);
        } finally {
            this.skipBackground = false;
        }
    }

    @Override
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        if (!this.skipBackground) this.extractBackground(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, a);
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.renderBackground(graphics.raw(), mouseX, mouseY, a);
    }

    protected void extractPanorama(GuiGraphicsExtractor graphics, float a) {
        this.renderPanorama(graphics.raw(), a);
    }

    public void init(int width, int height) {
        this.init(Minecraft.getInstance(), width, height);
    }

    @Override
    public final void resize(Minecraft minecraft, int width, int height) {
        this.resize(width, height);
    }

    public void resize(int width, int height) {
        super.resize(Minecraft.getInstance(), width, height);
    }

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.mouseClicked(CompatInput.click(mouseX, mouseY, button), CompatInput.doubleClick());
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public final boolean keyPressed(int key, int scancode, int modifiers) {
        return this.keyPressed(CompatInput.key(key, scancode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public final boolean keyReleased(int key, int scancode, int modifiers) {
        return this.keyReleased(new KeyEvent(key, scancode, modifiers));
    }

    public boolean keyReleased(KeyEvent event) {
        return super.keyReleased(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.mouseDragged(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, CompatInput.modifiers())), dragX, dragY);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.mouseReleased(CompatInput.release(mouseX, mouseY, button));
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }
}
