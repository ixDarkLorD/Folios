package net.ixdarklord.folios.internal.quest;

import net.ixdarklord.folios.internal.FoliosNetwork;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.config.FoliosServerConfig;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the server counts as reading, for quests' {@code "read"} objectives. Reading happens on the player's screen, so
 * the server can't see it; it makes faking it cost what reading does. The client says which entry is open as that
 * changes, and the server counts it read once it has been open a while ({@code readSeconds}), in a book the player has
 * with them or one the server opened for them ({@code needsBook}), and no faster than {@code readsPerMinute}. What it
 * turns down is logged, once a minute at most for each player.
 */
public final class QuestReading {
    private record Open(ResourceLocation book, String entry, long since) {}

    private static final Map<UUID, Open> OPEN = new HashMap<>();
    // Books the server opened for each player: these count without being in hand.
    private static final Map<UUID, Set<ResourceLocation>> OPENED = new HashMap<>();
    // When each player's last entries were counted, the last minute's.
    private static final Map<UUID, Deque<Long>> COUNTED = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private QuestReading() {}

    /** A book the server opened for a player, by the book item or {@code FoliosBooks.open}. */
    public static void opened(ServerPlayer player, ResourceLocation book) {
        OPENED.computeIfAbsent(player.getUUID(), uuid -> new HashSet<>()).add(book);
    }

    /** The client saying which entry is open now; none for {@link FoliosPayloads#NO_BOOK} or no entry. */
    static void viewing(ServerPlayer player, ResourceLocation book, String entry) {
        UUID id = player.getUUID();
        if (book.equals(FoliosPayloads.NO_BOOK) || entry.isEmpty()) {
            OPEN.remove(id);
            return;
        }
        Open was = OPEN.get(id);
        if (was != null && was.book.equals(book) && was.entry.equals(entry)) return;
        if (!mayRead(player, book)) {
            OPEN.remove(id);
            warn(player, "reading " + book + " without the book");
            return;
        }
        OPEN.put(id, new Open(book, entry, Util.getMillis()));
    }

    /** Every tick, for each player: an entry open long enough is counted read. */
    static void tick(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null || Util.getMillis() - open.since < FoliosServerConfig.READ_SECONDS.get() * 1000) return;
        OPEN.remove(player.getUUID());
        if (!mayRead(player, open.book)) return;
        if (!withinRate(player)) {
            warn(player, "reading faster than " + FoliosServerConfig.READS_PER_MINUTE.get() + " entries a minute");
            return;
        }
        QuestTracker.read(player, open.book, open.entry);
    }

    // Whether the player may read the book: they have it with them, or the server opened it for them.
    private static boolean mayRead(ServerPlayer player, ResourceLocation book) {
        if (!FoliosServerConfig.NEEDS_BOOK.get()) return true;
        Set<ResourceLocation> opened = OPENED.get(player.getUUID());
        if (opened != null && opened.contains(book)) return true;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && book.equals(FoliosNetwork.bookOf(stack))) return true;
        }
        return false;
    }

    // Counts one more entry in the last minute's, unless that is as many as are allowed.
    private static boolean withinRate(ServerPlayer player) {
        long now = Util.getMillis();
        Deque<Long> times = COUNTED.computeIfAbsent(player.getUUID(), uuid -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > 60_000) times.pollFirst();
        if (times.size() >= FoliosServerConfig.READS_PER_MINUTE.get()) return false;
        times.addLast(now);
        return true;
    }

    private static void warn(ServerPlayer player, @Nullable String what) {
        long now = Util.getMillis();
        Long last = WARNED.get(player.getUUID());
        if (last != null && now - last < 60_000) return;
        WARNED.put(player.getUUID(), now);
        Folios.LOGGER.warn("Not counting {} as reading: {}", player.getName().getString(), what);
    }

    /** Forgets a player, as they leave. */
    static void leave(UUID player) {
        OPEN.remove(player);
        OPENED.remove(player);
        COUNTED.remove(player);
        WARNED.remove(player);
    }
}
