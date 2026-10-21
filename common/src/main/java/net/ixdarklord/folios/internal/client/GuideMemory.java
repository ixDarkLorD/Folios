package net.ixdarklord.folios.internal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.coolcatcore.api.platform.Platform;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// What the player has done with each book, kept between games: the entries read, bookmarks, and where they left
// off. One file per book in config/folios/<namespace>/<path>.json, written when a book is closed.
public final class GuideMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ResourceLocation, GuideMemory> BOOKS = new HashMap<>();
    public static final int MAX_BOOKMARKS = 8;

    private final ResourceLocation book;
    private final Set<String> read = new LinkedHashSet<>();
    private final Set<String> questsDone = new LinkedHashSet<>();
    // What has been celebrated: "category:<id>", "mastery".
    private final Set<String> celebrated = new LinkedHashSet<>();
    private final List<GuideBook.Location> bookmarks = new ArrayList<>();
    private GuideBook.@Nullable Location last;
    // Whether a handheld book was put away lying open: it then opens again by itself, where it was left.
    private boolean leftOpen;
    // The spread last open when it wasn't an entry's (the front, a category, the index, the bookmarks): its chapter
    // key and page, or null.
    private @Nullable String lastChapter;
    private int lastPage;
    private boolean dirty;

    private GuideMemory(ResourceLocation book) {
        this.book = book;
    }

    public static synchronized GuideMemory of(ResourceLocation book) {
        return BOOKS.computeIfAbsent(book, id -> {
            GuideMemory memory = new GuideMemory(id);
            memory.load();
            return memory;
        });
    }

    private Path file() {
        return Platform.getConfigFolder().resolve("folios").resolve(book.getNamespace()).resolve(book.getPath() + ".json");
    }

    private void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("read")) json.getAsJsonArray("read").forEach(element -> read.add(element.getAsString()));
            if (json.has("bookmarks")) json.getAsJsonArray("bookmarks").forEach(element -> {
                GuideBook.Location location = location(element);
                if (location != null) bookmarks.add(location);
            });
            if (json.has("last")) last = location(json.get("last"));
            leftOpen = json.has("left_open") && json.get("left_open").getAsBoolean();
            if (json.has("last_spread") && json.get("last_spread").isJsonObject()) {
                JsonObject spread = json.getAsJsonObject("last_spread");
                lastChapter = spread.has("chapter") ? spread.get("chapter").getAsString() : null;
                lastPage = spread.has("page") ? spread.get("page").getAsInt() : 0;
            }
            if (json.has("quests_done")) json.getAsJsonArray("quests_done").forEach(element -> questsDone.add(element.getAsString()));
            if (json.has("celebrated")) json.getAsJsonArray("celebrated").forEach(element -> celebrated.add(element.getAsString()));
        } catch (IOException | RuntimeException e) {
            Folios.LOGGER.warn("Couldn't read what was read of the guide {} from {}: {}", book, file, e.getMessage());
        }
    }

    private static GuideBook.@Nullable Location location(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        if (!object.has("entry")) return null;
        return new GuideBook.Location(object.get("entry").getAsString(), object.has("page") ? object.get("page").getAsInt() : 0);
    }

    private static JsonObject json(GuideBook.Location location) {
        JsonObject object = new JsonObject();
        object.addProperty("entry", location.entry());
        object.addProperty("page", location.page());
        return object;
    }

    public synchronized void save() {
        if (!dirty) return;
        dirty = false;
        JsonObject json = new JsonObject();
        JsonArray readArray = new JsonArray();
        read.forEach(readArray::add);
        json.add("read", readArray);
        JsonArray marks = new JsonArray();
        bookmarks.forEach(location -> marks.add(json(location)));
        json.add("bookmarks", marks);
        if (last != null) json.add("last", json(last));
        if (leftOpen) json.addProperty("left_open", true);
        if (lastChapter != null) {
            JsonObject spread = new JsonObject();
            spread.addProperty("chapter", lastChapter);
            spread.addProperty("page", lastPage);
            json.add("last_spread", spread);
        }
        JsonArray quests = new JsonArray();
        questsDone.forEach(quests::add);
        json.add("quests_done", quests);
        JsonArray done = new JsonArray();
        celebrated.forEach(done::add);
        json.add("celebrated", done);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            Folios.LOGGER.warn("Couldn't save what was read of the guide {} to {}", book, file, e);
        }
    }

    public synchronized boolean isRead(String entry) {
        return read.contains(entry);
    }

    public synchronized void markRead(String entry) {
        if (read.add(entry)) dirty = true;
    }

    /** Notes a quest as done; returns whether it wasn't already. */
    public synchronized boolean markQuestDone(String entry) {
        if (!questsDone.add(entry)) return false;
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

    public synchronized List<GuideBook.Location> bookmarks() {
        return List.copyOf(bookmarks);
    }

    public synchronized boolean isBookmarked(String entry) {
        return bookmarks.stream().anyMatch(location -> location.entry().equals(entry));
    }

    /** Adds a bookmark at a place, or removes the entry's if it has one; returns whether it is bookmarked now. */
    public synchronized boolean toggleBookmark(GuideBook.Location location) {
        dirty = true;
        if (bookmarks.removeIf(mark -> mark.entry().equals(location.entry()))) return false;
        if (bookmarks.size() >= MAX_BOOKMARKS) bookmarks.removeFirst();
        bookmarks.add(location);
        return true;
    }

    public synchronized GuideBook.@Nullable Location last() {
        return last;
    }

    public synchronized void setLast(GuideBook.@Nullable Location location) {
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

    /** Forgets everything read, for the "start over" button. */
    public synchronized void clear() {
        read.clear();
        bookmarks.clear();
        last = null;
        dirty = true;
    }
}
