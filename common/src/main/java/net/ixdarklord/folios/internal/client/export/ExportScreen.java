package net.ixdarklord.folios.internal.client.export;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ExportScreen extends Screen {
    private final ExportJob job;

    ExportScreen(ExportJob job) {
        super(Component.translatableWithFallback("folios.export.title", "Exporting a book"));
        this.job = job;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        job.draw(graphics, this.width, this.height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        job.cancel();
        super.onClose();
    }
}
