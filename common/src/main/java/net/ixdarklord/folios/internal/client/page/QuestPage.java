package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.GuideObjective;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Optional;

// A quest: how far along it is as a whole, on a bar, then each objective with a box to tick (and a bar of its own
// when it is more than done or not done), then text, and what it gives once done. Drawn live: it moves as the
// player plays, the book open or not.
public record QuestPage(List<GuideObjective> objectives, Optional<String> title, String text, Optional<String> reward) implements GuidePage {
    public static final MapCodec<QuestPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            net.ixdarklord.folios.internal.compat.Compat.listOf(GuideObjective.CODEC, 1, 8).fieldOf("objectives").forGetter(QuestPage::objectives),
            Codec.STRING.optionalFieldOf("title").forGetter(QuestPage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(QuestPage::text),
            Codec.STRING.optionalFieldOf("reward").forGetter(QuestPage::reward)
    ).apply(instance, QuestPage::new));

    private static final int BAR = 16;
    private static final int ROW = 15;

    @Override
    public List<Laid> lay(GuideLayout layout) {
        GuideBook book = layout.book();
        int room = BAR + objectives.size() * ROW + (reward.isPresent() ? 24 : 0);
        return layout.flow(title.orElse("folios.quest"), room, text, (painter, y) -> {
            GuidePalette palette = painter.palette();
            int left = GuideLayout.CONTENT_LEFT;
            int right = GuideLayout.CONTENT_RIGHT;
            float whole = GuideObjective.progress(book, objectives);
            int done = 0;
            for (GuideObjective objective : objectives) if (objective.isDone(book)) done++;
            boolean complete = done == objectives.size();
            Component count = complete ? Component.translatableWithFallback("folios.quest.complete", "Complete").withStyle(ChatFormatting.BOLD)
                    : Component.literal(done + " / " + objectives.size());
            int countWidth = painter.font().width(count);
            bar(painter, left, y + 3, right - left - countWidth - 6, 5, whole, complete ? 0xFF4CAF50 : palette.accent());
            painter.text(count, right - countWidth, y + 1, complete ? 0xFF3E8E41 : palette.muted());
            int rowY = y + BAR;
            for (GuideObjective objective : objectives) {
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
                Component gift = Component.translatableWithFallback("folios.quest.reward", "Reward: %s", GuideText.inline(reward.get()))
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
     * A bar of a given fill (0 to 1) in a colour, drawn as pixel art. Four or more pixels tall: a dark rim round it
     * with its corners clipped, a sunk track, the fill lit along its top and shaded along its foot, notched every few
     * pixels like a gauge, with a bright cap at its leading edge. Thinner: a dashed line of the colour on its track.
     */
    public static void bar(GuidePainter painter, int x, int y, int width, int height, float fill, int color) {
        GuidePalette palette = painter.palette();
        int filled = Math.round(width * Math.max(0, Math.min(1, fill)));
        int track = GuidePalette.mix(palette.panel(), palette.line(), 0.6F);
        if (height < 4) {
            painter.fill(x, y, x + width, y + height, track);
            // Dashes of three pixels, a pixel apart, as far as it is filled.
            for (int at = 0; at < filled; at += 4) painter.fill(x + at, y, x + Math.min(filled, at + 3), y + height, color);
            return;
        }
        int rim = GuidePalette.mix(palette.line(), palette.text(), 0.45F);
        painter.fill(x, y - 1, x + width, y, rim);
        painter.fill(x, y + height, x + width, y + height + 1, rim);
        painter.fill(x - 1, y, x, y + height, rim);
        painter.fill(x + width, y, x + width + 1, y + height, rim);
        painter.fill(x, y, x + width, y + height, GuidePalette.mix(track, palette.text(), 0.08F));
        painter.fill(x, y, x + width, y + 1, GuidePalette.mix(track, palette.text(), 0.18F));
        if (filled <= 0) return;
        painter.fill(x, y, x + filled, y + height, color);
        painter.fill(x, y, x + filled, y + 1, GuidePalette.mix(color, 0xFFFFFFFF, 0.4F));
        painter.fill(x, y + height - 1, x + filled, y + height, GuidePalette.mix(color, 0xFF000000, 0.3F));
        int notch = GuidePalette.mix(color, 0xFF000000, 0.22F);
        for (int at = SEGMENT; at < filled; at += SEGMENT) painter.fill(x + at, y + 1, x + at + 1, y + height - 1, notch);
        if (filled < width) painter.fill(x + filled - 1, y, x + filled, y + height, GuidePalette.mix(color, 0xFFFFFFFF, 0.55F));
    }

    /** How far apart a bar's notches are. */
    private static final int SEGMENT = 6;

    @Override
    public List<GuideObjective> objectives() {
        return objectives;
    }

    @Override
    public String searchText() {
        return GuideText.resolve(title.orElse("")) + " " + GuideText.resolve(text);
    }
}
