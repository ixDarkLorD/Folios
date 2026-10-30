package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

final class LookupTooltip implements ClientTooltipComponent {
    private static final int FRAME = 20;
    private static final int TOP = 3;
    private static final int GAP = 5;
    private static final int NAME_WIDTH = 110;
    private static final float NAME_SCALE = 0.75F;
    private static final int NAME_LINE = 8;
    private static final int LINE = 10;

    private final BookLookup.Found found;
    private final Component title;
    private final Component hold;
    private final int accent;
    private java.util.@org.jetbrains.annotations.Nullable List<net.minecraft.util.FormattedCharSequence> name;

    LookupTooltip(BookLookup.Found found) {
        this.found = found;
        FoliosBook book = FoliosLibrary.book(found.book());
        this.title = (book != null ? book.title() : Component.literal(found.book().toString())).copy().withStyle(ChatFormatting.WHITE);
        this.hold = Component.translatableWithFallback("folios.lookup.hold", "Hold %s to learn more",
                BookLookup.KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY);
        this.accent = book != null ? 0xFF000000 | book.accent() : 0xFFF2C14E;
    }

    private java.util.List<net.minecraft.util.FormattedCharSequence> name(Font font) {
        if (name == null) name = font.split(title, Math.round(NAME_WIDTH / NAME_SCALE));
        return name;
    }

    private int textHeight(Font font) {
        return this.name(font).size() * NAME_LINE + 1 + LINE - 1;
    }

    @Override
    public int getHeight() {
        return TOP + Math.max(FRAME, this.textHeight(Minecraft.getInstance().font));
    }

    @Override
    public int getWidth(Font font) {
        int widest = font.width(hold);
        for (net.minecraft.util.FormattedCharSequence line : this.name(font)) widest = Math.max(widest, (int) Math.ceil(font.width(line) * NAME_SCALE));
        return FRAME + GAP + widest;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        int textY = y + TOP + Math.max(0, (FRAME - this.textHeight(font)) / 2);
        for (net.minecraft.util.FormattedCharSequence line : this.name(font)) {
            Matrix4f scaled = new Matrix4f(matrix).translate(x + FRAME + GAP, textY, 0).scale(NAME_SCALE, NAME_SCALE, 1);
            font.drawInBatch(line, 0, 0, 0xFFFFFFFF, true, scaled, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            textY += NAME_LINE;
        }
        font.drawInBatch(hold, x + FRAME + GAP, textY + 1, 0xFFFFFFFF, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int top = y + TOP + Math.max(0, (this.textHeight(font) - FRAME) / 2);
        float progress = BookLookup.progress(found);
        graphics.fill(x + 1, top + 1, x + FRAME - 1, top + FRAME - 1, 0x40000000);
        ring(graphics, x, top, 1, 0x50FFFFFF);
        if (progress > 0) ring(graphics, x, top, progress, FoliosPalette.mix(accent, 0xFFFFFFFF, 0.35F * progress));
        graphics.renderItem(found.bookItem(), x + 2, top + 2);
    }

    private static void ring(GuiGraphics graphics, int x, int y, float part, int argb) {
        int side = FRAME - 1;
        int length = 4 * side;
        int filled = Math.round(part * length);
        for (int step = 0; step < filled; step++) {
            int along = (step + side / 2) % length;
            int px, py;
            if (along < side) {
                px = along;
                py = 0;
            } else if (along < 2 * side) {
                px = side;
                py = along - side;
            } else if (along < 3 * side) {
                px = side - (along - 2 * side);
                py = side;
            } else {
                px = 0;
                py = side - (along - 3 * side);
            }
            graphics.fill(x + px, y + py, x + px + 1, y + py + 1, argb);
        }
    }
}
