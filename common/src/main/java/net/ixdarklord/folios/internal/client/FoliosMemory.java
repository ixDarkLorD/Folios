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

// What the player has done with a book (a FoliosRecord): the entries read, the spreads shown, quests done and what has
// been celebrated, bookmarks, and where they left off. The server keeps it, with the player, or on the copy of a book
// that keeps its own; this is the client's working copy of it, sent back when it changes (save, as a book is closed).
// The player's copies follow what the server syncs while they hold no unsent changes; a copy's is read from its stack
// each time the book is opened.
public final class FoliosMemory {
    private static final Map<Identifier, FoliosMemory> PLAYER = new HashMap<>();
    public static final int MAX_BOOKMARKS = FoliosRecord.MAX_BOOKMARKS;

    private final Identifier book;
    // The hand the copy it is kept on is in, or null for the player's own.
    private final @Nullable InteractionHand hand;
    // For a copy's: the player's, which hears of what is read too (for quests and celebrations).
    private final @Nullable FoliosMemory player;
    private final Set<String> read = new LinkedHashSet<>();
    private final Set<String> seen = new LinkedHashSet<>();
    private final Set<String> questsDone = new LinkedHashSet<>();
    // What has been celebrated: "category:<id>", "mastery".
    private final Set<String> celebrated = new LinkedHashSet<>();
    // The items had that the book's "obtained" locks and objectives ask for (ObtainedItems), by id.
    private final Set<String> obtained = new LinkedHashSet<>();
    private final List<FoliosBook.Location> bookmarks = new ArrayList<>();
    private FoliosBook.@Nullable Location last;
    // Whether a handheld book was put away lying open: it then opens again by itself, where it was left.
    private boolean leftOpen;
    // The spread last open when it wasn't an entry's (the front, a category, the index, the bookmarks): its chapter
    // key and page, or null.
    private @Nullable String lastChapter;
    private int lastPage;
    // What was last read from the server or sent to it; and whether there are changes since.
    private FoliosRecord loaded = FoliosRecord.EMPTY;
    private boolean dirty;

    private FoliosMemory(Identifier book, @Nullable InteractionHand hand, @Nullable FoliosMemory player) {
        this.book = book;
        this.hand = hand;
        this.player = player;
    }

    /** The player's own memory of a book. */
    public static synchronized FoliosMemory of(Identifier book) {
        return PLAYER.computeIfAbsent(book, id -> {
            FoliosMemory memory = new FoliosMemory(id, null, null);
            memory.load(playerRecords().getOrDefault(id, FoliosRecord.EMPTY));
            return memory;
        });
    }

    /**
     * The memory a reader of a book uses: for a book that keeps its own ({@code "progress": "book"}) opened from a copy
     * in a hand, that copy's; otherwise the player's.
     */
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

    /** The server has synced the player's records: each memory without unsent changes takes its book's. */
    public static synchronized void synced(Map<Identifier, FoliosRecord> records) {
        for (FoliosMemory memory : PLAYER.values()) {
            FoliosRecord record = records.getOrDefault(memory.book, FoliosRecord.EMPTY);
            if (!memory.dirty && !record.equals(memory.loaded)) memory.load(record);
        }
    }

    /** Forgets every memory, as the player leaves a world or server: the next one has its own. */
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

    /** Sends what changed to the server to keep (and, for a copy's, the player's too). */
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

    /** Whether a spread ({@code "<chapter>|<page>"}) has been shown in full. */
    public synchronized boolean isSeen(String spread) {
        return seen.contains(spread);
    }

    public synchronized void markSeen(String spread) {
        if (seen.add(spread)) dirty = true;
    }

    /** Notes a quest as done; returns whether it wasn't already. */
    public synchronized boolean markQuestDone(String entry) {
        if (!questsDone.add(entry)) return false;
        dirty = true;
        return true;
    }

    /** The items had that the book asks about, by id. */
    public synchronized Set<String> obtained() {
        return Set.copyOf(obtained);
    }

    /** Notes an item as had; returns whether it wasn't already. */
    public synchronized boolean markObtained(String item) {
        if (!obtained.add(item)) return false;
        dirty = true;
        return true;
    }

    /** Marks something celebrated; false if it already was. */
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

    /** Adds a bookmark at a place, or removes the entry's if it has one; returns whether it is bookmarked now. */
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

    /** The chapter key of the spread last open when it wasn't an entry's, or null; see {@link #lastPage()}. */
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

    /** Whether the book was put away lying open (a handheld book), to open by itself next time. */
    public synchronized boolean leftOpen() {
        return leftOpen;
    }

    public synchronized void setLeftOpen(boolean open) {
        if (leftOpen == open) return;
        leftOpen = open;
        dirty = true;
    }

    /** Forgets everything read and shown, for the "start over" button. */
    public synchronized void clear() {
        read.clear();
        seen.clear();
        bookmarks.clear();
        last = null;
        dirty = true;
    }
}
