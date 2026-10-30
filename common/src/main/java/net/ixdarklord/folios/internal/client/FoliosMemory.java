package net.ixdarklord.folios.internal.client;

import net.ixdarklord.coolcatcore.api.network.Network;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.internal.FoliosNetwork;
import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.FoliosRecord;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FoliosMemory {
    private static final Map<Identifier, FoliosMemory> PLAYER = new HashMap<>();
    public static final int MAX_BOOKMARKS = FoliosRecord.MAX_BOOKMARKS;

    private final Identifier book;
    private final @Nullable InteractionHand hand;
    private final @Nullable FoliosMemory player;
    private final Set<String> read = new LinkedHashSet<>();
    private final Set<String> seen = new LinkedHashSet<>();
    private final Set<String> questsDone = new LinkedHashSet<>();
    private final Set<String> celebrated = new LinkedHashSet<>();
    private final Set<String> obtained = new LinkedHashSet<>();
    private final List<FoliosBook.Location> bookmarks = new ArrayList<>();
    private FoliosBook.@Nullable Location last;
    private boolean leftOpen;
    private @Nullable String lastChapter;
    private int lastPage;
    private FoliosRecord loaded = FoliosRecord.EMPTY;
    private boolean dirty;

    private FoliosMemory(Identifier book, @Nullable InteractionHand hand, @Nullable FoliosMemory player) {
        this.book = book;
        this.hand = hand;
        this.player = player;
    }

    public static synchronized FoliosMemory of(Identifier book) {
        return PLAYER.computeIfAbsent(book, id -> {
            FoliosMemory memory = new FoliosMemory(id, null, null);
            memory.load(playerRecords().getOrDefault(id, FoliosRecord.EMPTY));
            return memory;
        });
    }

    public static FoliosMemory forReading(FoliosBook book, @Nullable InteractionHand hand) {
        FoliosMemory own = of(book.id());
        Player player = Minecraft.getInstance().player;
        if (book.progress() != FoliosBook.Progress.BOOK || hand == null || player == null) return own;
        var stack = player.getItemInHand(hand);
        if (!book.id().equals(FoliosNetwork.bookOf(stack))) return own;
        FoliosMemory copy = new FoliosMemory(book.id(), hand, own);
        copy.load(FoliosRegistry.BOOK_PROGRESS.get(stack));
        return copy;
    }

    public static synchronized void synced(Map<Identifier, FoliosRecord> records) {
        for (FoliosMemory memory : PLAYER.values()) {
            FoliosRecord record = records.getOrDefault(memory.book, FoliosRecord.EMPTY);
            if (!memory.dirty && !record.equals(memory.loaded)) memory.load(record);
        }
    }

    public static synchronized void forget() {
        PLAYER.clear();
    }

    private static Map<Identifier, FoliosRecord> playerRecords() {
        Player player = Minecraft.getInstance().player;
        return player != null ? FoliosRegistry.PLAYER_PROGRESS.get(player) : Map.of();
    }

    private synchronized void load(FoliosRecord record) {
        read.clear();
        read.addAll(record.read());
        seen.clear();
        seen.addAll(record.seen());
        questsDone.clear();
        questsDone.addAll(record.questsDone());
        celebrated.clear();
        celebrated.addAll(record.celebrated());
        obtained.clear();
        obtained.addAll(record.obtained());
        bookmarks.clear();
        record.bookmarks().forEach(place -> bookmarks.add(new FoliosBook.Location(place.entry(), place.page())));
        last = record.last().map(place -> new FoliosBook.Location(place.entry(), place.page())).orElse(null);
        leftOpen = record.leftOpen();
        lastChapter = record.lastChapter().orElse(null);
        lastPage = record.lastPage();
        loaded = record;
    }

    private synchronized FoliosRecord record() {
        return new FoliosRecord(List.copyOf(read), List.copyOf(seen), List.copyOf(questsDone), List.copyOf(celebrated), List.copyOf(obtained),
                bookmarks.stream().map(location -> new FoliosRecord.Place(location.entry(), location.page())).toList(),
                Optional.ofNullable(last).map(location -> new FoliosRecord.Place(location.entry(), location.page())),
                leftOpen, Optional.ofNullable(lastChapter), lastPage);
    }

    public void save() {
        if (player != null) player.save();
        FoliosRecord record;
        synchronized (this) {
            if (!dirty) return;
            dirty = false;
            record = this.record();
            loaded = record;
        }
        if (Minecraft.getInstance().getConnection() == null) return;
        Network.sendToServer(new FoliosPayloads.Progress(book, hand != null ? hand.ordinal() : -1, hand != null ? record.forStack() : record));
    }

    public synchronized boolean isRead(String entry) {
        return read.contains(entry);
    }

    public void markRead(String entry) {
        if (player != null) player.markRead(entry);
        synchronized (this) {
            if (read.add(entry)) dirty = true;
        }
    }

    public synchronized boolean isSeen(String spread) {
        return seen.contains(spread);
    }

    public synchronized void markSeen(String spread) {
        if (seen.add(spread)) dirty = true;
    }

    public synchronized boolean markQuestDone(String entry) {
        if (!questsDone.add(entry)) return false;
        dirty = true;
        return true;
    }

    public synchronized Set<String> obtained() {
        return Set.copyOf(obtained);
    }

    public synchronized boolean markObtained(String item) {
        if (!obtained.add(item)) return false;
        dirty = true;
        return true;
    }

    public synchronized boolean markCelebrated(String what) {
        if (!celebrated.add(what)) return false;
        dirty = true;
        return true;
    }

    public synchronized int readCount() {
        return read.size();
    }

    public synchronized List<FoliosBook.Location> bookmarks() {
        return List.copyOf(bookmarks);
    }

    public synchronized boolean isBookmarked(String entry) {
        return bookmarks.stream().anyMatch(location -> location.entry().equals(entry));
    }

    public synchronized boolean toggleBookmark(FoliosBook.Location location) {
        dirty = true;
        if (bookmarks.removeIf(mark -> mark.entry().equals(location.entry()))) return false;
        if (bookmarks.size() >= MAX_BOOKMARKS) bookmarks.removeFirst();
        bookmarks.add(location);
        return true;
    }

    public synchronized FoliosBook.@Nullable Location last() {
        return last;
    }

    public synchronized void setLast(FoliosBook.@Nullable Location location) {
        if (java.util.Objects.equals(last, location)) return;
        last = location;
        dirty = true;
    }

    public synchronized @Nullable String lastChapter() {
        return lastChapter;
    }

    public synchronized int lastPage() {
        return lastPage;
    }

    public synchronized void setLastSpread(@Nullable String chapter, int page) {
        if (java.util.Objects.equals(lastChapter, chapter) && lastPage == page) return;
        lastChapter = chapter;
        lastPage = page;
        dirty = true;
    }

    public synchronized boolean leftOpen() {
        return leftOpen;
    }

    public synchronized void setLeftOpen(boolean open) {
        if (leftOpen == open) return;
        leftOpen = open;
        dirty = true;
    }

    public synchronized void clear() {
        read.clear();
        seen.clear();
        bookmarks.clear();
        last = null;
        dirty = true;
    }
}
