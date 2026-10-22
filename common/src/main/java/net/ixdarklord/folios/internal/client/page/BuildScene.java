package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

// A build's blocks in a scene's window: what stands at each place is put in the scene again each second while any
// place takes a tag, so the tag's blocks are shown in turn. A page showing several builds puts another in its place.
final class BuildScene {
    private final SceneViewport view;
    private Map<BlockPos, BlockChoice> blocks = Map.of();
    private boolean cycles;
    private int shownAt = Integer.MIN_VALUE;

    BuildScene(SceneViewport view) {
        this.view = view;
    }

    /** Shows another build in the window, in place of the one there. */
    void show(Map<BlockPos, BlockChoice> blocks) {
        this.blocks = blocks;
        this.cycles = blocks.values().stream().anyMatch(BlockChoice::cycles);
        this.shownAt = Integer.MIN_VALUE;
    }

    /** Puts the blocks for this time (seconds) in the scene, if they have changed; call before drawing it. */
    void update(float time) {
        int second = cycles ? (int) time : 0;
        if (second == shownAt) return;
        shownAt = second;
        view.clear();
        for (Map.Entry<BlockPos, BlockChoice> block : blocks.entrySet()) {
            BlockState state = block.getValue().shown(time);
            if (state != null) view.block(block.getKey(), state);
        }
    }
}
