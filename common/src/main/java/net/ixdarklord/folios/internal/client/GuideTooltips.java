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
// the book's (its "panel" scheme), by default its cover's for the ground and gilt warmed by its accent for the
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
        return book.colors().panel().getOrDefault("title", TITLE);
    }

    /** A tooltip's ground in a book's colours: its own, else its cover's darkened. */
    public static int backgroundColor(GuideBook book) {
        return book.colors().panel().getOrDefault("background", GuidePalette.mix(0xFF000000 | book.cover(), 0xFF120C08, 0.55F));
    }

    /** A tooltip's frame in a book's colours: its own, else gilt warmed by its accent. */
    public static int frameColor(GuideBook book) {
        return book.colors().panel().getOrDefault("frame", GuidePalette.mix(GILT, 0xFF000000 | book.accent(), 0.3F));
    }

    /** The text colour of a tooltip's quieter lines in a book's colours. */
    public static int detailColor(GuideBook book) {
        return book.colors().panel().getOrDefault("detail", DETAIL);
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
        var colors = book.colors().panel();
        int ground = backgroundColor(book);
        int frame = frameColor(book);
        int x0 = x - inset;
        int y0 = y - inset;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x0, y0, width + inset * 2, height + inset * 2, GuidePalette.alpha(ground, (ground >>> 24) * alpha / 255));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x0, y0, width + inset * 2, height + inset * 2, GuidePalette.alpha(frame, (frame >>> 24) * alpha / 255));
    }

    /** Shows tooltip lines in the book's colours, over everything drawn so far: the first as a name, the rest quieter. */
    public static void show(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY, GuideBook book) {
        show(graphics, font, lines, mouseX, mouseY, book, -1);
    }

    /** The room a soft rule between two lines takes, more than the lines' own gap. */
    private static final int RULE_GAP = 5;

    /**
     * As {@link #show(GuiGraphicsExtractor, Font, List, int, int, GuideBook)}, with a soft rule above the line at
     * {@code ruleBefore} (none for -1): what is said set apart from what to do.
     */
    public static void show(GuiGraphicsExtractor graphics, Font font, List<Component> lines, int mouseX, int mouseY, GuideBook book, int ruleBefore) {
        if (lines.isEmpty()) return;
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
            if (ruled && index == ruleBefore) {
                softRule(graphics, x, lineY + 1, width, detail);
                lineY += RULE_GAP;
            }
            graphics.text(font, styled.get(index), x, lineY, 0xFFFFFFFF, true);
            lineY += 10 + (index == 0 ? 2 : 0);
        }
    }

    // A soft rule across a tooltip, a pixel high: its colour fading in from either end over a quarter of its length,
    // with a small stud at its middle, as the rule between a cover's name and subtitle has.
    private static void softRule(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        int fade = Math.max(1, width / 4);
        for (int at = 0; at < width; at++) {
            float strength = Math.min(1, Math.min(at + 0.5F, width - at - 0.5F) / fade);
            graphics.fill(x + at, y, x + at + 1, y + 1, GuidePalette.alpha(color, Math.round((color >>> 24) * 0.55F * strength)));
        }
        int middle = x + width / 2;
        graphics.fill(middle - 1, y, middle + 1, y + 1, color);
        graphics.fill(middle - 1, y - 1, middle + 1, y, GuidePalette.alpha(color, (color >>> 24) / 2));
        graphics.fill(middle - 1, y + 1, middle + 1, y + 2, GuidePalette.alpha(color, (color >>> 24) / 2));
    }
}
