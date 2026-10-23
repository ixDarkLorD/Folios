package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuideText;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// A build, turning in a window, with what it takes listed under it. `pattern` is its layers from the top down, each a
// list of rows; `mapping` names the block each character stands for (a space is air): a block state, or a tag of
// blocks any of which will do ("#minecraft:logs"), shown in turn.
public record MultiblockPageElement(List<List<String>> pattern, Map<String, String> mapping, Optional<String> title, String text, int height) implements GuidePage {
    public static final MapCodec<MultiblockPageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().listOf().fieldOf("pattern").forGetter(MultiblockPageElement::pattern),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("mapping").forGetter(MultiblockPageElement::mapping),
            Codec.STRING.optionalFieldOf("title").forGetter(MultiblockPageElement::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(MultiblockPageElement::text),
            Codec.intRange(40, 130).optionalFieldOf("height", 78).forGetter(MultiblockPageElement::height)
    ).apply(instance, MultiblockPageElement::new));

    @Override
    public List<Laid> lay(GuideLayout layout) {
        Body body = this.body(layout);
        return layout.flow(title.orElse(null), body.height(), text, body.part());
    }

    /** The blocks a pattern and its mapping make, by place: layers from the top down, rows, characters. */
    public static Map<BlockPos, BlockChoice> blocks(List<List<String>> pattern, Map<String, String> mapping) {
        Map<Character, BlockChoice> choices = new HashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (!entry.getKey().isEmpty()) choices.put(entry.getKey().charAt(0), BlockChoice.of(entry.getValue()));
        }
        Map<BlockPos, BlockChoice> blocks = new LinkedHashMap<>();
        for (int layer = 0; layer < pattern.size(); layer++) {
            List<String> rows = pattern.get(layer);
            int y = pattern.size() - 1 - layer;
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < row.length(); x++) {
                    BlockChoice choice = choices.get(row.charAt(x));
                    if (choice == null || choice.options().isEmpty() && !choice.isTag()) continue;
                    blocks.put(new BlockPos(x, y, z), choice);
                }
            }
        }
        return blocks;
    }

    /** What it shows between its title and its text. */
    Body body(GuideLayout layout) {
        Map<BlockPos, BlockChoice> blocks = blocks(pattern, mapping);
        int contentWidth = layout.contentWidth();
        int height = layout.scaled(this.height);
        SceneViewport view = layout.viewport(contentWidth * GuideLayout.SCENE_DETAIL, height * GuideLayout.SCENE_DETAIL);
        view.refreshRate(30);
        BuildScene scene = new BuildScene(view);
        scene.show(blocks);
        // Set on a ground of grid lines a block wider than it every way.
        view.ground(0, 1, 0x30808080, 0x55A0A0A0, false);
        SceneControl control = new SceneControl(view, 35, 28, 18);
        // The eye shows the build in the world, to be built: the book is put away and the guide takes over.
        Component buildName = title.isPresent() ? GuideText.inline(title.get()) : Component.translatableWithFallback("folios.build", "Build");
        SceneControl.Extra build = new SceneControl.Extra(Component.translatableWithFallback("folios.build.show", "Show in the world"), () -> {
            BuildGuide.start(buildName, blocks);
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.screen != null) minecraft.screen.onClose();
        });
        // What it takes, behind the clasp in the window's corner, laid over the window when pressed.
        Requirements needs = new Requirements(blocks.values());
        return new Body(height, (painter, y) -> {
            int left = GuideLayout.CONTENT_LEFT;
            Shapes.panel(painter, left, y, left + contentWidth, y + height);
            sceneWindow(painter, scene, control, needs, left + 1, y + 1, contentWidth - 2, height - 2, Shapes.Icon.EYE, build);
        });
    }

    /**
     * A build's window: the scene, draggable, with the page's clasp in one corner and the blocks-needed clasp in the
     * bottom left; or, while that is pressed, the list of blocks laid over it.
     */
    static void sceneWindow(net.ixdarklord.folios.api.client.page.GuidePainter painter, BuildScene scene, SceneControl control,
                            @org.jetbrains.annotations.Nullable Requirements needs, int x, int y, int width, int height,
                            Shapes.@org.jetbrains.annotations.Nullable Icon extra, SceneControl.@org.jetbrains.annotations.Nullable Extra action) {
        if (needs != null && !needs.isEmpty() && needs.isOpen()) {
            needs.popup(painter, x, y, width, height);
            return;
        }
        scene.update(painter.time());
        control.draw(painter, x, y, width, height, extra, action);
        if (needs != null && !needs.isEmpty()) needs.clasp(painter, x + 2, y + height - Shapes.BUTTON - 2);
    }

    @Override
    public String searchText() {
        return GuideText.resolve(title.orElse("")) + " " + GuideText.resolve(text);
    }
}
