package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.minecraft.network.chat.Component;

import java.util.List;

// Stands in for a page that couldn't be read or laid out, saying why, so a book's writer sees the mistake in place.
public record ErrorPage(String message) implements GuidePage {
    @Override
    public List<Laid> lay(GuideLayout layout) {
        List<net.minecraft.util.FormattedCharSequence> lines = layout.font().split(Component.literal(message), GuideLayout.CONTENT_WIDTH - 8);
        return List.of(painter -> {
            int height = 18 + lines.size() * 10;
            painter.fill(GuideLayout.CONTENT_LEFT, GuideLayout.CONTENT_TOP, GuideLayout.CONTENT_RIGHT, GuideLayout.CONTENT_TOP + height, 0x40E05050);
            painter.fill(GuideLayout.CONTENT_LEFT, GuideLayout.CONTENT_TOP, GuideLayout.CONTENT_LEFT + 2, GuideLayout.CONTENT_TOP + height, 0xFFE05050);
            painter.text(Component.literal("Error").withStyle(net.minecraft.ChatFormatting.BOLD), GuideLayout.CONTENT_LEFT + 6, GuideLayout.CONTENT_TOP + 4, 0xFFE05050);
            int y = GuideLayout.CONTENT_TOP + 16;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                painter.text(line, GuideLayout.CONTENT_LEFT + 6, y, painter.palette().text());
                y += 10;
            }
        });
    }
}
