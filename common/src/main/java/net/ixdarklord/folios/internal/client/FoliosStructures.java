package net.ixdarklord.folios.internal.client;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FoliosStructures {
    public record Known(int status, Map<BlockPos, BlockChoice> blocks) {}

    private static final Map<ResourceLocation, Known> KNOWN = new HashMap<>();
    private static final Set<ResourceLocation> ASKED = new LinkedHashSet<>();
    private static final Set<ResourceLocation> TO_ASK = new LinkedHashSet<>();
    private static int connectedTicks;

    private FoliosStructures() {}

    public static synchronized @Nullable Known get(ResourceLocation structure) {
        Known known = KNOWN.get(structure);
        if (known == null && ASKED.add(structure)) TO_ASK.add(structure);
        return known;
    }

    public static synchronized void receive(FoliosPayloads.Structure payload) {
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
        if (!FoliosProgress.serverHasGuides()) {
            if (connectedTicks < 100) return;
            TO_ASK.forEach(id -> KNOWN.put(id, new Known(FoliosPayloads.Structure.MISSING, Map.of())));
            TO_ASK.clear();
            return;
        }
        List<ResourceLocation> ids = new ArrayList<>(TO_ASK);
        TO_ASK.clear();
        for (int start = 0; start < ids.size(); start += 64) {
            Network.sendToServer(new FoliosPayloads.StructureRequest(ids.subList(start, Math.min(ids.size(), start + 64))));
        }
    }

    public static synchronized void clear() {
        connectedTicks = 0;
        KNOWN.clear();
        ASKED.clear();
        TO_ASK.clear();
    }
}
