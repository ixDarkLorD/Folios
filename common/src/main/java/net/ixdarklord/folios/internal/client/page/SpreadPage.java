package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SpreadPage(Optional<String> title, String text, Optional<ResourceLocation> image, Optional<String> build, Optional<Build> multiblock,
                         Optional<MapLayout> map, int height)
        implements FoliosPageElement {
    public record Build(List<List<String>> pattern, Map<String, String> mapping) {
        static final Codec<Build> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().listOf().fieldOf("pattern").forGetter(Build::pattern),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("mapping").forGetter(Build::mapping)
        ).apply(instance, Build::new));
    }

    public record Node(String id, int x, int y, JsonElement icon, Optional<String> entry, Optional<String> label) {
        static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Node::id),
                Codec.INT.fieldOf("x").forGetter(Node::x),
                Codec.INT.fieldOf("y").forGetter(Node::y),
                ExtraCodecs.JSON.optionalFieldOf("icon", new com.google.gson.JsonPrimitive("minecraft:book")).forGetter(Node::icon),
                Codec.STRING.optionalFieldOf("entry").forGetter(Node::entry),
                FoliosText.CODEC.optionalFieldOf("label").forGetter(Node::label)
        ).apply(instance, Node::new));
    }

    public record MapLayout(List<Node> nodes, List<List<String>> paths, Optional<ResourceLocation> background) {
        static final Codec<MapLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Node.CODEC.listOf().fieldOf("nodes").forGetter(MapLayout::nodes),
                net.ixdarklord.folios.internal.compat.Compat.listOf(Codec.STRING, 2, 2).listOf().optionalFieldOf("paths", List.of()).forGetter(MapLayout::paths),
                ResourceLocation.CODEC.optionalFieldOf("background").forGetter(MapLayout::background)
        ).apply(instance, MapLayout::new));
    }

    public static final MapCodec<SpreadPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FoliosText.CODEC.optionalFieldOf("title").forGetter(SpreadPage::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(SpreadPage::text),
            ResourceLocation.CODEC.optionalFieldOf("image").forGetter(SpreadPage::image),
            Codec.STRING.optionalFieldOf("build").forGetter(SpreadPage::build),
            Build.CODEC.optionalFieldOf("multiblock").forGetter(SpreadPage::multiblock),
            MapLayout.CODEC.optionalFieldOf("map").forGetter(SpreadPage::map),
            Codec.intRange(40, 150).optionalFieldOf("height", 104).forGetter(SpreadPage::height)
    ).apply(instance, SpreadPage::new));

    private static final int LEFT = FoliosLayout.CONTENT_LEFT;

    private record G(int pw, int ph) {
        static G of(FoliosLayout layout) {
            return new G(layout.pageWidth(), layout.pageHeight());
        }

        int width() {
            return pw * 2 - FoliosLayout.CONTENT_LEFT * 2;
        }

        int contentBottom() {
            return ph - FoliosLayout.FOOT;
        }
    }
    private static final int NODE = 20;
    private static final int INNER = 2;

    private int windowHeight(G g) {
        if (build.isEmpty() && multiblock.isEmpty()) return height;
        int top = FoliosLayout.CONTENT_TOP + (title.isPresent() && !title.get().isEmpty() ? FoliosLayout.TITLE_HEIGHT : 0);
        int below = 5 + Requirements.HEADING + Requirements.SLOT + (text.isEmpty() ? 0 : 5 + 10);
        return Math.max(40, Math.min(height, g.contentBottom() - 6 - top - below));
    }

    @Override
    public boolean spread() {
        return true;
    }

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        G g = G.of(layout);
        Component heading = title.isPresent() && !title.get().isEmpty() ? FoliosText.inline(title.get()) : null;
        int top = FoliosLayout.CONTENT_TOP + (heading != null ? FoliosLayout.TITLE_HEIGHT : 0);
        Map<BlockPos, BlockChoice> blocks = this.blocks(layout);
        Requirements needs = blocks == null || blocks.isEmpty() ? null : new Requirements(blocks.values());
        int listTop = top + this.windowHeight(g) + 5;
        int textTop = needs != null ? listTop + Requirements.HEADING + Requirements.SLOT + 5 : top + this.windowHeight(g) + 6;
        List<FormattedCharSequence> caption = text.isEmpty() ? List.of() : Minecraft.getInstance().font.split(FoliosText.inline(text), g.width() - 24);
        List<FormattedCharSequence> under = caption.subList(0, Math.min(caption.size(), Math.max(0, (g.contentBottom() - textTop) / 10)));
        Window window = this.window(layout, top, g, blocks);
        Component needed = needs == null ? null : Component.translatableWithFallback("folios.requires", "Blocks needed")
                .append(" \u00B7 " + needs.total()).withStyle(net.minecraft.ChatFormatting.ITALIC);
        TextPlate plate = heading == null && under.isEmpty() && needed == null ? null : new TextPlate(layout, g, heading, needed, listTop, under, textTop);
        return List.of(new Half(painter -> this.half(new Part(painter, 0, g), window, plate, needs, heading != null, top, listTop)),
                new Half(painter -> this.half(new Part(painter, 1, g), window, plate, needs, heading != null, top, listTop)));
    }

    private void half(Part part, Window window, @Nullable TextPlate plate, @Nullable Requirements needs, boolean titled, int top, int listTop) {
        this.window(window, part, titled, top);
        if (needs != null) part.requirements(needs, listTop + Requirements.HEADING);
        if (plate != null) plate.draw(part);
    }

    private static final int NEEDS_SHOWN = 12;

    private static final class TextPlate {
        private final SceneViewport view;
        private final G g;
        private final @Nullable Component heading;
        private final @Nullable Component needed;
        private final int neededTop;
        private final List<FormattedCharSequence> lines;
        private final int textTop;
        private int laidFor;

        TextPlate(FoliosLayout layout, G g, @Nullable Component heading, @Nullable Component needed, int neededTop, List<FormattedCharSequence> lines, int textTop) {
            this.g = g;
            this.view = layout.viewport(g.pw() * 2 * FoliosLayout.SCENE_DETAIL, g.ph() * FoliosLayout.SCENE_DETAIL);
            view.spin(0).refreshRate(1);
            this.heading = heading;
            this.needed = needed;
            this.neededTop = neededTop;
            this.lines = lines;
            this.textTop = textTop;
        }

        void draw(Part part) {
            FoliosPalette palette = part.painter.palette();
            int colours = palette.heading() * 31 + palette.text();
            if (laidFor != colours || view.texts().isEmpty()) {
                laidFor = colours;
                view.clearText();
                net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
                float detail = FoliosLayout.SCENE_DETAIL;
                if (heading != null) {
                    FormattedCharSequence bold = heading.copy().withStyle(net.minecraft.ChatFormatting.BOLD).getVisualOrderText();
                    view.text(bold, (g.pw() - font.width(bold) / 2.0F) * detail, FoliosLayout.CONTENT_TOP * detail, palette.heading(), detail);
                }
                if (needed != null) {
                    FormattedCharSequence line = needed.getVisualOrderText();
                    view.text(line, (g.pw() - font.width(line) / 2.0F) * detail, neededTop * detail, palette.muted(), detail);
                }
                int y = textTop;
                for (FormattedCharSequence line : lines) {
                    view.text(line, (g.pw() - font.width(line) / 2.0F) * detail, y * detail, palette.text(), detail);
                    y += 10;
                }
            }
            part.plate(view);
        }
    }

    private void window(Window window, Part part, boolean titled, int top) {
        G g = part.g();
        if (titled) part.rule(FoliosLayout.CONTENT_TOP);
        part.frame(LEFT, top, LEFT + g.width(), top + this.windowHeight(g));
        window.draw(part);
    }

    private record Half(Laid content) implements SpreadHalf {
        @Override
        public void draw(FoliosPainter painter) {
            content.draw(painter);
        }
    }

    private @Nullable Map<BlockPos, BlockChoice> blocks(FoliosLayout layout) {
        if (multiblock.isPresent()) return MultiblockPageElement.blocks(multiblock.get().pattern(), multiblock.get().mapping());
        if (build.isEmpty()) return null;
        ResourceLocation book = layout.book().id();
        String named = build.get();
        ResourceLocation id = named.contains(":") ? ResourceLocation.tryParse(named) : book.withPath(book.getPath() + "/builds/" + named);
        com.google.gson.JsonObject json = id == null ? null : net.ixdarklord.folios.internal.client.FoliosLibrary.build(id);
        if (json == null) {
            net.ixdarklord.folios.internal.core.Folios.LOGGER.error("A spread of the guide {} names the build {}, which it doesn't have", book, named);
            return Map.of();
        }
        try {
            return BuildPageElement.read(json);
        } catch (Exception e) {
            net.ixdarklord.folios.internal.core.Folios.LOGGER.error("Couldn't read the build {} of the guide {}: {}", id, book, e.getMessage());
            return Map.of();
        }
    }

    private Window window(FoliosLayout layout, int top, G g, @Nullable Map<BlockPos, BlockChoice> blocks) {
        if (blocks != null) return this.buildWindow(layout, top, blocks, g);
        if (map.isPresent()) return this.mapWindow(top, map.get(), g);
        ResourceLocation picture = image.orElse(null);
        return part -> {
            if (picture != null) part.texture(picture, LEFT + INNER, top + INNER, g.width() - INNER * 2, this.windowHeight(g) - INNER * 2);
        };
    }

    private Window buildWindow(FoliosLayout layout, int top, Map<BlockPos, BlockChoice> blocks, G g) {
        SceneViewport view = layout.viewport(g.width() * FoliosLayout.SCENE_DETAIL, this.windowHeight(g) * FoliosLayout.SCENE_DETAIL);
        view.refreshRate(240);
        BuildScene scene = new BuildScene(view);
        scene.show(blocks);
        view.ground(0, 1, 0x30808080, 0x55A0A0A0, false);
        SceneControl control = new SceneControl(view, 35, 26, 14);
        Component name = title.isPresent() ? FoliosText.inline(title.get()) : Component.translatableWithFallback("folios.build", "Build");
        SceneControl.Extra show = new SceneControl.Extra(Component.translatableWithFallback("folios.build.show", "Show in the world"), () -> {
            BuildGuide.start(name, blocks);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) minecraft.screen.onClose();
        });
        return part -> {
            int[] range = part.clip(LEFT + INNER, LEFT + g.width() - INNER);
            if (range == null) return;
            scene.update(part.painter.time());
            float u0 = (range[0] - LEFT - INNER) / (float) (g.width() - INNER * 2);
            float u1 = (range[1] - LEFT - INNER) / (float) (g.width() - INNER * 2);
            control.drawPart(part.painter, range[0] - part.offset(), top + INNER, range[1] - range[0], this.windowHeight(g) - INNER * 2, u0, u1,
                    part.side == 0, part.side == 1, Shapes.Icon.EYE, show);
        };
    }

    private Window mapWindow(int top, MapLayout map, G g) {
        Map<String, Node> byId = new HashMap<>();
        Map<String, FoliosIcon> icons = new HashMap<>();
        Map<String, int[]> at = new HashMap<>();
        for (Node node : map.nodes()) {
            byId.put(node.id(), node);
            icons.put(node.id(), FoliosIcon.read(node.icon()));
            int x = Mth.clamp(LEFT + node.x(), LEFT + INNER + NODE / 2 + 1, LEFT + g.width() - INNER - NODE / 2 - 3);
            int y = Mth.clamp(top + node.y(), top + INNER + NODE / 2 + 1, top + this.windowHeight(g) - INNER - NODE / 2 - 3);
            if (x - NODE / 2 - 1 < g.pw() && x + NODE / 2 + 2 > g.pw()) x = x < g.pw() ? g.pw() - NODE / 2 - 3 : g.pw() + NODE / 2 + 2;
            at.put(node.id(), new int[]{x, y});
        }
        return part -> {
            FoliosPalette palette = part.painter.palette();
            map.background().ifPresent(background -> part.texture(background, LEFT + INNER, top + INNER, g.width() - INNER * 2, this.windowHeight(g) - INNER * 2));
            int ink = FoliosPalette.mix(palette.line(), palette.accent(), 0.5F);
            for (List<String> path : map.paths()) {
                Node from = byId.get(path.get(0));
                Node to = byId.get(path.get(1));
                if (from == null || to == null) continue;
                int[] a = at.get(from.id());
                int[] b = at.get(to.id());
                float x0 = a[0], y0 = a[1], x1 = b[0], y1 = b[1];
                float length = (float) Math.hypot(x1 - x0, y1 - y0);
                for (float along = NODE / 2.0F; along < length - NODE / 2.0F; along += 4) {
                    int x = Math.round(x0 + (x1 - x0) * along / length);
                    int y = Math.round(y0 + (y1 - y0) * along / length);
                    part.fill(x - 1, y - 1, x + 1, y + 1, ink);
                }
            }
            for (Node node : map.nodes()) {
                int x = at.get(node.id())[0] - NODE / 2;
                int y = at.get(node.id())[1] - NODE / 2;
                part.fill(x + 1, y + 2, x + NODE + 1, y + NODE + 2, FoliosPalette.mix(palette.panel(), palette.text(), 0.22F));
                part.fill(x, y, x + NODE, y + NODE, FoliosPalette.mix(palette.line(), palette.accent(), 0.35F));
                part.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, FoliosPalette.mix(palette.panel(), 0xFFFFFFFF, 0.45F));
                part.fill(x + 2, y + 2, x + NODE - 1, y + NODE - 1, palette.panel());
                int middle = x + NODE / 2;
                if (!part.holds(middle)) continue;
                FoliosPainter painter = part.painter;
                int px = x - part.offset();
                boolean over = false;
                if (node.entry().isPresent()) {
                    over = painter.link(px, y, NODE, NODE, "entry:" + node.entry().get());
                }
                if (over) painter.fill(px + 1, y + 1, px + NODE - 1, y + NODE - 1, FoliosPalette.alpha(palette.accent(), 0x40));
                icons.get(node.id()).draw(painter, px + 2, y + 2);
                if (node.label().isPresent() && painter.hovered(px, y, NODE, NODE)) painter.tooltip(FoliosText.inline(node.label().get()));
            }
        };
    }

    @Override
    public String searchText() {
        StringBuilder words = new StringBuilder(FoliosText.resolve(title.orElse(""))).append(' ').append(FoliosText.resolve(text));
        map.ifPresent(layout -> layout.nodes().forEach(node -> node.label().ifPresent(name -> words.append(' ').append(FoliosText.resolve(name)))));
        return words.toString();
    }

    @FunctionalInterface
    private interface Window {
        void draw(Part part);
    }

    private record Part(FoliosPainter painter, int side, G g) {
        private int gap() {
            return painter.flat() ? Shapes.SCREEN_SPINE : 0;
        }

        int offset() {
            return side == 0 ? -this.gap() / 2 : g.pw() + this.gap() / 2;
        }

        boolean holds(int x) {
            return side == 0 ? x < g.pw() : x >= g.pw();
        }

        int @Nullable [] clip(int x0, int x1) {
            int from = side == 0 ? x0 : Math.max(x0, g.pw());
            int to = side == 0 ? Math.min(x1, g.pw()) : x1;
            return from < to ? new int[]{from, to} : null;
        }

        void rule(int y) {
            FoliosPalette palette = painter.palette();
            this.fill(LEFT, y + 11, LEFT + g.width(), y + 12, palette.line());
            this.fill(g.pw() - 12, y + 11, g.pw() + 12, y + 12, palette.accent());
        }

        void requirements(Requirements needs, int y) {
            List<Requirements.Need> shown = needs.shown(NEEDS_SHOWN);
            int onLeft = (shown.size() + 1) / 2;
            for (int index = 0; index < shown.size(); index++) {
                boolean left = index < onLeft;
                if (left != (side == 0)) continue;
                int x = left ? g.pw() - 3 - (onLeft - index) * Requirements.SLOT : g.pw() + 3 + (index - onLeft) * Requirements.SLOT;
                Requirements.slot(painter, shown.get(index), x - this.offset(), y);
            }
            if (!needs.paged(NEEDS_SHOWN)) return;
            int arrowY = y + (Requirements.SLOT - Shapes.BUTTON) / 2;
            if (side == 0) needs.stepper(painter, LEFT + 2 - this.offset(), arrowY, false);
            else needs.stepper(painter, LEFT + g.width() - Shapes.BUTTON - 2 - this.offset(), arrowY, true);
        }

        void plate(SceneViewport view) {
            int[] range = this.clip(0, g.pw() * 2);
            if (range == null) return;
            painter.scene(view, range[0] - this.offset(), 0, range[1] - range[0], g.ph(), range[0] / (g.pw() * 2.0F), range[1] / (g.pw() * 2.0F));
        }

        void fill(int x0, int y0, int x1, int y1, int argb) {
            int[] range = this.clip(x0, x1);
            if (range != null) painter.fill(range[0] - offset(), y0, range[1] - offset(), y1, argb);
        }

        void frame(int x0, int y0, int x1, int y1) {
            FoliosPalette palette = painter.palette();
            this.fill(x0 + 2, y1, x1 + 2, y1 + 2, 0x30000000);
            this.fill(x1, y0 + 2, x1 + 2, y1, 0x30000000);
            this.fill(x0 + 3, y1 + 2, x1 + 3, y1 + 3, 0x16000000);
            this.fill(x1 + 2, y0 + 3, x1 + 3, y1 + 2, 0x16000000);
            this.fill(x0, y0, x1, y1, FoliosPalette.mix(palette.line(), palette.text(), 0.3F));
            this.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, FoliosPalette.mix(palette.panel(), 0xFFFFFFFF, 0.4F));
            this.fill(x0 + INNER, y0 + INNER, x1 - INNER, y1 - INNER, palette.panel());
        }

        void texture(ResourceLocation texture, int x, int y, int width, int height) {
            int[] range = this.clip(x, x + width);
            if (range == null) return;
            float u0 = (range[0] - x) / (float) width;
            float u1 = (range[1] - x) / (float) width;
            painter.texture(texture, range[0] - offset(), y, range[1] - range[0], height, u0, 0, u1, 1, 0xFFFFFFFF);
        }
    }
}
