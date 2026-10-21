package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.page.GuidePalette;
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

// The books' tooltips: a dark ground with a gilt frame, a name and, under it, a quieter line saying more (how far
// along, which keys). Text that brings its own colours (an item's name by its rarity) keeps them. Their colours are
// the book's ("colors" › "tooltip"), by default its cover's for the ground and gilt warmed by its accent for the
// frame; the sprites are grey, tinted with them, so the tooltip is drawn here rather than as a vanilla style.
public final class GuideTooltips {
    private static final ResourceLocation BACKGROUND = Folios.rl("tooltip/guide_background");
    private static final ResourceLocation FRAME = Folios.rl("tooltip/guide_frame");
    private static final int GILT = 0xFFFFE28C;
    private static final int TITLE = 0xFFF2E6CC;
    private static final int DETAIL = 0xFFA89A86;

    private GuideTooltips() {}

    /** A name, and a line under it saying more; null for none. */
    public static List<Component> of(Component title, @Nullable Component detail) {
        return detail == null ? List.of(title) : List.of(title, detail);
    }

    /** The text colour of a tooltip's name in a book's colours. */
    public static int titleColor(GuideBook book) {
        return book.colors().tooltip().getOrDefault("title", TITLE);
    }

    /**
     * A tooltip's ground and gilt frame in the book's colours, round content from (x, y), {@code width} by
     * {@code height}: for anything shown as the books' tooltips are (a hint, say).
     */
    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, GuideBook book) {
        frame(graphics, x, y, width, height, book, 255);
    }

    /** As {@link #frame(GuiGraphicsExtractor, int, int, int, int, GuideBook)}, faded to {@code alpha} (0 to 255). */
    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, GuideBook book, int alpha) {
        frame(graphics, x, y, width, height, book, alpha, 12);
    }

    /** As the others, the frame {@code inset} pixels out from the content each way (12 for a tooltip's). */
    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, GuideBook book, int alpha, int inset) {
        var colors = book.colors().tooltip();
        int ground = colors.getOrDefault("background", GuidePalette.mix(0xFF000000 | book.cover(), 0xFF120C08, 0.55F));
        int frame = colors.getOrDefault("frame", GuidePalette.mix(GILT, 0xFF000000 | book.accent(), 0.3F));
        int x0 = x - inset;
        int y0 = y - inset;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x0, y0, width + inset * 2, height + inset * 2, GuidePalette.alpha(ground, (ground >>> 24) * alpha / 255));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x0, y0, width + inset * 2, height + inset * 2, GuidePalette.alpha(frame, (frame >>> 24) * alpha / 255));
    }

    /** Shows tooltip lines in the book's colours, over everything drawn so far: the first as a name, the rest quieter. */
    public static void show(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY, GuideBook book) {
        if (lines.isEmpty()) return;
        var colors = book.colors().tooltip();
        int title = colors.getOrDefault("title", TITLE);
        int detail = colors.getOrDefault("detail", DETAIL);
        List<FormattedCharSequence> styled = new ArrayList<>(lines.size());
        int width = 0;
        for (int index = 0; index < lines.size(); index++) {
            Component line = Component.empty().withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(index == 0 ? title : detail)).append(lines.get(index));
            styled.add(line.getVisualOrderText());
            width = Math.max(width, font.width(line));
        }
        int height = styled.size() * 10 - 2 + (styled.size() > 1 ? 2 : 0);
        // Placed as vanilla places its own: up and right of the pointer, flipped left at the screen's edge.
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width > graphics.guiWidth()) x = Math.max(x - 24 - width, 4);
        if (y + height + 3 > graphics.guiHeight()) y = graphics.guiHeight() - height - 3;
        y = Math.max(y, 4);
        graphics.nextStratum();
        frame(graphics, x, y, width, height, book);
        int lineY = y;
        for (int index = 0; index < styled.size(); index++) {
            graphics.text(font, styled.get(index), x, lineY, 0xFFFFFFFF, true);
            lineY += 10 + (index == 0 ? 2 : 0);
        }
    }
}
