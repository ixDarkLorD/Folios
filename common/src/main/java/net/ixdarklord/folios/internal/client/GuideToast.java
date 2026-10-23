package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.paint.PixelIcon;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

// A toast in a book's own look, for part of a book finished (an objective, an entry, a category, a quest) and for the
// book mastered: the book's tooltip ground and gilt frame,
// at the left the quest's icon (or, for mastery, a gold trophy that glints), a small gilt heading over the quest's or
// book's name, and along its foot a thin gilt line that runs down while it shows.
public final class GuideToast implements Toast {
    /** The clear margin round the tooltip frame's images, as BuildGuide's panel takes it off. */
    private static final int MARGIN = 8;
    private static final int INSET = 12;
    private static final PixelIcon TROPHY = PixelIcon.of("trophy", 9, 9);
    /** A band of light sweeping across the trophy, over it: a flipbook of its frames. */
    private static final PixelIcon TROPHY_SHEEN = PixelIcon.of("trophy_sheen", 9, 9, 10);

    private final FoliosBook book;
    private final Component heading;
    private final Component name;
    private final FoliosIcon icon;
    private final boolean mastery;
    private final long duration;

    private GuideToast(FoliosBook book, Component heading, Component name, FoliosIcon icon, boolean mastery, long duration) {
        this.book = book;
        this.heading = heading;
        this.name = name;
        this.icon = icon;
        this.mastery = mastery;
        this.duration = duration;
    }

    /** Shows a toast: a heading over a name, with an icon (none for null), or the trophy for mastery. */
    public static void show(FoliosBook book, Component heading, Component name, @Nullable FoliosIcon icon, boolean mastery) {
        Minecraft.getInstance().getToasts().addToast(new GuideToast(book, heading, name, icon != null ? icon : FoliosIcon.NONE, mastery, mastery ? 7000 : 5000));
    }

    // 1.20.1 draws and times a toast in the one call: what it returns says whether it still shows.
    @Override
    public Toast.Visibility render(GuiGraphics raw, ToastComponent toasts, long timeSinceVisible) {
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(raw);
        Font font = toasts.getMinecraft().font;
        double shown = duration * toasts.getNotificationDisplayTimeMultiplier();
        int width = this.width();
        int height = this.height();
        // The frame's visible edge on the toast's own: its images' clear margin taken off.
        int edge = INSET - MARGIN;
        GuideTooltips.frame(graphics, edge, edge, width - edge * 2, height - edge * 2, book, 255, INSET);
        int gilt = GuideTooltips.titleColor(book);
        int quiet = GuideTooltips.detailColor(book);
        float seconds = timeSinceVisible / 1000.0F;
        // The icon, in a slot sunk into the ground.
        int slotX = 7;
        int slotY = (height - 20) / 2;
        int frame = GuideTooltips.frameColor(book) | 0xFF000000;
        int ground = GuideTooltips.backgroundColor(book) | 0xFF000000;
        graphics.fill(slotX, slotY, slotX + 20, slotY + 20, FoliosPalette.mix(frame, ground, 0.35F));
        graphics.fill(slotX + 1, slotY + 1, slotX + 19, slotY + 19, FoliosPalette.mix(ground, 0xFF000000, 0.45F));
        if (mastery) {
            this.trophy(graphics, slotX + 2, slotY + 1, seconds);
        } else if (!icon.isEmpty()) {
            icon.draw(graphics, slotX + 2, slotY + 2, 16, seconds);
        }
        // The heading, small and gilt, over the name, cut short if it must be.
        int textX = slotX + 26;
        int room = width - textX - 8;
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, 7);
        graphics.pose().scale(0.75F, 0.75F);
        graphics.text(font, heading, 0, 0, quiet, true);
        graphics.pose().popMatrix();
        graphics.text(font, fit(font, name, room), textX, 16, gilt, true);
        // How long it has left: a thin line along its foot, running down from full.
        float left = 1 - (float) Math.min(1, timeSinceVisible / shown);
        int lineLeft = textX;
        int lineRight = width - 8;
        int y = height - 6;
        graphics.fill(lineLeft, y, lineRight, y + 1, FoliosPalette.alpha(quiet, 0x40));
        graphics.fill(lineLeft, y, lineLeft + Math.round((lineRight - lineLeft) * left), y + 1, frame);
        return timeSinceVisible < shown ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    // The trophy at its own size, centred in the slot's 18-pixel face, in a rainbow, its sheen sweeping across it
    // every couple of seconds.
    private void trophy(GuiGraphicsExtractor graphics, int x, int y, float seconds) {
        int left = x - 1 + (18 - 9) / 2;
        int top = y + 1 + (18 - 9) / 2;
        TROPHY.rainbow(graphics, left, top, seconds, 0xFFE0B23A);
        int frame = TROPHY_SHEEN.sweepFrame(seconds, 2.4F, 0.5F);
        if (frame >= 0) TROPHY_SHEEN.draw(graphics, left, top, frame, 0xFFFFFFFF);
    }

    // A line cut short with an ellipsis to fit the room, if it must be.
    private static FormattedCharSequence fit(Font font, Component text, int room) {
        if (font.width(text) <= room) return text.getVisualOrderText();
        net.minecraft.network.chat.FormattedText head = font.substrByWidth(text, Math.max(0, room - font.width("…")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(head, net.minecraft.network.chat.FormattedText.of("…")));
    }
}
