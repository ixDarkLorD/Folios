package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.gui.Font;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.folios.internal.compat.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FoliosTooltips {
    private static final ResourceLocation BACKGROUND = Folios.rl("tooltip/guide_background");
    private static final ResourceLocation FRAME = Folios.rl("tooltip/guide_frame");
    private static final int GILT = 0xFFFFE28C;
    private static final int TITLE = 0xFFF2E6CC;
    private static final int DETAIL = 0xFFA89A86;

    private FoliosTooltips() {}

    public static List<Component> of(Component title, @Nullable Component detail) {
        return detail == null ? List.of(title) : List.of(title, detail);
    }

    public static int titleColor(FoliosBook book) {
        return book.colors().panel().getOrDefault("title", TITLE);
    }

    public static int backgroundColor(FoliosBook book) {
        return book.colors().panel().getOrDefault("background", FoliosPalette.mix(0xFF000000 | book.cover(), 0xFF120C08, 0.55F));
    }

    public static int frameColor(FoliosBook book) {
        return book.colors().panel().getOrDefault("frame", FoliosPalette.mix(GILT, 0xFF000000 | book.accent(), 0.3F));
    }

    public static int detailColor(FoliosBook book) {
        return book.colors().panel().getOrDefault("detail", DETAIL);
    }

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, FoliosBook book) {
        frame(graphics, x, y, width, height, book, 255);
    }

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, FoliosBook book, int alpha) {
        frame(graphics, x, y, width, height, book, alpha, 12);
    }

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, FoliosBook book, int alpha, int inset) {
        var colors = book.colors().panel();
        int ground = backgroundColor(book);
        int frame = frameColor(book);
        int x0 = x - inset;
        int y0 = y - inset;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x0, y0, width + inset * 2, height + inset * 2, FoliosPalette.alpha(ground, (ground >>> 24) * alpha / 255));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x0, y0, width + inset * 2, height + inset * 2, FoliosPalette.alpha(frame, (frame >>> 24) * alpha / 255));
    }

    public static void show(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY, FoliosBook book) {
        show(graphics, font, lines, mouseX, mouseY, book, -1);
    }

    private static final int RULE_GAP = 5;

    public static void show(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY, FoliosBook book, int ruleBefore) {
        show(graphics, font, lines, mouseX, mouseY, book, ruleBefore, 1);
    }

    public static void show(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY, FoliosBook book, int ruleBefore, float scale) {
        if (lines.isEmpty()) return;
        float s = Math.max(1, scale);
        if (s > 1) {
            graphics.nextStratum();
            graphics.pose().pushMatrix();
            graphics.pose().scale(s, s);
            draw(graphics, font, lines, Math.round(mouseX / s), Math.round(mouseY / s), book, ruleBefore,
                    (int) (graphics.guiWidth() / s), (int) (graphics.guiHeight() / s), false);
            graphics.pose().popMatrix();
            return;
        }
        draw(graphics, font, lines, mouseX, mouseY, book, ruleBefore, graphics.guiWidth(), graphics.guiHeight(), true);
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY, FoliosBook book, int ruleBefore,
                             int screenWidth, int screenHeight, boolean newStratum) {
        var colors = book.colors().panel();
        int title = colors.getOrDefault("title", TITLE);
        int detail = colors.getOrDefault("detail", DETAIL);
        List<FormattedCharSequence> styled = new ArrayList<>(lines.size());
        int width = 0;
        for (int index = 0; index < lines.size(); index++) {
            Component line = Component.empty().withColor(index == 0 ? title : detail).append(lines.get(index));
            styled.add(line.getVisualOrderText());
            width = Math.max(width, font.width(line));
        }
        boolean ruled = ruleBefore > 0 && ruleBefore < styled.size();
        int height = styled.size() * 10 - 2 + (styled.size() > 1 ? 2 : 0) + (ruled ? RULE_GAP : 0);
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width > screenWidth) x = Math.max(x - 24 - width, 4);
        if (y + height + 3 > screenHeight) y = screenHeight - height - 3;
        y = Math.max(y, 4);
        if (newStratum) graphics.nextStratum();
        frame(graphics, x, y, width, height, book);
        int lineY = y;
        for (int index = 0; index < styled.size(); index++) {
            if (ruled && index == ruleBefore) {
                softRule(graphics, x, lineY + 1, width, detail);
                lineY += RULE_GAP;
            }
            graphics.text(font, styled.get(index), x, lineY, 0xFFFFFFFF, true);
            lineY += 10 + (index == 0 ? 2 : 0);
        }
    }

    private static void softRule(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        int fade = Math.max(1, width / 4);
        for (int at = 0; at < width; at++) {
            float strength = Math.min(1, Math.min(at + 0.5F, width - at - 0.5F) / fade);
            graphics.fill(x + at, y, x + at + 1, y + 1, FoliosPalette.alpha(color, Math.round((color >>> 24) * 0.55F * strength)));
        }
        int middle = x + width / 2;
        graphics.fill(middle - 1, y, middle + 1, y + 1, color);
        graphics.fill(middle - 1, y - 1, middle + 1, y, FoliosPalette.alpha(color, (color >>> 24) / 2));
        graphics.fill(middle - 1, y + 1, middle + 1, y + 2, FoliosPalette.alpha(color, (color >>> 24) / 2));
    }
}
