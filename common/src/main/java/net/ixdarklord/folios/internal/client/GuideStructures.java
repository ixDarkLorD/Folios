package net.ixdarklord.folios.internal.client;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.internal.GuidePayloads;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Structures' blocks, asked of the server the first time a page shows one (structures are data packs', which clients
// don't have) and kept for the rest of the visit. Requests made in one frame go together at the next tick.
public final class GuideStructures {
    /** A structure as the server told of it: its blocks by place, or why there are none. */
    public record Known(int status, Map<BlockPos, BlockChoice> blocks) {}

    private static final Map<Identifier, Known> KNOWN = new HashMap<>();
    private static final Set<Identifier> ASKED = new LinkedHashSet<>();
    private static final Set<Identifier> TO_ASK = new LinkedHashSet<>();
    private static int connectedTicks;

    private GuideStructures() {}

    /** A structure's blocks; null until the server answers. */
    public static synchronized @Nullable Known get(Identifier structure) {
        Known known = KNOWN.get(structure);
        if (known == null && ASKED.add(structure)) TO_ASK.add(structure);
        return known;
    }

    public static synchronized void receive(GuidePayloads.Structure payload) {
        Map<BlockPos, BlockChoice> blocks = new LinkedHashMap<>();
        List<BlockChoice> palette = new ArrayList<>();
        for (BlockState state : payload.palette()) palette.add(BlockChoice.of(state));
        List<Integer> numbers = payload.blocks();
        for (int at = 0; at + 3 < numbers.size(); at += 4) {
            int index = numbers.get(at + 3);
            if (index < 0 || index >= palette.size()) continue;
            blocks.put(new BlockPos(numbers.get(at), numbers.get(at + 1), numbers.get(at + 2)), palette.get(index));
        }
        KNOWN.put(payload.id(), new Known(payload.status(), blocks));
    }

    public static synchronized void tick() {
        if (Minecraft.getInstance().getConnection() == null) return;
        connectedTicks++;
        if (TO_ASK.isEmpty()) return;
        // As for recipes: a server without the mod says nothing of advancements as the player joins, and has no
        // structures to tell of either.
        if (!GuideProgress.serverHasGuides()) {
            if (connectedTicks < 100) return;
            TO_ASK.forEach(id -> KNOWN.put(id, new Known(GuidePayloads.Structure.MISSING, Map.of())));
            TO_ASK.clear();
            return;
        }
        List<Identifier> ids = new ArrayList<>(TO_ASK);
        TO_ASK.clear();
        for (int start = 0; start < ids.size(); start += 64) {
            Network.sendToServer(new GuidePayloads.StructureRequest(ids.subList(start, Math.min(ids.size(), start + 64))));
        }
    }

    public static synchronized void clear() {
        connectedTicks = 0;
        KNOWN.clear();
        ASKED.clear();
        TO_ASK.clear();
    }
}
