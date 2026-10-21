package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideText;
import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// A build, turning in a window, with what it takes listed under it. `pattern` is its layers from the top down, each a
// list of rows; `mapping` names the block each character stands for (a space is air).
public record MultiblockPage(List<List<String>> pattern, Map<String, String> mapping, Optional<String> title, String text, int height) implements GuidePage {
    public static final MapCodec<MultiblockPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().listOf().fieldOf("pattern").forGetter(MultiblockPage::pattern),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("mapping").forGetter(MultiblockPage::mapping),
            Codec.STRING.optionalFieldOf("title").forGetter(MultiblockPage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(MultiblockPage::text),
            Codec.intRange(40, 130).optionalFieldOf("height", 78).forGetter(MultiblockPage::height)
    ).apply(instance, MultiblockPage::new));

    @Override
    public List<Laid> lay(GuideLayout layout) {
        Map<BlockPos, BlockState> blocks = blocks(pattern, mapping);
        Map<BlockState, Integer> counts = new LinkedHashMap<>();
        for (BlockState state : blocks.values()) counts.merge(state, 1, Integer::sum);
        return this.lay(layout, blocks, counts);
    }

    /** The blocks a pattern and its mapping make, by place: layers from the top down, rows, characters. */
    static Map<BlockPos, BlockState> blocks(List<List<String>> pattern, Map<String, String> mapping) {
        Map<Character, BlockState> states = new HashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getKey().isEmpty()) continue;
            try {
                states.put(entry.getKey().charAt(0), BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, entry.getValue(), false).blockState());
            } catch (Exception e) {
                Folios.LOGGER.warn("A multiblock page names a block it can't read, {}: {}", entry.getValue(), e.getMessage());
            }
        }
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int layer = 0; layer < pattern.size(); layer++) {
            List<String> rows = pattern.get(layer);
            int y = pattern.size() - 1 - layer;
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < row.length(); x++) {
                    BlockState state = states.get(row.charAt(x));
                    if (state == null || state.isAir()) continue;
                    blocks.put(new BlockPos(x, y, z), state);
                }
            }
        }
        return blocks;
    }

    private List<Laid> lay(GuideLayout layout, Map<BlockPos, BlockState> blocks, Map<BlockState, Integer> counts) {
        SceneViewport view = layout.viewport(GuideLayout.CONTENT_WIDTH * GuideLayout.SCENE_DETAIL, height * GuideLayout.SCENE_DETAIL);
        view.blocks(blocks).refreshRate(30);
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
        // What it takes, under the window: up to two rows of six at a time, paged past that.
        Requirements needs = new Requirements(counts);
        int listHeight = needs.isEmpty() ? 0 : Requirements.HEADING + needs.rows(6, 2) * Requirements.SLOT;
        return layout.flow(title.orElse(null), height + (listHeight > 0 ? listHeight + 4 : 0), text, (painter, y) -> {
            int left = GuideLayout.CONTENT_LEFT;
            Shapes.panel(painter, left, y, GuideLayout.CONTENT_RIGHT, y + height);
            control.draw(painter, left + 1, y + 1, GuideLayout.CONTENT_WIDTH - 2, height - 2, Shapes.Icon.EYE, build);
            if (!needs.isEmpty()) needs.grid(painter, GuideLayout.PAGE_WIDTH / 2, y + height + 5, 6, 2);
        });
    }

    @Override
    public String searchText() {
        return title.orElse("") + " " + text;
    }
}
