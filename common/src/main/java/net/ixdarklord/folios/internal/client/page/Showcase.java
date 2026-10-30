package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class Showcase {
    enum Selector implements StringRepresentable {
        ARROWS("arrows"),
        NUMBERS("numbers");

        static final Codec<Selector> CODEC = StringRepresentable.fromEnum(Selector::values);

        private final String name;

        Selector(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    record Shown(String id, Optional<String> label) {
        private static final Codec<Shown> OBJECT = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Shown::id),
                FoliosText.CODEC.optionalFieldOf("label").forGetter(Shown::label)
        ).apply(instance, Shown::new));
        static final Codec<Shown> ONE = Codec.withAlternative(OBJECT, Codec.STRING.xmap(id -> new Shown(id, Optional.empty()), Shown::id));
        static final Codec<List<Shown>> CODEC = Codec.withAlternative(ONE.listOf(1, 32), ONE.xmap(List::of, List::getFirst));
    }

    record State(@Nullable Map<BlockPos, BlockChoice> blocks, @Nullable Component message) {
        static State of(Map<BlockPos, BlockChoice> blocks) {
            return new State(blocks, null);
        }

        static State saying(Component message) {
            return new State(null, message);
        }
    }

    record Item(@Nullable Component name, Supplier<State> state) {}

    private static final int SELECTOR = Shapes.BUTTON + 4;
    private static final int GAP = 2;

    private Showcase() {}

    static List<FoliosPageElement.Laid> lay(FoliosLayout layout, Optional<String> title, String text, int height, Selector selector, List<Item> items) {
        Body body = body(layout, height, selector, items);
        return layout.flow(title.orElse(null), body.height(), text, body.part());
    }

    static Body body(FoliosLayout layout, int authoredHeight, Selector selector, List<Item> items) {
        int height = layout.scaled(authoredHeight);
        int contentWidth = layout.contentWidth();
        SceneViewport view = layout.viewport(contentWidth * FoliosLayout.SCENE_DETAIL, height * FoliosLayout.SCENE_DETAIL);
        view.refreshRate(240);
        view.ground(0, 1, 0x30808080, 0x55A0A0A0, false);
        Window window = new Window(view, items);
        int selectorHeight = items.size() > 1 ? SELECTOR : 0;
        return new Body(height + selectorHeight, (painter, y) -> {
            window.draw(painter, y, height);
            if (items.size() > 1) selector(painter, window, selector, y + height + 3);
        });
    }

    private static final class Window {
        private final BuildScene scene;
        private final SceneControl control;
        private final List<Item> items;
        private int current;
        private @Nullable Map<BlockPos, BlockChoice> shown;

        Window(SceneViewport view, List<Item> items) {
            this.scene = new BuildScene(view);
            this.control = new SceneControl(view, 35, 28, 18);
            this.items = items;
        }

        int current() {
            return current;
        }

        int size() {
            return items.size();
        }

        @Nullable Component name(int index) {
            return items.get(index).name();
        }

        void pick(int index) {
            current = Math.floorMod(index, items.size());
        }

        void draw(FoliosPainter painter, int y, int height) {
            int left = FoliosLayout.CONTENT_LEFT;
            int contentWidth = painter.contentRight() - left;
            Shapes.panel(painter, left, y, left + contentWidth, y + height);
            State state = items.get(current).state().get();
            if (state.blocks() != shown) {
                shown = state.blocks();
                scene.show(shown != null ? shown : Map.of());
            }
            if (shown != null && !shown.isEmpty()) {
                scene.update(painter.time());
                control.draw(painter, left + 1, y + 1, contentWidth - 2, height - 2);
                return;
            }
            Component message = state.message() != null ? state.message() : Component.translatableWithFallback("folios.showcase.empty", "Nothing to show");
            List<net.minecraft.util.FormattedCharSequence> lines = painter.font().split(message, contentWidth - 12);
            int top = y + height / 2 - lines.size() * 10 / 2;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                painter.text(line, painter.pageWidth() / 2 - painter.font().width(line) / 2, top, painter.palette().muted());
                top += 10;
            }
        }
    }

    private static void selector(FoliosPainter painter, Window window, Selector selector, int y) {
        int count = window.size();
        int width = count >= 10 ? Shapes.BUTTON + 4 : Shapes.BUTTON;
        int across = count * width + (count - 1) * GAP;
        if (selector == Selector.NUMBERS && across <= painter.contentRight() - FoliosLayout.CONTENT_LEFT) {
            int x = painter.pageWidth() / 2 - across / 2;
            for (int index = 0; index < count; index++) {
                int picked = index;
                Component name = window.name(index);
                Component tooltip = name != null ? name : Component.translatableWithFallback("folios.showcase.number", "%s of %s", index + 1, count);
                boolean over = painter.action(x, y - 1, width, Shapes.BUTTON + 1, tooltip, () -> window.pick(picked));
                Shapes.numberButton(painter, x, y, width, String.valueOf(index + 1), over, index == window.current());
                x += width + GAP;
            }
            return;
        }
        int left = FoliosLayout.CONTENT_LEFT;
        int right = painter.contentRight() - Shapes.BUTTON;
        boolean back = painter.action(left, y - 1, Shapes.BUTTON, Shapes.BUTTON + 1,
                Component.translatableWithFallback("folios.showcase.previous", "Previous"), () -> window.pick(window.current() - 1));
        Shapes.button(painter, left, y, Shapes.Icon.PREVIOUS, back);
        boolean on = painter.action(right, y - 1, Shapes.BUTTON, Shapes.BUTTON + 1,
                Component.translatableWithFallback("folios.showcase.next", "Next"), () -> window.pick(window.current() + 1));
        Shapes.button(painter, right, y, Shapes.Icon.NEXT, on);
        FoliosPalette palette = painter.palette();
        String counter = (window.current() + 1) + "/" + count;
        Component name = window.name(window.current());
        int room = right - left - Shapes.BUTTON - 8;
        net.minecraft.client.gui.Font font = painter.font();
        if (name == null) {
            painter.centeredText(Component.literal(counter), painter.pageWidth() / 2, y + 2, palette.muted());
            return;
        }
        String suffix = "  " + counter;
        int nameRoom = room - font.width(suffix);
        net.minecraft.network.chat.FormattedText fitted = font.width(name) <= nameRoom ? name
                : net.minecraft.network.chat.FormattedText.composite(font.substrByWidth(name, Math.max(0, nameRoom - font.width("…"))), net.minecraft.network.chat.FormattedText.of("…"));
        net.minecraft.util.FormattedCharSequence line = net.minecraft.locale.Language.getInstance().getVisualOrder(fitted);
        int wide = font.width(line) + font.width(suffix);
        int x = painter.pageWidth() / 2 - wide / 2;
        painter.text(line, x, y + 2, palette.text());
        painter.text(Component.literal(suffix), x + font.width(line), y + 2, palette.muted());
    }
}
