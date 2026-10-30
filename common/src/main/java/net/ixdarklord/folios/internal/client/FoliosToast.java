package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.paint.PixelIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class FoliosToast implements Toast {
    private static final int MARGIN = 8;
    private static final int INSET = 12;
    private static final PixelIcon TROPHY = PixelIcon.of("trophy", 9, 9);
    private static final PixelIcon TROPHY_SHEEN = PixelIcon.of("trophy_sheen", 9, 9, 10);

    private final FoliosBook book;
    private final Component heading;
    private final Component name;
    private final FoliosIcon icon;
    private final boolean mastery;
    private final long duration;
    private Toast.Visibility visibility = Toast.Visibility.SHOW;
    private long shownFor;

    private FoliosToast(FoliosBook book, Component heading, Component name, FoliosIcon icon, boolean mastery, long duration) {
        this.book = book;
        this.heading = heading;
        this.name = name;
        this.icon = icon;
        this.mastery = mastery;
        this.duration = duration;
    }

    public static void show(FoliosBook book, Component heading, Component name, @org.jspecify.annotations.Nullable FoliosIcon icon, boolean mastery) {
        Minecraft.getInstance().gui.toastManager().addToast(new FoliosToast(book, heading, name, icon != null ? icon : FoliosIcon.NONE, mastery, mastery ? 7000 : 5000));
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        shownFor = fullyVisibleForMs;
        visibility = fullyVisibleForMs < duration * manager.getNotificationDisplayTimeMultiplier() ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        int width = this.width();
        int height = this.height();
        int edge = INSET - MARGIN;
        FoliosTooltips.frame(graphics, edge, edge, width - edge * 2, height - edge * 2, book, 255, INSET);
        int gilt = FoliosTooltips.titleColor(book);
        int quiet = FoliosTooltips.detailColor(book);
        float seconds = fullyVisibleForMs / 1000.0F;
        int slotX = 7;
        int slotY = (height - 20) / 2;
        int frame = FoliosTooltips.frameColor(book) | 0xFF000000;
        int ground = FoliosTooltips.backgroundColor(book) | 0xFF000000;
        graphics.fill(slotX, slotY, slotX + 20, slotY + 20, FoliosPalette.mix(frame, ground, 0.35F));
        graphics.fill(slotX + 1, slotY + 1, slotX + 19, slotY + 19, FoliosPalette.mix(ground, 0xFF000000, 0.45F));
        if (mastery) {
            this.trophy(graphics, slotX + 2, slotY + 1, seconds);
        } else if (!icon.isEmpty()) {
            icon.draw(graphics, slotX + 2, slotY + 2, 16, seconds);
        }
        int textX = slotX + 26;
        int room = width - textX - 8;
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, 7);
        graphics.pose().scale(0.75F, 0.75F);
        graphics.text(font, heading, 0, 0, quiet, true);
        graphics.pose().popMatrix();
        graphics.text(font, fit(font, name, room), textX, 16, gilt, true);
        float left = 1 - Math.min(1, shownFor / (float) duration);
        int lineLeft = textX;
        int lineRight = width - 8;
        int y = height - 6;
        graphics.fill(lineLeft, y, lineRight, y + 1, FoliosPalette.alpha(quiet, 0x40));
        graphics.fill(lineLeft, y, lineLeft + Math.round((lineRight - lineLeft) * left), y + 1, frame);
    }

    private void trophy(GuiGraphicsExtractor graphics, int x, int y, float seconds) {
        int left = x - 1 + (18 - 9) / 2;
        int top = y + 1 + (18 - 9) / 2;
        TROPHY.rainbow(graphics, left, top, seconds, 0xFFE0B23A);
        int frame = TROPHY_SHEEN.sweepFrame(seconds, 2.4F, 0.5F);
        if (frame >= 0) TROPHY_SHEEN.draw(graphics, left, top, frame, 0xFFFFFFFF);
    }

    private static FormattedCharSequence fit(Font font, Component text, int room) {
        if (font.width(text) <= room) return text.getVisualOrderText();
        net.minecraft.network.chat.FormattedText head = font.substrByWidth(text, Math.max(0, room - font.width("…")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(head, net.minecraft.network.chat.FormattedText.of("…")));
    }
}
