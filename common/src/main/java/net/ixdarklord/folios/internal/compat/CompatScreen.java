package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
    private static final PanoramaRenderer PANORAMA = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private boolean focusPending = true;

    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        if (this.focusPending) {
            this.focusPending = false;
            this.setInitialFocus();
        }
        GuiGraphicsExtractor extractor = GuiGraphicsExtractor.of(graphics);
        this.extractBackground(extractor, mouseX, mouseY, a);
        this.extractRenderState(extractor, mouseX, mouseY, a);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.render(graphics.raw(), mouseX, mouseY, a);
    }

    @Override
    public final void renderBackground(GuiGraphics graphics) {
        this.extractBackground(GuiGraphicsExtractor.of(graphics), -1, -1, 0);
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.renderBackground(graphics.raw());
    }

    protected void extractPanorama(GuiGraphicsExtractor graphics, float a) {
        graphics.nextStratum();
        PANORAMA.render(Minecraft.getInstance().getDeltaFrameTime(), 1);
    }

    protected void setInitialFocus() {}

    @Override
    protected void rebuildWidgets() {
        super.rebuildWidgets();
        this.focusPending = true;
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
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.mouseReleased(CompatInput.release(mouseX, mouseY, button));
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.mouseDragged(CompatInput.release(mouseX, mouseY, button), dragX, dragY);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return super.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return this.mouseScrolled(mouseX, mouseY, 0, delta);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
}
