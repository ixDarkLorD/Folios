package net.ixdarklord.folios.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.folios.api.FoliosSources;
import net.ixdarklord.folios.internal.client.FoliosProgress;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rows of {@link FoliosSources}: on the server, those data packs add ({@code data/<namespace>/folios/sources/*.json})
 * and the sending of every server source to players; on the client, what the server sent.
 */
public final class FoliosSourceData extends SimpleJsonResourceReloadListener {
    public static final FoliosSourceData INSTANCE = new FoliosSourceData();
    private static Map<ResourceLocation, List<FoliosSources.Row>> packs = Map.of();
    // The ids data packs took out of each source, whoever gave them.
    private static Map<ResourceLocation, List<String>> removals = Map.of();
    // Data packs reloaded: every source is sent again at the end of the next tick.
    private static volatile boolean reloaded;
    // What the server sent, by source (the client's side).
    private static final Map<ResourceLocation, List<FoliosSources.Row>> RECEIVED = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<String>> RECEIVED_REMOVED = new ConcurrentHashMap<>();

    private FoliosSourceData() {
        super(new Gson(), "folios/sources");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<String, FoliosSources.Row>> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, Set<String>> removed = new LinkedHashMap<>();
        new TreeMap<>(files).forEach((file, json) -> {
            try {
                JsonObject object = json.getAsJsonObject();
                if (!modsLoaded(object)) return;
                ResourceLocation source = ResourceLocation.parse(object.get("source").getAsString());
                Map<String, FoliosSources.Row> rows = loaded.computeIfAbsent(source, id -> new LinkedHashMap<>());
                Set<String> gone = removed.computeIfAbsent(source, id -> new LinkedHashSet<>());
                for (JsonElement element : object.getAsJsonArray("rows")) {
                    JsonObject row = element.getAsJsonObject();
                    if (!modsLoaded(row)) continue;
                    String id = row.get("id").getAsString();
                    // Taken out, whoever gave it; a later file's row of that id brings it back.
                    if (row.has("remove") && row.get("remove").getAsBoolean()) {
                        rows.remove(id);
                        gone.add(id);
                        continue;
                    }
                    gone.remove(id);
                    FoliosSources.Builder built = FoliosSources.row(id);
                    for (Map.Entry<String, JsonElement> field : row.entrySet()) {
                        if (field.getKey().equals("id") || field.getKey().equals("remove") || field.getKey().equals("requires_mods")) continue;
                        built.field(field.getKey(), field.getValue().isJsonPrimitive() ? field.getValue().getAsString() : field.getValue().toString());
                    }
                    rows.merge(id, built.build(), FoliosSources.Row::with);
                }
            } catch (Exception e) {
                Folios.LOGGER.error("Skipping the Folios source file {}: {}", file, e.getMessage());
            }
        });
        Map<ResourceLocation, List<FoliosSources.Row>> done = new LinkedHashMap<>();
        loaded.forEach((source, rows) -> done.put(source, List.copyOf(rows.values())));
        packs = Map.copyOf(done);
        Map<ResourceLocation, List<String>> taken = new LinkedHashMap<>();
        removed.forEach((source, ids) -> taken.put(source, List.copyOf(ids)));
        removals = Map.copyOf(taken);
        reloaded = true;
    }

    // Whether the mods a file or row needs ("requires_mods": an id or a list) are all loaded.
    private static boolean modsLoaded(JsonObject json) {
        if (!json.has("requires_mods")) return true;
        JsonElement needs = json.get("requires_mods");
        if (needs.isJsonArray()) {
            for (JsonElement mod : needs.getAsJsonArray()) if (!Platform.isModLoaded(mod.getAsString())) return false;
            return true;
        }
        return Platform.isModLoaded(needs.getAsString());
    }

    /** The rows data packs give a source. */
    public static List<FoliosSources.Row> packRows(ResourceLocation source) {
        return packs.getOrDefault(source, List.of());
    }

    /** The ids data packs took out of a source. */
    public static List<String> packRemoved(ResourceLocation source) {
        return removals.getOrDefault(source, List.of());
    }

    public static List<ResourceLocation> packSources() {
        return List.copyOf(packs.keySet());
    }

    // ---- Sending ----

    /** Sends every server source to a player, as they join. */
    public static void sendAll(ServerPlayer player) {
        if (!Network.canPlayerReceive(player, FoliosPayloads.Source.TYPE)) return;
        MinecraftServer server = player.level().getServer();
        for (ResourceLocation source : FoliosSources.serverSources()) Network.sendToPlayer(player, payload(server, source));
    }

    /** Sends a server source to every player. */
    public static void send(MinecraftServer server, ResourceLocation source) {
        FoliosPayloads.Source payload = payload(server, source);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Network.canPlayerReceive(player, FoliosPayloads.Source.TYPE)) Network.sendToPlayer(player, payload);
        }
    }

    private static FoliosPayloads.Source payload(MinecraftServer server, ResourceLocation source) {
        return new FoliosPayloads.Source(source, FoliosSources.serverRows(server, source), packRemoved(source));
    }

    /** At the end of a tick: after data packs reloaded, every source is sent again. */
    public static void tick(MinecraftServer server) {
        if (!reloaded) return;
        reloaded = false;
        for (ResourceLocation source : FoliosSources.serverSources()) send(server, source);
    }

    // ---- The client ----

    /** A source the server sent: kept, and books written again if its rows changed. */
    public static void receive(FoliosPayloads.Source payload) {
        List<FoliosSources.Row> before = RECEIVED.put(payload.source(), List.copyOf(payload.rows()));
        List<String> removedBefore = RECEIVED_REMOVED.put(payload.source(), List.copyOf(payload.removed()));
        if (!payload.rows().equals(before) || !payload.removed().equals(removedBefore)) FoliosProgress.changed();
    }

    public static List<FoliosSources.Row> received(ResourceLocation source) {
        return RECEIVED.getOrDefault(source, List.of());
    }

    /** The ids the server's data packs took out of a source, to take out of the client's rows too. */
    public static List<String> removed(ResourceLocation source) {
        return RECEIVED_REMOVED.getOrDefault(source, List.of());
    }

    public static boolean hasReceived(ResourceLocation source) {
        return RECEIVED.containsKey(source);
    }

    /** Forgets what a server sent, as the client leaves it. */
    public static void forget() {
        RECEIVED.clear();
        RECEIVED_REMOVED.clear();
    }
}
