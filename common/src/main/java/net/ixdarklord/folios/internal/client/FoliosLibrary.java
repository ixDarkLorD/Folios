package net.ixdarklord.folios.internal.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.ixdarklord.folios.api.FoliosFormat;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosReveal;
import net.ixdarklord.folios.api.client.FoliosReveals;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.FoliosLock;
import net.ixdarklord.folios.api.client.FoliosStyles;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPageElements;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.format.BookUpgrader;
import net.ixdarklord.folios.internal.client.page.ErrorPage;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Reads every book from resources (assets/<namespace>/folios/<book>/...) when they reload, and keeps what was laid
// out from them until the next reload or a change of language. A book's builds (<book>/builds/<name>.json) are kept as
// they are written, for its build pages to read. A book in an older format (its "format_version", 2.0 when it has
// none) is upgraded to the current one as it is read (BookUpgrader), so everything after reads one format only.
public final class FoliosLibrary {
    private static final FileToIdConverter FILES = FileToIdConverter.json("folios");
    private static final int DEFAULT_ACCENT = 0x8B6CFF;
    private static final int DEFAULT_COVER = 0x3A2E58;
    private static final float DEFAULT_TILT = 6.0F;
    private static final float DEFAULT_DISTANCE = 0.64F;

    private static Map<Identifier, FoliosBook> books = Map.of();
    private static Map<Identifier, JsonObject> builds = Map.of();
    private static Map<Identifier, String> authors = Map.of();
    // How each book sets its elements unless its pages say otherwise ("overflow", "alignment"... in book.json).
    private static Map<Identifier, net.ixdarklord.folios.internal.client.page.ElementLayout> layouts = Map.of();
    private static Map<Identifier, CelebrationSettings> celebrations = Map.of();
    private static final Map<Identifier, FoliosSession> SESSIONS = new HashMap<>();
    // A book laid out to larger pages (a screen book at a small GUI scale): one size a book at a time, the last asked for.
    private static final Map<Identifier, FoliosSession> LARGER = new HashMap<>();

    private FoliosLibrary() {}

    public static @Nullable FoliosBook book(Identifier id) {
        return books.get(id);
    }

    public static Map<Identifier, FoliosBook> books() {
        return books;
    }

    /**
     * Who a book says it is by, as markup: its {@code "author"}, or else its {@code folios.<namespace>.<path>.author}
     * translation; null when it says neither.
     */
    public static @Nullable String author(Identifier book) {
        String written = authors.get(book);
        if (written != null) return written;
        String key = textKey(book, "author");
        return FoliosText.hasTranslation(key) ? key : null;
    }

    /** The translation key of a book's own text: {@code folios.<namespace>.<path>.<what>}, a / in the path as a dot. */
    public static String textKey(Identifier book, String what) {
        return "folios." + book.getNamespace() + "." + book.getPath().replace('/', '.') + "." + what;
    }

    /** How a book celebrates the player finishing part of it ({@code "celebrations"} in book.json); off unless it says. */
    public static CelebrationSettings celebrations(Identifier book) {
        return celebrations.getOrDefault(book, CelebrationSettings.OFF);
    }

    /** A build file as written, by its path under folios/ without ".json" ({@code mymod:my_guide/builds/tower}); null for none. */
    public static @Nullable JsonObject build(Identifier id) {
        return builds.get(id);
    }

    /** A book's laid-out pages, kept between openings; null when there is no such book. */
    public static @Nullable FoliosSession session(Identifier id) {
        FoliosBook book = books.get(id);
        if (book == null) return null;
        FoliosSession session = SESSIONS.get(id);
        if (session == null || !session.isCurrent()) {
            if (session != null) session.close();
            session = new FoliosSession(book);
            SESSIONS.put(id, session);
        }
        return session;
    }

    /** A book's laid-out pages for pages {@code width} by {@code height}; the default session for the default page size. */
    public static @Nullable FoliosSession session(Identifier id, int width, int height) {
        if (width <= FoliosLayout.PAGE_WIDTH && height <= FoliosLayout.PAGE_HEIGHT) return session(id);
        FoliosBook book = books.get(id);
        if (book == null) return null;
        FoliosSession session = LARGER.get(id);
        if (session == null || !session.isCurrent() || session.layout().pageWidth() != width || session.layout().pageHeight() != height) {
            if (session != null) session.close();
            session = new FoliosSession(book, width, height);
            LARGER.put(id, session);
        }
        return session;
    }

    // ---- Reading ----

    public static void reload(ResourceManager manager) {
        // The widgets' sprites are read again (a resource pack may have changed them).
        net.ixdarklord.folios.internal.client.page.Sprites.clear();
        net.ixdarklord.folios.internal.client.handheld.CoverModel.clear();
        CoverMarkers.clear();
        ObtainedItems.clear();
        Map<Identifier, JsonObject> bookFiles = new LinkedHashMap<>();
        // Each book's other files, by their path in its folder: "categories/basics.json", "entries/basics/first_steps.json".
        Map<Identifier, Map<String, JsonObject>> parts = new HashMap<>();
        for (Map.Entry<Identifier, Resource> file : FILES.listMatchingResources(manager).entrySet()) {
            Identifier id = FILES.fileToId(file.getKey());
            String path = id.getPath();
            JsonObject json;
            try (Reader reader = file.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) throw new IllegalStateException("not a JSON object");
                json = element.getAsJsonObject();
            } catch (Exception e) {
                Folios.LOGGER.error("Couldn't read the guide file {}: {}", file.getKey(), e.getMessage());
                continue;
            }
            if (path.endsWith("/book")) {
                bookFiles.put(id.withPath(path.substring(0, path.length() - 5)), json);
                continue;
            }
            int categories = path.indexOf("/categories/");
            int entries = path.indexOf("/entries/");
            int buildsAt = path.indexOf("/builds/");
            int at;
            if (buildsAt > 0 && (categories < 0 || buildsAt < categories) && (entries < 0 || buildsAt < entries)) at = buildsAt;
            else if (categories > 0 && (entries < 0 || categories < entries)) at = categories;
            else if (entries > 0) at = entries;
            else continue;
            parts.computeIfAbsent(id.withPath(path.substring(0, at)), book -> new LinkedHashMap<>()).put(path.substring(at + 1) + ".json", json);
        }
        Map<Identifier, FoliosBook> loaded = new LinkedHashMap<>();
        Map<Identifier, JsonObject> buildFiles = new HashMap<>();
        Map<Identifier, String> writtenBy = new HashMap<>();
        Map<Identifier, net.ixdarklord.folios.internal.client.page.ElementLayout> setting = new HashMap<>();
        Map<Identifier, CelebrationSettings> celebrating = new HashMap<>();
        for (Map.Entry<Identifier, JsonObject> file : bookFiles.entrySet()) {
            Identifier id = file.getKey();
            try {
                Map<String, JsonObject> files = new LinkedHashMap<>();
                files.put("book.json", file.getValue());
                files.putAll(parts.getOrDefault(id, Map.of()));
                files = current(id, files);
                JsonObject book = files.get("book.json");
                if (book.has("author")) writtenBy.put(id, FoliosText.read(book.get("author")));
                setting.put(id, net.ixdarklord.folios.internal.client.page.ElementLayout.read(book, "The guide " + id));
                Map<String, JsonObject> categories = new LinkedHashMap<>();
                Map<String, JsonObject> entries = new LinkedHashMap<>();
                files.forEach((path, json) -> {
                    String name = path.substring(0, path.length() - 5);
                    if (name.startsWith("categories/")) categories.put(name.substring(11), json);
                    else if (name.startsWith("entries/")) entries.put(name.substring(8), json);
                    else if (name.startsWith("builds/")) buildFiles.put(id.withPath(id.getPath() + "/" + name), json);
                });
                loaded.put(id, readBook(id, book, categories, entries));
                celebrating.put(id, CelebrationSettings.read(book.get("celebrations")));
            } catch (Exception e) {
                Folios.LOGGER.error("Couldn't load the guide {}: {}", id, e.getMessage());
            }
        }
        for (Identifier orphan : parts.keySet()) {
            if (!bookFiles.containsKey(orphan)) Folios.LOGGER.warn("Guide files for {} have no book.json beside them", orphan);
        }
        books = Map.copyOf(loaded);
        builds = Map.copyOf(buildFiles);
        authors = Map.copyOf(writtenBy);
        layouts = Map.copyOf(setting);
        celebrations = Map.copyOf(celebrating);
        SESSIONS.values().forEach(FoliosSession::close);
        SESSIONS.clear();
        LARGER.values().forEach(FoliosSession::close);
        LARGER.clear();
        if (!loaded.isEmpty()) Folios.LOGGER.info("Loaded {} guide book(s)", loaded.size());
    }

    // A book's files in the current format: upgraded from the one it says it is written in (2.0 when it doesn't say).
    private static Map<String, JsonObject> current(Identifier id, Map<String, JsonObject> files) {
        JsonObject book = files.get("book.json");
        String format = FoliosFormat.LEGACY;
        JsonElement named = book.get("format_version");
        if (named == null) {
            Folios.LOGGER.warn("The guide {} is in the Folios 2.x format; update it with the book upgrader on the Folios docs site", id);
        } else if (!named.isJsonPrimitive() || FoliosFormat.parse(named.getAsString()) == null) {
            Folios.LOGGER.error("The guide {} has the \"format_version\" {}, which isn't a version such as \"{}\"; it is read in the format of Folios 2.x",
                    id, named, FoliosFormat.CURRENT);
        } else {
            String version = named.getAsString();
            if (FoliosFormat.isNewer(version)) {
                Folios.LOGGER.error("The guide {} is written for Folios {} or newer; this is Folios {}, so parts of it may not be read as meant",
                        id, version, FoliosFormat.RELEASE);
            }
            format = FoliosFormat.of(version);
        }
        if (FoliosFormat.compare(format, FoliosFormat.CURRENT) < 0) {
            return BookUpgrader.upgrade(id.toString(), files, format, FoliosFormat.CURRENT).files();
        }
        // Keys of an older format left in it are named, and not read (the current format has no aliases).
        Map<String, List<String>> old = BookUpgrader.check(id.toString(), files, FoliosFormat.CURRENT);
        if (!old.isEmpty()) {
            StringBuilder list = new StringBuilder();
            old.forEach((path, lines) -> lines.forEach(line -> list.append(System.lineSeparator()).append("  ").append(path).append(": ").append(line)));
            Folios.LOGGER.error("The guide {} is written in format {} but still has the shape of an older one in places; these are left out "
                    + "until they are written as the format has them (the book upgrader on the docs site, {}, lists them too; how each is "
                    + "written now: {}){}", id, FoliosFormat.CURRENT, UPGRADER, MIGRATION, list);
        }
        return files;
    }

    // A book's own text (its name, subtitle, summary or landing): as book.json writes it (markup, a translation key or
    // {"translate": ...}), or, left out, the translation folios.<namespace>.<path>.<key>, shown as the fallback when the
    // language has no such key either.
    private static String bookText(Identifier id, JsonObject json, String key, String fallback) {
        if (json.has(key) && !json.get(key).isJsonNull()) return FoliosText.read(json.get(key));
        return FoliosText.translation(textKey(id, key), List.of(), fallback);
    }

    /** How a book sets its elements where its pages don't say ("overflow", "alignment"... in its book.json). */
    public static net.ixdarklord.folios.internal.client.page.ElementLayout layoutOf(Identifier book) {
        return layouts.getOrDefault(book, net.ixdarklord.folios.internal.client.page.ElementLayout.NONE);
    }

    /** Where the docs say how to move a book to the current format. */
    public static final String MIGRATION = "https://ixdarklord.github.io/Folios/docs/migrating-to-3.0/";
    /** The docs site's tool that updates a book's files to the current format. */
    public static final String UPGRADER = "https://ixdarklord.github.io/Folios/upgrader/";

    private static FoliosBook readBook(Identifier id, JsonObject json, Map<String, JsonObject> categoryFiles, Map<String, JsonObject> entryFiles) {
        String name = bookText(id, json, "name", id.toString());
        Identifier style = json.has("style") ? FoliosStyles.id(json.get("style").getAsString()) : FoliosStyles.SCREEN;
        List<FoliosCategory> categories = new ArrayList<>();
        for (Map.Entry<String, JsonObject> file : categoryFiles.entrySet()) {
            try {
                JsonObject category = file.getValue();
                String key = textKey(id, "category." + file.getKey().replace('/', '.'));
                categories.add(new FoliosCategory(file.getKey(), text(category, "name", key + ".name", file.getKey()),
                        text(category, "description", key + ".description", ""), FoliosIcon.read(category.get("icon")),
                        integer(category, "sort", 0), FoliosLock.read(category, id), color(category, "color", -1)));
            } catch (Exception e) {
                Folios.LOGGER.error("Couldn't load the category {} of the guide {}: {}", file.getKey(), id, e.getMessage());
            }
        }
        List<FoliosEntry> entries = new ArrayList<>();
        for (Map.Entry<String, JsonObject> file : entryFiles.entrySet()) {
            try {
                FoliosEntry entry = readEntry(id, file.getKey(), file.getValue());
                if (categories.stream().noneMatch(category -> category.id().equals(entry.category()))) {
                    Folios.LOGGER.warn("The entry {} of the guide {} is in the category {}, which the book doesn't have", entry.id(), id, entry.category());
                    continue;
                }
                entries.add(entry);
            } catch (Exception e) {
                Folios.LOGGER.error("Couldn't load the entry {} of the guide {}: {}", file.getKey(), id, e.getMessage());
            }
        }
        // The cover's colour, picture, model and lettering, and how a handheld book is held, each in a group of its own.
        JsonObject cover = json.has("cover") ? json.getAsJsonObject("cover") : new JsonObject();
        JsonObject handheld = json.has("handheld") ? json.getAsJsonObject("handheld") : new JsonObject();
        return new FoliosBook(id, name, bookText(id, json, "subtitle", ""), bookText(id, json, "landing", ""), style,
                color(json, "accent", DEFAULT_ACCENT), color(cover, "color", DEFAULT_COVER),
                cover.has("texture") ? Identifier.parse(cover.get("texture").getAsString()) : null,
                json.has("icon") ? FoliosIcon.read(json.get("icon")) : ownItem(id),
                models(json, cover),
                !json.has("show_progress") || json.get("show_progress").getAsBoolean(),
                handheld.has("tilt") ? Mth.clamp(handheld.get("tilt").getAsFloat(), -45.0F, 80.0F) : DEFAULT_TILT,
                handheld.has("distance") ? Mth.clamp(handheld.get("distance").getAsFloat(), 0.3F, 1.5F) : DEFAULT_DISTANCE,
                handheld.has("page_turn") ? FoliosBook.PageTurn.valueOf(handheld.get("page_turn").getAsString().toUpperCase(java.util.Locale.ROOT)) : FoliosBook.PageTurn.BEND,
                json.has("reveal") ? reveal(json.get("reveal")) : FoliosReveals.DEFAULT,
                textures(json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject()),
                layout(json.has("layout") ? json.getAsJsonObject("layout") : new JsonObject()),
                // A handheld book is put away by a click off it unless it says otherwise; a screen book stays.
                json.has("close_on_click_outside") ? json.get("close_on_click_outside").getAsBoolean() : style.equals(FoliosStyles.HANDHELD),
                handling(handheld),
                colors(json.has("colors") ? json.getAsJsonObject("colors") : new JsonObject(), json.get("panel")),
                coverText(cover.has("text") ? cover.getAsJsonObject("text") : new JsonObject(), bookText(id, json, "summary", "")),
                // Whose reading the book keeps: the player's, or each copy its own.
                json.has("progress_scope") && json.get("progress_scope").getAsString().equals("copy") ? FoliosBook.Progress.BOOK : FoliosBook.Progress.PLAYER,
                categories, entries);
    }

    private static final java.util.Set<String> PAGE_COLORS = java.util.Set.of("paper", "text", "muted", "heading", "accent", "link", "panel", "line");

    private static final java.util.Set<String> PANEL_COLORS = java.util.Set.of("background", "frame", "title", "detail");

    // The page colours, and the panel scheme ("panel" in book.json).
    private static FoliosBook.Colors colors(JsonObject json, @Nullable JsonElement panel) {
        Map<String, Integer> panelColors = panel != null && panel.isJsonObject() ? named(panel.getAsJsonObject(), PANEL_COLORS) : Map.of();
        return new FoliosBook.Colors(named(json, PAGE_COLORS), json.has("dark") ? named(json.getAsJsonObject("dark"), PAGE_COLORS) : Map.of(), panelColors);
    }

    // The colours of a block of a book's scheme, opaque unless given with their alpha.
    private static Map<String, Integer> named(JsonObject json, java.util.Set<String> names) {
        Map<String, Integer> colors = new HashMap<>();
        for (String name : names) {
            Integer value = argb(json, name);
            if (value != null) colors.put(name, value);
        }
        return Map.copyOf(colors);
    }

    private static FoliosBook.CoverText coverText(JsonObject json, String summary) {
        return new FoliosBook.CoverText(line(json, "title", FoliosBook.CoverText.TITLE), line(json, "subtitle", FoliosBook.CoverText.SUBTITLE),
                line(json, "summary", FoliosBook.CoverText.SUBTITLE), summary);
    }

    // A line of a cover's lettering, its values left out taken from its default; null when the book says nothing of it.
    private static FoliosBook.CoverText.@Nullable Line line(JsonObject json, String key, FoliosBook.CoverText.Line fallback) {
        if (!json.has(key)) return null;
        JsonObject line = json.getAsJsonObject(key);
        return new FoliosBook.CoverText.Line(
                line.has("x") ? line.get("x").getAsFloat() : fallback.x(),
                line.has("y") ? line.get("y").getAsFloat() : fallback.y(),
                line.has("size") ? Mth.clamp(line.get("size").getAsFloat(), 0.1F, 4.0F) : fallback.size(),
                line.has("width") ? Mth.clamp(line.get("width").getAsFloat(), 0.5F, 16.0F) : fallback.width());
    }

    private static FoliosBook.Handling handling(JsonObject json) {
        return new FoliosBook.Handling(!json.has("look_around") || json.get("look_around").getAsBoolean(),
                !json.has("walk") || json.get("walk").getAsBoolean());
    }

    private static FoliosEntry readEntry(Identifier book, String id, JsonObject json) {
        // Its category is the folder it is in: entries/<category>/<name>.json.
        String category = id.contains("/") ? id.substring(0, id.indexOf('/')) : "";
        List<FoliosEntry.Page> pages = new ArrayList<>();
        if (json.has("pages")) {
            int index = pages.size();
            for (JsonElement page : json.getAsJsonArray("pages")) {
                JsonObject object = page.isJsonObject() ? page.getAsJsonObject() : null;
                if (object != null && object.has("anchor") && !ANCHORS.contains(object.get("anchor").getAsString())) {
                    // A 2.x page name in a book of the current format: told, and left out, so the page still loads.
                    Folios.LOGGER.error("Page {} of the entry {} of the guide {} has \"anchor\": \"{}\", which places a page's content (top, center or bottom); "
                            + "to name the page for links, use \"marker\": \"{}\"", index + 1, id, book, object.get("anchor").getAsString(), object.get("anchor").getAsString());
                    object = object.deepCopy();
                    object.remove("anchor");
                    page = object;
                }
                pages.add(new FoliosEntry.Page(readPage(book, id, index++, page),
                        object != null && object.has("marker") ? object.get("marker").getAsString() : null,
                        object != null ? FoliosLock.read(object, book) : FoliosLock.NONE, object != null ? pageIcon(object.get("icon")) : null));
            }
        }
        return new FoliosEntry(id, category, text(json, "name", textKey(book, "entry." + id.replace('/', '.')) + ".name", id), FoliosIcon.read(json.get("icon")),
                integer(json, "sort", 0), json.has("featured") && json.get("featured").getAsBoolean(), FoliosLock.read(json, book), pages,
                json.has("reveal") ? reveal(json.get("reveal")) : null);
    }

    // What "anchor" may be on a page: where its content sits down it.
    private static final java.util.Set<String> ANCHORS = java.util.Set.of("top", "center", "bottom");

    // The icon a page shows before its title: null to take its entry's, none for "none".
    private static @Nullable FoliosIcon pageIcon(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) return null;
        if (json.isJsonPrimitive() && json.getAsString().equals("none")) return FoliosIcon.NONE;
        try {
            return FoliosIcon.read(json);
        } catch (Exception e) {
            Folios.LOGGER.warn("A page names an icon it can't read, {}: {}", json, e.getMessage());
            return null;
        }
    }

    // A page that can't be read is shown as what went wrong, where it would have been, for the book's writer to see.
    private static FoliosPageElement readPage(Identifier book, String entry, int index, JsonElement json) {
        try {
            JsonObject object;
            if (json.isJsonPrimitive()) {
                object = new JsonObject();
                object.addProperty("text", json.getAsString());
            } else {
                object = json.getAsJsonObject();
            }
            Identifier type = FoliosPageElements.id(object.has("type") ? object.get("type").getAsString() : "text");
            MapCodec<? extends FoliosPageElement> codec = FoliosPageElements.get(type);
            if (codec == null) throw new IllegalArgumentException("no page type " + type);
            FoliosPageElement page = codec.codec().parse(JsonOps.INSTANCE, object).getOrThrow(IllegalArgumentException::new);
            // How it is set: a page of elements as it says; a page of one element made one, so it is set the same way.
            var settings = net.ixdarklord.folios.internal.client.page.ElementLayout.read(object, "Page " + (index + 1) + " of the entry " + entry + " of the guide " + book);
            var observed = net.ixdarklord.folios.internal.client.page.Observed.read(object);
            if (page instanceof net.ixdarklord.folios.internal.client.page.TextPage text) return text.with(settings, observed);
            return page.spread() ? page : net.ixdarklord.folios.internal.client.page.TextPage.single(page, settings, observed);
        } catch (Exception e) {
            Folios.LOGGER.error("Couldn't read page {} of the entry {} of the guide {}: {}", index + 1, entry, book, e.getMessage());
            return new ErrorPage("Page " + (index + 1) + " couldn't be read: " + e.getMessage());
        }
    }

    // The models the book names ("models", and the cover group's "model"), the default for the rest.
    private static FoliosBook.Models models(JsonObject book, JsonObject coverGroup) {
        JsonObject json = book.has("models") ? book.getAsJsonObject("models") : new JsonObject();
        FoliosBook.Models fallback = FoliosBook.Models.DEFAULT;
        Identifier item = json.has("item") ? Identifier.parse(json.get("item").getAsString()) : fallback.item();
        Identifier open = json.has("open") ? Identifier.parse(json.get("open").getAsString()) : fallback.open();
        Identifier cover = fallback.cover();
        if (coverGroup.has("model")) {
            String named = coverGroup.get("model").getAsString();
            cover = named.equals("none") ? null : Identifier.parse(named);
        }
        boolean guiModel = json.has("gui") ? json.get("gui").getAsString().equals("model") : fallback.guiModel();
        return new FoliosBook.Models(item, open, cover, !json.has("hands") || json.get("hands").getAsBoolean(), guiModel);
    }

    // A reveal: "none", an effect's id (for its default length), or {"effect": id, "seconds": n, "once": true, "unread": true}. An effect no mod has
    // registered is warned of and shows the content at once.
    private static FoliosReveal.Use reveal(JsonElement json) {
        String effect;
        float seconds = FoliosReveals.DEFAULT_SECONDS;
        boolean once = false;
        boolean unread = false;
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            effect = string(object, "effect", FoliosReveals.DEFAULT.effect().toString());
            if (object.has("seconds")) seconds = Mth.clamp(object.get("seconds").getAsFloat(), 0.0F, 5.0F);
            once = object.has("once") && object.get("once").getAsBoolean();
            unread = object.has("unread") && object.get("unread").getAsBoolean();
        } else {
            effect = json.getAsString();
        }
        if (effect.equals("none")) return FoliosReveal.Use.NONE;
        Identifier id = effect.contains(":") ? Identifier.parse(effect) : Identifier.fromNamespaceAndPath("folios", effect);
        if (FoliosReveals.get(id) == null) {
            Folios.LOGGER.warn("A guide asks for the reveal effect {}, which no mod has registered", id);
            return FoliosReveal.Use.NONE;
        }
        return new FoliosReveal.Use(id, seconds, once, unread);
    }

    // A book's icon when it names none: its own guide book item, as the book's item is drawn.
    private static FoliosIcon ownItem(Identifier book) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", "folios:guide_book");
        JsonObject components = new JsonObject();
        components.addProperty("folios:guide_book", book.toString());
        stack.add("components", components);
        return FoliosIcon.read(stack);
    }

    // Each layer the book names, the default for the rest.
    private static FoliosBook.Textures textures(JsonObject json) {
        FoliosBook.Textures fallback = FoliosBook.Textures.DEFAULT;
        return new FoliosBook.Textures(texture(json, "page", fallback.page()), texture(json, "frame", fallback.frame()),
                texture(json, "boards", fallback.cover()), texture(json, "tab", fallback.tab()), texture(json, "tab_plate", fallback.tabPlate()),
                json.has("backdrop") ? Identifier.parse(json.get("backdrop").getAsString()) : fallback.backdrop(), texture(json, "spotlight", fallback.spotlight()));
    }

    // Each position the book sets, the default for the rest; kept within reach of the page.
    private static FoliosBook.Layout layout(JsonObject json) {
        FoliosBook.Layout fallback = FoliosBook.Layout.DEFAULT;
        JsonObject tabs = json.has("tabs") ? json.getAsJsonObject("tabs") : new JsonObject();
        JsonObject buttons = json.has("turn_buttons") ? json.getAsJsonObject("turn_buttons") : new JsonObject();
        JsonObject numbers = json.has("page_numbers") ? json.getAsJsonObject("page_numbers") : new JsonObject();
        JsonObject close = json.has("close_button") ? json.getAsJsonObject("close_button") : new JsonObject();
        return new FoliosBook.Layout(position(tabs, "x", fallback.tabX(), -20, 40), position(tabs, "y", fallback.tabY(), -20, 160),
                position(tabs, "spacing", fallback.tabSpacing(), -10, 30), position(buttons, "x", fallback.buttonX(), 0, 60),
                position(buttons, "y", fallback.buttonY(), 0, 170), position(numbers, "x", fallback.numberX(), -60, 60),
                position(numbers, "y", fallback.numberY(), 0, 170), position(close, "x", fallback.closeX(), 0, 125),
                position(close, "y", fallback.closeY(), 0, 169));
    }

    private static int position(JsonObject json, String key, int fallback, int min, int max) {
        return json.has(key) ? Mth.clamp(json.get(key).getAsInt(), min, max) : fallback;
    }

    private static Identifier texture(JsonObject json, String key, Identifier fallback) {
        return json.has(key) ? Identifier.parse(json.get(key).getAsString()) : fallback;
    }

    // Text a book writes: markup, or {"translate": ...}.
    private static String text(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? FoliosText.read(json.get(key)) : fallback;
    }

    // As text(), left out taking a translation key's text (the fallback when the language hasn't it either).
    private static String text(JsonObject json, String key, String translation, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? FoliosText.read(json.get(key)) : FoliosText.translation(translation, List.of(), fallback);
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    // A colour as a book writes it, "#RRGGBB" (opaque) or "#AARRGGBB"; null when it isn't there, logged when it can't be read.
    private static @Nullable Integer argb(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return null;
        JsonElement value = json.get(key);
        String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString().strip() : "";
        if (text.startsWith("#") && (text.length() == 7 || text.length() == 9)) {
            try {
                int read = (int) Long.parseLong(text.substring(1), 16);
                return text.length() == 7 ? 0xFF000000 | read : read;
            } catch (NumberFormatException ignored) {
                // Told below.
            }
        }
        Folios.LOGGER.error("\"{}\": {} isn't a colour; colours are written \"#RRGGBB\" or \"#AARRGGBB\"", key, value);
        return null;
    }

    /** A colour without its alpha, as "accent", the cover's and a category's are kept. */
    private static int color(JsonObject json, String key, int fallback) {
        Integer value = argb(json, key);
        return value != null ? value & 0xFFFFFF : fallback;
    }
}
