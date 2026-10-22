package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.GuideIcon;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideText;
import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// One thing over both pages of the open book, a window across the fold: a wide picture ("image"), a build turning in
// the world's light ("build", as a multiblock page's pattern and mapping), or a map of the book's entries ("map": nodes
// placed in the window, each a link to an entry, joined by dotted paths). The title is centred over it across the fold,
// and the text under it, in centred lines. It runs unbroken over the fold: on a screen book, whose pages have the
// spine between them, each page draws on into the spine to meet the other. In the window's own pixels a node at x 0 is at its left edge, at the window's
// width (the spread's, less the margins) its right.
public record SpreadPage(Optional<String> title, String text, Optional<Identifier> image, Optional<Build> build, Optional<MapLayout> map, int height)
        implements GuidePage {
    /** A build in the window: a multiblock page's pattern and mapping. */
    public record Build(List<List<String>> pattern, Map<String, String> mapping) {
        static final Codec<Build> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().listOf().fieldOf("pattern").forGetter(Build::pattern),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("mapping").forGetter(Build::mapping)
        ).apply(instance, Build::new));
    }

    /** A place on the map: where it is in the window, what it shows, and the entry it opens. */
    public record Node(String id, int x, int y, JsonElement icon, Optional<String> entry, Optional<String> name) {
        static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Node::id),
                Codec.INT.fieldOf("x").forGetter(Node::x),
                Codec.INT.fieldOf("y").forGetter(Node::y),
                ExtraCodecs.JSON.optionalFieldOf("icon", new com.google.gson.JsonPrimitive("minecraft:book")).forGetter(Node::icon),
                Codec.STRING.optionalFieldOf("entry").forGetter(Node::entry),
                Codec.STRING.optionalFieldOf("name").forGetter(Node::name)
        ).apply(instance, Node::new));
    }

    /** The map: its places, the paths between them (pairs of ids), and a picture under them. */
    public record MapLayout(List<Node> nodes, List<List<String>> paths, Optional<Identifier> background) {
        static final Codec<MapLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Node.CODEC.listOf().fieldOf("nodes").forGetter(MapLayout::nodes),
                Codec.STRING.listOf(2, 2).listOf().optionalFieldOf("paths", List.of()).forGetter(MapLayout::paths),
                Identifier.CODEC.optionalFieldOf("background").forGetter(MapLayout::background)
        ).apply(instance, MapLayout::new));
    }

    public static final MapCodec<SpreadPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("title").forGetter(SpreadPage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(SpreadPage::text),
            Identifier.CODEC.optionalFieldOf("image").forGetter(SpreadPage::image),
            Build.CODEC.optionalFieldOf("build").forGetter(SpreadPage::build),
            MapLayout.CODEC.optionalFieldOf("map").forGetter(SpreadPage::map),
            Codec.intRange(40, 150).optionalFieldOf("height", 104).forGetter(SpreadPage::height)
    ).apply(instance, SpreadPage::new));

    private static final int PW = GuideLayout.PAGE_WIDTH;
    /** The window's left edge and width, in the spread's pixels (0 at the left page's left edge). */
    private static final int LEFT = GuideLayout.CONTENT_LEFT;
    private static final int WIDTH = PW * 2 - GuideLayout.CONTENT_LEFT * 2;
    private static final int NODE = 20;
    /** How far the window's content sits inside its frame. */
    private static final int INNER = 2;

    // How tall the window is: as the page asks, but a build's leaves room under it for the blocks it needs and a line
    // of its text above the page number.
    private int windowHeight() {
        if (build.isEmpty()) return height;
        int top = GuideLayout.CONTENT_TOP + (title.isPresent() && !title.get().isEmpty() ? GuideLayout.TITLE_HEIGHT : 0);
        int below = 5 + Requirements.HEADING + Requirements.SLOT + (text.isEmpty() ? 0 : 5 + 10);
        return Math.max(40, Math.min(height, GuideLayout.CONTENT_BOTTOM - 6 - top - below));
    }

    @Override
    public boolean spread() {
        return true;
    }

    @Override
    public List<Laid> lay(GuideLayout layout) {
        Component heading = title.isPresent() && !title.get().isEmpty() ? GuideText.inline(title.get()) : null;
        int top = GuideLayout.CONTENT_TOP + (heading != null ? GuideLayout.TITLE_HEIGHT : 0);
        // A build's blocks, a row under its window, the text under them.
        Requirements needs = build.map(made -> {
            Map<BlockPos, BlockState> blocks = MultiblockPage.blocks(made.pattern(), made.mapping());
            Map<BlockState, Integer> counts = new java.util.LinkedHashMap<>();
            for (BlockState state : blocks.values()) counts.merge(state, 1, Integer::sum);
            return new Requirements(counts);
        }).filter(made -> !made.isEmpty()).orElse(null);
        int listTop = top + this.windowHeight() + 5;
        int textTop = needs != null ? listTop + Requirements.HEADING + Requirements.SLOT + 5 : top + this.windowHeight() + 6;
        // The text under the window, in centred lines across the spread, as many as there is room for.
        List<FormattedCharSequence> caption = text.isEmpty() ? List.of() : Minecraft.getInstance().font.split(GuideText.inline(text), WIDTH - 24);
        List<FormattedCharSequence> under = caption.subList(0, Math.min(caption.size(), Math.max(0, (GuideLayout.CONTENT_BOTTOM - textTop) / 10)));
        Window window = this.window(layout, top);
        Component needed = needs == null ? null : Component.translatableWithFallback("folios.requires", "Blocks needed")
                .append(" \u00B7 " + needs.total()).withStyle(net.minecraft.ChatFormatting.ITALIC);
        TextPlate plate = heading == null && under.isEmpty() && needed == null ? null : new TextPlate(layout, heading, needed, listTop, under, textTop);
        return List.of(new Half(painter -> this.half(new Part(painter, 0), window, plate, needs, heading != null, top, listTop)),
                new Half(painter -> this.half(new Part(painter, 1), window, plate, needs, heading != null, top, listTop)));
    }

    // One page's half: the rule under the title, the window, its half of the blocks needed, and of the text plate.
    private void half(Part part, Window window, @Nullable TextPlate plate, @Nullable Requirements needs, boolean titled, int top, int listTop) {
        this.window(window, part, titled, top);
        if (needs != null) part.requirements(needs, listTop + Requirements.HEADING);
        if (plate != null) plate.draw(part);
    }

    /** How many kinds of block a build spread shows under its window at once. */
    private static final int NEEDS_SHOWN = 12;

    // The spread's words, its title and the text under its window, laid on one picture across both pages (a scene
    // viewport of nothing but text) that each page shows its half of. So the words are cut exactly at the fold, as the
    // window is, and bend and turn with their page as a picture does, rather than letters being sorted to a side.
    // The picture is made again only when the paper's colours change (light or dark).
    private static final class TextPlate {
        private final SceneViewport view;
        private final @Nullable Component heading;
        private final @Nullable Component needed;
        private final int neededTop;
        private final List<FormattedCharSequence> lines;
        private final int textTop;
        private int laidFor;

        TextPlate(GuideLayout layout, @Nullable Component heading, @Nullable Component needed, int neededTop, List<FormattedCharSequence> lines, int textTop) {
            this.view = layout.viewport(PW * 2 * GuideLayout.SCENE_DETAIL, GuideLayout.PAGE_HEIGHT * GuideLayout.SCENE_DETAIL);
            view.spin(0).refreshRate(1);
            this.heading = heading;
            this.needed = needed;
            this.neededTop = neededTop;
            this.lines = lines;
            this.textTop = textTop;
        }

        void draw(Part part) {
            GuidePalette palette = part.painter.palette();
            int colours = palette.heading() * 31 + palette.text();
            if (laidFor != colours || view.texts().isEmpty()) {
                laidFor = colours;
                view.clearText();
                net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
                float detail = GuideLayout.SCENE_DETAIL;
                if (heading != null) {
                    FormattedCharSequence bold = heading.copy().withStyle(net.minecraft.ChatFormatting.BOLD).getVisualOrderText();
                    view.text(bold, (PW - font.width(bold) / 2.0F) * detail, GuideLayout.CONTENT_TOP * detail, palette.heading(), detail);
                }
                if (needed != null) {
                    FormattedCharSequence line = needed.getVisualOrderText();
                    view.text(line, (PW - font.width(line) / 2.0F) * detail, neededTop * detail, palette.muted(), detail);
                }
                int y = textTop;
                for (FormattedCharSequence line : lines) {
                    view.text(line, (PW - font.width(line) / 2.0F) * detail, y * detail, palette.text(), detail);
                    y += 10;
                }
            }
            part.plate(view);
        }
    }

    // The rule under the title, then the window on one page: its frame and what is in it.
    private void window(Window window, Part part, boolean titled, int top) {
        if (titled) part.rule(GuideLayout.CONTENT_TOP);
        part.frame(LEFT, top, LEFT + WIDTH, top + this.windowHeight());
        window.draw(part);
    }

    // One page of the spread.
    private record Half(Laid content) implements SpreadHalf {
        @Override
        public void draw(GuidePainter painter) {
            content.draw(painter);
        }
    }

    private Window window(GuideLayout layout, int top) {
        if (build.isPresent()) return this.buildWindow(layout, top, build.get());
        if (map.isPresent()) return this.mapWindow(top, map.get());
        Identifier picture = image.orElse(null);
        return part -> {
            if (picture != null) part.texture(picture, LEFT + INNER, top + INNER, WIDTH - INNER * 2, this.windowHeight() - INNER * 2);
        };
    }

    private Window buildWindow(GuideLayout layout, int top, Build build) {
        Map<BlockPos, BlockState> blocks = MultiblockPage.blocks(build.pattern(), build.mapping());
        SceneViewport view = layout.viewport(WIDTH * GuideLayout.SCENE_DETAIL, this.windowHeight() * GuideLayout.SCENE_DETAIL);
        view.blocks(blocks).refreshRate(30);
        view.ground(0, 1, 0x30808080, 0x55A0A0A0, false);
        SceneControl control = new SceneControl(view, 35, 26, 14);
        Component name = title.isPresent() ? GuideText.inline(title.get()) : Component.translatableWithFallback("folios.build", "Build");
        SceneControl.Extra show = new SceneControl.Extra(Component.translatableWithFallback("folios.build.show", "Show in the world"), () -> {
            BuildGuide.start(name, blocks);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) minecraft.screen.onClose();
        });
        return part -> {
            int[] range = part.clip(LEFT + INNER, LEFT + WIDTH - INNER);
            if (range == null) return;
            float u0 = (range[0] - LEFT - INNER) / (float) (WIDTH - INNER * 2);
            float u1 = (range[1] - LEFT - INNER) / (float) (WIDTH - INNER * 2);
            control.drawPart(part.painter, range[0] - part.offset(), top + INNER, range[1] - range[0], this.windowHeight() - INNER * 2, u0, u1,
                    part.side == 0, part.side == 1, Shapes.Icon.EYE, show);
        };
    }

    private Window mapWindow(int top, MapLayout map) {
        Map<String, Node> byId = new HashMap<>();
        Map<String, GuideIcon> icons = new HashMap<>();
        // Where each place sits, in the spread's pixels: kept inside the frame, its plate and shadow too, and off the
        // fold, so neither page cuts it.
        Map<String, int[]> at = new HashMap<>();
        for (Node node : map.nodes()) {
            byId.put(node.id(), node);
            icons.put(node.id(), GuideIcon.read(node.icon()));
            int x = Math.clamp(LEFT + node.x(), LEFT + INNER + NODE / 2 + 1, LEFT + WIDTH - INNER - NODE / 2 - 3);
            int y = Math.clamp(top + node.y(), top + INNER + NODE / 2 + 1, top + this.windowHeight() - INNER - NODE / 2 - 3);
            if (x - NODE / 2 - 1 < PW && x + NODE / 2 + 2 > PW) x = x < PW ? PW - NODE / 2 - 3 : PW + NODE / 2 + 2;
            at.put(node.id(), new int[]{x, y});
        }
        return part -> {
            GuidePalette palette = part.painter.palette();
            map.background().ifPresent(background -> part.texture(background, LEFT + INNER, top + INNER, WIDTH - INNER * 2, this.windowHeight() - INNER * 2));
            // The paths first, dotted, under the places they join.
            int ink = GuidePalette.mix(palette.line(), palette.accent(), 0.5F);
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
            // Each place: a framed slot, its icon, a link to its entry, on the page its middle is over.
            for (Node node : map.nodes()) {
                int x = at.get(node.id())[0] - NODE / 2;
                int y = at.get(node.id())[1] - NODE / 2;
                // A plate standing a little off the map: its shadow under it, a rim, a pale line inside the rim.
                part.fill(x + 1, y + 2, x + NODE + 1, y + NODE + 2, GuidePalette.mix(palette.panel(), palette.text(), 0.22F));
                part.fill(x, y, x + NODE, y + NODE, GuidePalette.mix(palette.line(), palette.accent(), 0.35F));
                part.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, GuidePalette.mix(palette.panel(), 0xFFFFFFFF, 0.45F));
                part.fill(x + 2, y + 2, x + NODE - 1, y + NODE - 1, palette.panel());
                int middle = x + NODE / 2;
                if (!part.holds(middle)) continue;
                GuidePainter painter = part.painter;
                int px = x - part.offset();
                boolean over = false;
                if (node.entry().isPresent()) {
                    over = painter.link(px, y, NODE, NODE, "entry:" + node.entry().get());
                }
                if (over) painter.fill(px + 1, y + 1, px + NODE - 1, y + NODE - 1, GuidePalette.alpha(palette.accent(), 0x40));
                icons.get(node.id()).draw(painter, px + 2, y + 2);
                if (node.name().isPresent() && painter.hovered(px, y, NODE, NODE)) painter.tooltip(GuideText.inline(node.name().get()));
            }
        };
    }

    @Override
    public String searchText() {
        // Each piece in its words: any of them can be a translation key.
        StringBuilder words = new StringBuilder(GuideText.resolve(title.orElse(""))).append(' ').append(GuideText.resolve(text));
        map.ifPresent(layout -> layout.nodes().forEach(node -> node.name().ifPresent(name -> words.append(' ').append(GuideText.resolve(name)))));
        return words.toString();
    }

    @FunctionalInterface
    private interface Window {
        void draw(Part part);
    }

    // One page's share of the spread: what lies over it is drawn, cut at the fold, moved onto it. `side` 0 is the
    // left page (spread x from 0 to the fold at its width), 1 the right. On a screen book the pages have the spine
    // between them, so the spread is moved half the spine's width in on each and each page draws on half of it.
    private record Part(GuidePainter painter, int side) {
        private int gap() {
            return painter.flat() ? Shapes.SCREEN_SPINE : 0;
        }

        // Where spread x 0 is on this page, less: the page's x is the spread's less this.
        int offset() {
            return side == 0 ? -this.gap() / 2 : PW + this.gap() / 2;
        }

        boolean holds(int x) {
            return side == 0 ? x < PW : x >= PW;
        }

        // The part of a span on this page's side of the fold, in the spread's pixels; null when none of it is.
        int @Nullable [] clip(int x0, int x1) {
            int from = side == 0 ? x0 : Math.max(x0, PW);
            int to = side == 0 ? Math.min(x1, PW) : x1;
            return from < to ? new int[]{from, to} : null;
        }

        // The rule under the title, across the window, with a mark of the book's colour at the fold.
        void rule(int y) {
            GuidePalette palette = painter.palette();
            this.fill(LEFT, y + 11, LEFT + WIDTH, y + 12, palette.line());
            this.fill(PW - 12, y + 11, PW + 12, y + 12, palette.accent());
        }

        // The row of blocks a build needs, split either side of the fold so no slot is cut, a clasp at each end to
        // page through them when there are more than fit.
        void requirements(Requirements needs, int y) {
            List<net.minecraft.world.item.ItemStack> shown = needs.shown(NEEDS_SHOWN);
            int onLeft = (shown.size() + 1) / 2;
            for (int index = 0; index < shown.size(); index++) {
                boolean left = index < onLeft;
                if (left != (side == 0)) continue;
                int x = left ? PW - 3 - (onLeft - index) * Requirements.SLOT : PW + 3 + (index - onLeft) * Requirements.SLOT;
                Requirements.slot(painter, shown.get(index), x - this.offset(), y);
            }
            if (!needs.paged(NEEDS_SHOWN)) return;
            int arrowY = y + (Requirements.SLOT - Shapes.BUTTON) / 2;
            if (side == 0) needs.stepper(painter, LEFT + 2 - this.offset(), arrowY, false);
            else needs.stepper(painter, LEFT + WIDTH - Shapes.BUTTON - 2 - this.offset(), arrowY, true);
        }

        // This page's half of a picture as wide as the spread and as tall as a page.
        void plate(SceneViewport view) {
            int[] range = this.clip(0, PW * 2);
            if (range == null) return;
            painter.scene(view, range[0] - this.offset(), 0, range[1] - range[0], GuideLayout.PAGE_HEIGHT, range[0] / (PW * 2.0F), range[1] / (PW * 2.0F));
        }

        void fill(int x0, int y0, int x1, int y1, int argb) {
            int[] range = this.clip(x0, x1);
            if (range != null) painter.fill(range[0] - offset(), y0, range[1] - offset(), y1, argb);
        }

        // The window's frame, as a print laid on the page: a soft shadow under it and to its right, a dark rim, a pale
        // line inside it, and the ground. At the fold it has no side: rim and ground run on to the page's edge.
        void frame(int x0, int y0, int x1, int y1) {
            GuidePalette palette = painter.palette();
            this.fill(x0 + 2, y1, x1 + 2, y1 + 2, 0x30000000);
            this.fill(x1, y0 + 2, x1 + 2, y1, 0x30000000);
            this.fill(x0 + 3, y1 + 2, x1 + 3, y1 + 3, 0x16000000);
            this.fill(x1 + 2, y0 + 3, x1 + 3, y1 + 2, 0x16000000);
            this.fill(x0, y0, x1, y1, GuidePalette.mix(palette.line(), palette.text(), 0.3F));
            this.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GuidePalette.mix(palette.panel(), 0xFFFFFFFF, 0.4F));
            this.fill(x0 + INNER, y0 + INNER, x1 - INNER, y1 - INNER, palette.panel());
        }

        void texture(Identifier texture, int x, int y, int width, int height) {
            int[] range = this.clip(x, x + width);
            if (range == null) return;
            float u0 = (range[0] - x) / (float) width;
            float u1 = (range[1] - x) / (float) width;
            painter.texture(texture, range[0] - offset(), y, range[1] - range[0], height, u0, 0, u1, 1, 0xFFFFFFFF);
        }
    }
}
