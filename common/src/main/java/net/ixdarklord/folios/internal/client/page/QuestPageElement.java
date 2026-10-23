package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Optional;

// A quest: how far along it is as a whole, on a bar, then each objective with a box to tick (and a bar of its own
// when it is more than done or not done), then text, and what it gives once done. Drawn live: it moves as the
// player plays, the book open or not.
public record QuestPageElement(List<FoliosObjective> objectives, Optional<String> title, String text, Optional<String> reward) implements FoliosPageElement {
    public static final MapCodec<QuestPageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FoliosObjective.CODEC.listOf(1, 8).fieldOf("objectives").forGetter(QuestPageElement::objectives),
            Codec.STRING.optionalFieldOf("title").forGetter(QuestPageElement::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(QuestPageElement::text),
            Codec.STRING.optionalFieldOf("reward").forGetter(QuestPageElement::reward)
    ).apply(instance, QuestPageElement::new));

    private static final int BAR = 16;
    private static final int ROW = 15;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        FoliosBook book = layout.book();
        int room = BAR + objectives.size() * ROW + (reward.isPresent() ? 24 : 0);
        return layout.flow(title.orElse("folios.quest"), room, text, (painter, y) -> {
            FoliosPalette palette = painter.palette();
            int left = FoliosLayout.CONTENT_LEFT;
            int right = painter.contentRight();
            float whole = FoliosObjective.progress(book, objectives);
            int done = 0;
            for (FoliosObjective objective : objectives) if (objective.isDone(book)) done++;
            boolean complete = done == objectives.size();
            Component count = complete ? Component.translatableWithFallback("folios.quest.complete", "Complete").withStyle(ChatFormatting.BOLD)
                    : Component.literal(done + " / " + objectives.size());
            int countWidth = painter.font().width(count);
            bar(painter, left, y + 3, right - left - countWidth - 6, 5, whole, complete ? 0xFF4CAF50 : palette.accent());
            painter.text(count, right - countWidth, y + 1, complete ? 0xFF3E8E41 : palette.muted());
            int rowY = y + BAR;
            for (FoliosObjective objective : objectives) {
                boolean ticked = objective.isDone(book);
                // A box, ticked once done.
                painter.fill(left, rowY + 1, left + 8, rowY + 9, palette.line());
                painter.fill(left + 1, rowY + 2, left + 7, rowY + 8, ticked ? (complete ? 0xFF4CAF50 : palette.accent()) : palette.panel());
                if (ticked) {
                    painter.fill(left + 2, rowY + 5, left + 3, rowY + 6, 0xFFFFFFFF);
                    painter.fill(left + 3, rowY + 6, left + 4, rowY + 7, 0xFFFFFFFF);
                    painter.fill(left + 4, rowY + 3, left + 5, rowY + 6, 0xFFFFFFFF);
                    painter.fill(left + 5, rowY + 2, left + 6, rowY + 4, 0xFFFFFFFF);
                }
                Component label = objective.label(book);
                if (ticked) label = label.copy().withStyle(ChatFormatting.STRIKETHROUGH);
                FormattedCharSequence line = painter.font().split(label, right - left - 12).stream().findFirst().orElse(FormattedCharSequence.EMPTY);
                painter.text(line, left + 12, rowY + 1, ticked ? palette.muted() : palette.text());
                if (!objective.isAllOrNothing() && !ticked) bar(painter, left + 12, rowY + 11, right - left - 12, 2, objective.progress(book), palette.accent());
                rowY += ROW;
            }
            if (reward.isPresent()) {
                Component gift = Component.translatableWithFallback("folios.quest.reward", "Reward: %s", FoliosText.inline(reward.get()))
                        .withStyle(ChatFormatting.ITALIC);
                // Up to two lines.
                List<FormattedCharSequence> lines = painter.font().split(gift, right - left);
                for (int line = 0; line < Math.min(2, lines.size()); line++) {
                    painter.text(lines.get(line), left, rowY + 2 + line * 10, complete ? palette.heading() : palette.muted());
                }
            }
        });
    }

    /**
     * The mastery bar, 7 pixels tall from (x, y), kin to the build guide's but its own: a soft shadow under it and to its
     * right; a gilt rim, lighter along its top and darker along its foot, its corners rounded off and nothing standing
     * past it; a dark groove with a faint light line along its floor; the filled length in the book's colour (green
     * once full), lit along its top, shaded along its foot and banded with fine slanting stripes, a glint at its end;
     * and quarters marked by gilt dividers inside the groove.
     */
    public static void gilded(FoliosPainter painter, int x, int y, int width, float fill) {
        int accent = painter.palette().accent() | 0xFF000000;
        int rim = FoliosPalette.mix(0xFFE8C66A, accent, 0.35F);
        int x1 = x + width;
        int y1 = y + 7;
        // The shadow.
        painter.fill(x + 2, y1, x1, y1 + 1, 0x50000000);
        painter.fill(x1, y + 2, x1 + 1, y1, 0x50000000);
        painter.fill(x1, y1, x1 + 1, y1 + 1, 0x28000000);
        // The rim, its corner pixels left out: lit on top and at the left, shaded at the foot and the right.
        painter.fill(x + 1, y, x1 - 1, y + 1, FoliosPalette.mix(rim, 0xFFFFFFFF, 0.25F));
        painter.fill(x + 1, y1 - 1, x1 - 1, y1, FoliosPalette.mix(rim, 0xFF000000, 0.35F));
        painter.fill(x, y + 1, x + 1, y1 - 1, FoliosPalette.mix(rim, 0xFFFFFFFF, 0.1F));
        painter.fill(x1 - 1, y + 1, x1, y1 - 1, FoliosPalette.mix(rim, 0xFF000000, 0.25F));
        // The groove: in shadow under the rim's top, a faint light line along its floor.
        painter.fill(x + 1, y + 1, x1 - 1, y1 - 1, 0xFF161112);
        painter.fill(x + 1, y + 1, x1 - 1, y + 2, 0xFF0A0707);
        painter.fill(x + 1, y1 - 2, x1 - 1, y1 - 1, 0xFF231C1C);
        int inside = width - 2;
        int filled = Math.round(inside * Math.max(0, Math.min(1, fill)));
        int left = x + 1;
        int right = left + filled;
        boolean full = fill >= 1;
        if (filled > 0) {
            int color = full ? 0xFF4CAF50 : FoliosPalette.mix(accent, 0xFFFFFFFF, 0.15F);
            painter.fill(left, y + 1, right, y1 - 1, color);
            // Fine slanting stripes, a shade lighter, across the middle of the fill.
            int stripe = FoliosPalette.mix(color, 0xFFFFFFFF, 0.12F);
            for (int at = left; at < right; at += 4) {
                for (int row = 0; row < 3; row++) {
                    int px = at + 2 - row;
                    if (px >= left && px < right) painter.fill(px, y + 2 + row, px + 1, y + 3 + row, stripe);
                }
            }
            painter.fill(left, y + 1, right, y + 2, FoliosPalette.mix(color, 0xFFFFFFFF, 0.5F));
            painter.fill(left, y1 - 2, right, y1 - 1, FoliosPalette.mix(color, 0xFF000000, 0.3F));
            if (!full && filled > 2) painter.fill(right - 1, y + 1, right, y1 - 1, FoliosPalette.mix(color, 0xFFFFFFFF, 0.65F));
        }
        // The quarters: gilt dividers across the groove, inside the rim.
        for (int quarter = 1; quarter < 4; quarter++) {
            int at = left + inside * quarter / 4;
            painter.fill(at, y + 1, at + 1, y1 - 1, FoliosPalette.mix(rim, 0xFF000000, at < right ? 0.1F : 0.4F));
        }
    }

    /**
     * A bar of {@code steps} parts, as a category's entries or a quest's objectives, 5 pixels tall from (x, y): a rim
     * with its corners rounded off, a sunken groove notched between the parts, and the filled length lit along its top
     * and shaded along its foot, a glint at its end. Once full it shows {@code color} with a light sweeping across it
     * now and then.
     */
    public static void stepped(FoliosPainter painter, int x, int y, int width, float fill, int steps, int color) {
        FoliosPalette palette = painter.palette();
        int filled = Math.round((width - 2) * Math.max(0, Math.min(1, fill)));
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.35F);
        int groove = FoliosPalette.mix(palette.panel(), palette.line(), 0.55F);
        // The rim, its corners left out, and the groove inside it, darker along its top where the rim shades it.
        painter.fill(x + 1, y, x + width - 1, y + 1, rim);
        painter.fill(x + 1, y + 4, x + width - 1, y + 5, rim);
        painter.fill(x, y + 1, x + 1, y + 4, rim);
        painter.fill(x + width - 1, y + 1, x + width, y + 4, rim);
        painter.fill(x + 1, y + 1, x + width - 1, y + 4, groove);
        painter.fill(x + 1, y + 1, x + width - 1, y + 2, FoliosPalette.mix(groove, 0xFF000000, 0.12F));
        int inside = x + 1;
        if (filled > 0) {
            painter.fill(inside, y + 1, inside + filled, y + 4, color);
            painter.fill(inside, y + 1, inside + filled, y + 2, FoliosPalette.mix(color, 0xFFFFFFFF, 0.38F));
            painter.fill(inside, y + 3, inside + filled, y + 4, FoliosPalette.mix(color, 0xFF000000, 0.25F));
        }
        // A notch between each part and the next, light in the groove and dark in the fill; left out when too close.
        int parts = Math.max(1, steps);
        if (parts > 1 && (width - 2) / parts >= 3) {
            for (int part = 1; part < parts; part++) {
                int at = inside + Math.round((width - 2) * part / (float) parts);
                boolean lit = at < inside + filled;
                painter.fill(at, y + 1, at + 1, y + 4, lit ? FoliosPalette.mix(color, 0xFF000000, 0.35F) : FoliosPalette.mix(groove, 0xFFFFFFFF, 0.3F));
            }
        }
        if (filled > 0 && filled < width - 2) painter.fill(inside + filled - 1, y + 1, inside + filled, y + 4, FoliosPalette.mix(color, 0xFFFFFFFF, 0.6F));
        if (filled < width - 2) return;
        // Full: a band of light crossing it every few seconds.
        float sweep = (painter.time() % 3.5F) / 1.2F;
        if (sweep >= 1) return;
        int band = inside + Math.round((width + 4) * sweep) - 4;
        int from = Math.max(inside, band);
        int to = Math.min(inside + filled, band + 3);
        if (to > from) painter.fill(from, y + 1, to, y + 4, FoliosPalette.mix(color, 0xFFFFFFFF, 0.55F));
    }

    /**
     * A bar of a given fill (0 to 1) in a colour, drawn as pixel art. Four or more pixels tall: a dark rim round it
     * with its corners clipped, a sunk track, the fill lit along its top and shaded along its foot, notched every few
     * pixels like a gauge, with a bright cap at its leading edge. Thinner: a dashed line of the colour on its track.
     */
    public static void bar(FoliosPainter painter, int x, int y, int width, int height, float fill, int color) {
        FoliosPalette palette = painter.palette();
        int filled = Math.round(width * Math.max(0, Math.min(1, fill)));
        int track = FoliosPalette.mix(palette.panel(), palette.line(), 0.6F);
        if (height < 4) {
            painter.fill(x, y, x + width, y + height, track);
            // Dashes of three pixels, a pixel apart, as far as it is filled.
            for (int at = 0; at < filled; at += 4) painter.fill(x + at, y, x + Math.min(filled, at + 3), y + height, color);
            return;
        }
        int rim = FoliosPalette.mix(palette.line(), palette.text(), 0.45F);
        painter.fill(x, y - 1, x + width, y, rim);
        painter.fill(x, y + height, x + width, y + height + 1, rim);
        painter.fill(x - 1, y, x, y + height, rim);
        painter.fill(x + width, y, x + width + 1, y + height, rim);
        painter.fill(x, y, x + width, y + height, FoliosPalette.mix(track, palette.text(), 0.08F));
        painter.fill(x, y, x + width, y + 1, FoliosPalette.mix(track, palette.text(), 0.18F));
        if (filled <= 0) return;
        painter.fill(x, y, x + filled, y + height, color);
        painter.fill(x, y, x + filled, y + 1, FoliosPalette.mix(color, 0xFFFFFFFF, 0.4F));
        painter.fill(x, y + height - 1, x + filled, y + height, FoliosPalette.mix(color, 0xFF000000, 0.3F));
        int notch = FoliosPalette.mix(color, 0xFF000000, 0.22F);
        for (int at = SEGMENT; at < filled; at += SEGMENT) painter.fill(x + at, y + 1, x + at + 1, y + height - 1, notch);
        if (filled < width) painter.fill(x + filled - 1, y, x + filled, y + height, FoliosPalette.mix(color, 0xFFFFFFFF, 0.55F));
    }

    /** How far apart a bar's notches are. */
    private static final int SEGMENT = 6;

    @Override
    public List<FoliosObjective> objectives() {
        return objectives;
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
