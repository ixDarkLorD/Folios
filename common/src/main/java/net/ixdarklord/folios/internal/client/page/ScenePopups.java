package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonObject;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.FoliosLinks;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPopup;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.FoliosStructures;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Folios's own popup links: a window over the book showing what the link names, turning, as the page elements of the
 * same kind show it.
 * <ul>
 *     <li>{@code [Blaze](entity:minecraft:blaze)}, or the tag {@code {entity:minecraft:blaze}}: an entity;</li>
 *     <li>{@code [the igloo](structure:minecraft:igloo/top)}: a structure, asked of the server;</li>
 *     <li>{@code [the tower](build:tower)}: a build file of the book's (or {@code build:mymod:my_guide/builds/tower});</li>
 *     <li>{@code [the shrine](multiblock:world/builds#shrine)}: the multiblock on an entry's page with that marker (the
 *     entry's first multiblock without one).</li>
 * </ul>
 */
public final class ScenePopups {
    /** The body of a scene's window, in page pixels. */
    private static final int WIDTH = 124;
    private static final int HEIGHT = 84;

    private ScenePopups() {}

    /** Registers the four kinds (once, as the client starts). */
    public static void register() {
        FoliosLinks.register("entity", new FoliosLinks.Kind() {
            @Override
            public FoliosPopup popup(FoliosLayout layout, String target) {
                ResourceLocation id = ResourceLocation.tryParse(target);
                EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                return type == null ? null : entity(layout, type);
            }
        });
        FoliosLinks.register("structure", new FoliosLinks.Kind() {
            @Override
            public FoliosPopup popup(FoliosLayout layout, String target) {
                ResourceLocation id = ResourceLocation.tryParse(target);
                return id == null ? null : structure(layout, id);
            }
        });
        FoliosLinks.register("build", new FoliosLinks.Kind() {
            @Override
            public FoliosPopup popup(FoliosLayout layout, String target) {
                return build(layout, target);
            }
        });
        FoliosLinks.register("multiblock", new FoliosLinks.Kind() {
            @Override
            public FoliosPopup popup(FoliosLayout layout, String target) {
                return multiblock(layout, target);
            }

            @Override
            public boolean isOpen(FoliosBook book, String target) {
                FoliosEntry entry = book.entry(target.contains("#") ? target.substring(0, target.indexOf('#')) : target);
                return entry != null && entry.isOpen();
            }
        });
    }

    private static FoliosPopup entity(FoliosLayout layout, EntityType<?> type) {
        SceneViewport view = viewport(layout);
        view.ground(0, 1, 0x30808080, 0x55A0A0A0, true);
        SceneControl control = new SceneControl(view, 45, 30, 24);
        Entity[] shown = {null};
        return new Popup(type.getDescription(), (painter, pinned) -> {
            if (shown[0] == null && Minecraft.getInstance().level != null) {
                shown[0] = type.create(Minecraft.getInstance().level);
                if (shown[0] != null) {
                    // Never in the world, but drawn as if it were: an id of its own, as an entity page's.
                    shown[0].setId(EntityPageElement.SHOWN_IDS.decrementAndGet());
                    PreviewEntities.track(shown[0]);
                    // Framed as the entity element frames it: whole, turned corner-on, with a little room round it.
                    view.entity(shown[0], 0, 0, 0, 0);
                    float width = shown[0].getBbWidth() * 1.45F;
                    float tall = shown[0].getBbHeight() * (WIDTH / (float) HEIGHT);
                    view.frame(Math.max(Math.max(width, tall) * 1.75F, 2.2F), 0, shown[0].getBbHeight() * 0.52, 0);
                }
            }
            window(painter, control, pinned);
        });
    }

    private static FoliosPopup structure(FoliosLayout layout, ResourceLocation id) {
        Component loading = Component.translatableWithFallback("folios.structure.loading", "Loading the structure…");
        Component missing = Component.translatableWithFallback("folios.structure.missing", "No structure %s", id.toString());
        Component large = Component.translatableWithFallback("folios.structure.too_large", "%s is too large to show", id.toString());
        return blocks(layout, Component.literal(prettyName(id.getPath())), () -> {
            FoliosStructures.Known known = FoliosStructures.get(id);
            if (known == null) return Showcase.State.saying(loading);
            return switch (known.status()) {
                case FoliosPayloads.Structure.FOUND -> Showcase.State.of(known.blocks());
                case FoliosPayloads.Structure.TOO_LARGE -> Showcase.State.saying(large);
                default -> Showcase.State.saying(missing);
            };
        });
    }

    private static @Nullable FoliosPopup build(FoliosLayout layout, String target) {
        ResourceLocation book = layout.book().id();
        ResourceLocation id = target.contains(":") ? ResourceLocation.tryParse(target) : book.withPath(book.getPath() + "/builds/" + target);
        JsonObject json = id == null ? null : FoliosLibrary.build(id);
        if (json == null) return null;
        Component name = json.has("name") ? FoliosText.inline(json.get("name").getAsString()) : Component.literal(prettyName(target));
        Showcase.State state;
        try {
            state = Showcase.State.of(BuildPageElement.read(json));
        } catch (Exception e) {
            state = Showcase.State.saying(Component.translatableWithFallback("folios.build.unreadable", "The build %s couldn't be read", target));
        }
        Showcase.State known = state;
        return blocks(layout, name, () -> known);
    }

    // The multiblock on an entry's page: the one with the marker, else the entry's first.
    private static @Nullable FoliosPopup multiblock(FoliosLayout layout, String target) {
        int hash = target.indexOf('#');
        FoliosEntry entry = layout.book().entry(hash >= 0 ? target.substring(0, hash) : target);
        if (entry == null) return null;
        String marker = hash >= 0 ? target.substring(hash + 1) : null;
        for (FoliosEntry.Page page : entry.pages()) {
            if (marker != null && !marker.equals(page.marker())) continue;
            MultiblockPageElement found = find(page.page());
            if (found == null) continue;
            Component name = found.title().map(FoliosText::inline).orElse(entry.title());
            Showcase.State state = Showcase.State.of(MultiblockPageElement.blocks(found.pattern(), found.mapping()));
            return blocks(layout, name, () -> state);
        }
        return null;
    }

    private static @Nullable MultiblockPageElement find(FoliosPageElement page) {
        if (page instanceof MultiblockPageElement multiblock) return multiblock;
        if (page instanceof TextPage text) {
            for (TextPage.Item item : text.elements()) if (item.element() instanceof MultiblockPageElement multiblock) return multiblock;
        }
        return null;
    }

    // A window of blocks: a structure's, a build's or a multiblock's.
    private static FoliosPopup blocks(FoliosLayout layout, Component title, Supplier<Showcase.State> state) {
        SceneViewport view = viewport(layout);
        view.ground(0, 1, 0x30808080, 0x55A0A0A0, false);
        BuildScene scene = new BuildScene(view);
        SceneControl control = new SceneControl(view, 35, 28, 18);
        @SuppressWarnings("unchecked") Map<BlockPos, BlockChoice>[] shown = new Map[1];
        return new Popup(title, (painter, pinned) -> {
            Showcase.State now = state.get();
            if (now.blocks() != shown[0]) {
                shown[0] = now.blocks();
                scene.show(shown[0] != null ? shown[0] : Map.of());
            }
            if (shown[0] == null || shown[0].isEmpty()) {
                Shapes.panel(painter, 0, 0, WIDTH, HEIGHT);
                Component message = now.message() != null ? now.message() : Component.translatableWithFallback("folios.showcase.empty", "Nothing to show");
                List<net.minecraft.util.FormattedCharSequence> lines = painter.font().split(message, WIDTH - 12);
                int top = HEIGHT / 2 - lines.size() * 10 / 2;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    painter.text(line, WIDTH / 2 - painter.font().width(line) / 2, top, painter.palette().muted());
                    top += 10;
                }
                return;
            }
            scene.update(painter.time());
            window(painter, control, pinned);
        });
    }

    private static SceneViewport viewport(FoliosLayout layout) {
        SceneViewport view = layout.viewport(WIDTH * FoliosLayout.SCENE_DETAIL, HEIGHT * FoliosLayout.SCENE_DETAIL);
        // Drawn every frame: a scene drawn fewer times turns in uneven steps.
        view.refreshRate(240);
        return view;
    }

    // The scene's framed window; turned and zoomed by the reader only while pinned.
    private static void window(FoliosPainter painter, SceneControl control, boolean pinned) {
        Shapes.panel(painter, 0, 0, WIDTH, HEIGHT);
        if (pinned) control.draw(painter, 1, 1, WIDTH - 2, HEIGHT - 2);
        else control.look(painter, 1, 1, WIDTH - 2, HEIGHT - 2);
    }

    // "igloo/top" as "Igloo Top": a name for what has none of its own.
    private static String prettyName(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.replace('/', ' ').replace('_', ' ').split(" ")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private interface Body {
        void draw(FoliosPainter painter, boolean pinned);
    }

    private record Popup(Component title, Body body) implements FoliosPopup {
        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return HEIGHT;
        }

        @Override
        public void draw(FoliosPainter painter, boolean pinned) {
            body.draw(painter, pinned);
        }
    }
}
