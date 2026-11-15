package net.ixdarklord.folios.internal.client.paint;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface TooltipMore {
    void moreTooltip(List<Component> lines);

    static void more(FoliosPainter painter, List<Component> lines) {
        if (painter instanceof TooltipMore more) more.moreTooltip(lines);
    }
}
