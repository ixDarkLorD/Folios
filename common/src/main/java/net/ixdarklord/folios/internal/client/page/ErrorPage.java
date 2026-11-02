package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public record ErrorPage(String message) implements FoliosPageElement {
    @Override
    public List<Laid> lay(FoliosLayout layout) {
        List<FormattedCharSequence> lines = layout.font().split(Component.literal(message), layout.contentWidth() - 8);
        return List.of(painter -> {
            int height = 18 + lines.size() * 10;
            painter.fill(FoliosLayout.CONTENT_LEFT, FoliosLayout.CONTENT_TOP, painter.contentRight(), FoliosLayout.CONTENT_TOP + height, 0x40E05050);
            painter.fill(FoliosLayout.CONTENT_LEFT, FoliosLayout.CONTENT_TOP, FoliosLayout.CONTENT_LEFT + 2, FoliosLayout.CONTENT_TOP + height, 0xFFE05050);
            painter.text(Component.literal("Error").withStyle(ChatFormatting.BOLD), FoliosLayout.CONTENT_LEFT + 6, FoliosLayout.CONTENT_TOP + 4, 0xFFE05050);
            int y = FoliosLayout.CONTENT_TOP + 16;
            for (FormattedCharSequence line : lines) {
                painter.text(line, FoliosLayout.CONTENT_LEFT + 6, y, painter.palette().text());
                y += 10;
            }
        });
    }
}
