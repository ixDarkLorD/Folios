package net.ixdarklord.folios.internal.client.viewer;

import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.internal.mixin.client.ClientTextTooltipAccessor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ViewerTheme {
    private static @Nullable FoliosPainter painter;

    private ViewerTheme() {}

    public static void begin(FoliosPainter on) {
        painter = on;
    }

    public static void end() {
        painter = null;
    }

    public static boolean active() {
        return painter != null;
    }

    public static boolean tooltip(List<ClientTooltipComponent> components) {
        FoliosPainter on = painter;
        if (on == null) return false;
        List<Component> lines = new ArrayList<>();
        for (ClientTooltipComponent component : components) {
            if (component instanceof ClientTextTooltipAccessor text) lines.add(text(text.folios$text()));
        }
        if (!lines.isEmpty()) on.tooltip(lines);
        return true;
    }

    private static Component text(FormattedCharSequence sequence) {
        MutableComponent line = Component.empty();
        StringBuilder run = new StringBuilder();
        Style[] current = {Style.EMPTY};
        sequence.accept((index, style, codePoint) -> {
            if (!style.equals(current[0]) && !run.isEmpty()) {
                line.append(Component.literal(run.toString()).setStyle(current[0]));
                run.setLength(0);
            }
            current[0] = style;
            run.appendCodePoint(codePoint);
            return true;
        });
        if (!run.isEmpty()) line.append(Component.literal(run.toString()).setStyle(current[0]));
        return line;
    }
}
