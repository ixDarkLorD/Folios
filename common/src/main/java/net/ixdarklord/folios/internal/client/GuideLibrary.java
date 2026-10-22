package net.ixdarklord.folios.internal.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.GuideReveal;
import net.ixdarklord.folios.api.client.GuideReveals;
import net.ixdarklord.folios.api.client.GuideCategory;
import net.ixdarklord.folios.api.client.GuideEntry;
import net.ixdarklord.folios.api.client.GuideLock;
import net.ixdarklord.folios.api.client.GuideStyles;
import net.ixdarklord.folios.api.client.page.GuideIcon;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePageTypes;
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
// out from them until the next reload or a change of language.
public final class GuideLibrary {
    private static final FileToIdConverter FILES = FileToIdConverter.json("folios");
    private static final int DEFAULT_ACCENT = 0x8B6CFF;
    private static final int DEFAULT_COVER = 0x3A2E58;
    private static final float DEFAULT_TILT = 6.0F;
    private static final float DEFAULT_DISTANCE = 0.64F;

    private static Map<Identifier, GuideBook> books = Map.of();
    private static final Map<Identifier, GuideSession> SESSIONS = new HashMap<>();

    private GuideLibrary() {}

    public static @Nullable GuideBook book(Identifier id) {
        return books.get(id);
    }

    public static Map<Identifier, GuideBook> books() {
        return books;
    }

    /** A book's laid-out pages, kept between openings; null when there is no such book. */
    public static @Nullable GuideSession session(Identifier id) {
        GuideBook book = books.get(id);
        if (book == null) return null;
        GuideSession session = SESSIONS.get(id);
        if (session == null || !session.isCurrent()) {
            if (session != null) session.close();
            session = new GuideSession(book);
            SESSIONS.put(id, session);
        }
        return session;
    }

    // ---- Reading ----

    private record Files(Map<String, JsonObject> categories, Map<String, JsonObject> entries) {}

    public static void reload(ResourceManager manager) {
        net.ixdarklord.folios.internal.client.handheld.CoverModel.clear();
        CoverMarkers.clear();
        Map<Identifier, JsonObject> bookFiles = new LinkedHashMap<>();
        Map<Identifier, Files> parts = new HashMap<>();
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
            if (categories > 0 && (entries < 0 || categories < entries)) {
                parts.computeIfAbsent(id.withPath(path.substring(0, categories)), book -> new Files(new LinkedHashMap<>(), new LinkedHashMap<>()))
                        .categories.put(path.substring(categories + 12), json);
            } else if (entries > 0) {
                parts.computeIfAbsent(id.withPath(path.substring(0, entries)), book -> new Files(new LinkedHashMap<>(), new LinkedHashMap<>()))
                        .entries.put(path.substring(entries + 9), json);
            }
        }
        Map<Identifier, GuideBook> loaded = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonObject> file : bookFiles.entrySet()) {
            Identifier id = file.getKey();
            try {
                loaded.put(id, readBook(id, file.getValue(), parts.getOrDefault(id, new Files(Map.of(), Map.of()))));
            } catch (Exception e) {
                Folios.LOGGER.error("Couldn't load the guide {}: {}", id, e.getMessage());
            }
        }
        for (Identifier orphan : parts.keySet()) {
            if (!bookFiles.containsKey(orphan)) Folios.LOGGER.warn("Guide files for {} have no book.json beside them", orphan);
        }
        books = Map.copyOf(loaded);
        SESSIONS.values().forEach(GuideSession::close);
        SESSIONS.clear();
        if (!loaded.isEmpty()) Folios.LOGGER.info("Loaded {} guide book(s)", loaded.size());
    }

    private static GuideBook readBook(Identifier id, JsonObject json, Files files) {
        String name = string(json, "name", "guide." + id.getNamespace() + "." + id.getPath().replace('/', '.') + ".name");
        Identifier style = json.has("style") ? GuideStyles.id(json.get("style").getAsString()) : GuideStyles.SCREEN;
        List<GuideCategory> categories = new ArrayList<>();
        for (Map.Entry<String, JsonObject> file : files.categories.entrySet()) {
            try {
                JsonObject category = file.getValue();
                categories.add(new GuideCategory(file.getKey(), string(category, "name", file.getKey()), string(category, "description", ""),
                        GuideIcon.read(category.get("icon")), integer(category, "sort", 0), GuideLock.read(category), color(category, "color", -1)));
            } catch (Exception e) {
                Folios.LOGGER.error("Couldn't load the category {} of the guide {}: {}", file.getKey(), id, e.getMessage());
            }
        }
        List<GuideEntry> entries = new ArrayList<>();
        for (Map.Entry<String, JsonObject> file : files.entries.entrySet()) {
            try {
                GuideEntry entry = readEntry(id, file.getKey(), file.getValue());
                if (categories.stream().noneMatch(category -> category.id().equals(entry.category()))) {
                    Folios.LOGGER.warn("The entry {} of the guide {} is in the category {}, which the book doesn't have", entry.id(), id, entry.category());
                    continue;
                }
                entries.add(entry);
            } catch (Exception e) {
                Folios.LOGGER.error("Couldn't load the entry {} of the guide {}: {}", file.getKey(), id, e.getMessage());
            }
        }
        return new GuideBook(id, name, string(json, "subtitle", ""), string(json, "landing", ""), style,
                color(json, "accent", DEFAULT_ACCENT), color(json, "cover", DEFAULT_COVER),
                json.has("cover_texture") ? Identifier.parse(json.get("cover_texture").getAsString()) : null,
                json.has("icon") ? GuideIcon.read(json.get("icon")) : ownItem(id),
                models(json),
                !json.has("show_progress") || json.get("show_progress").getAsBoolean(),
                json.has("tilt") ? Mth.clamp(json.get("tilt").getAsFloat(), -45.0F, 80.0F) : DEFAULT_TILT,
                json.has("distance") ? Mth.clamp(json.get("distance").getAsFloat(), 0.3F, 1.5F) : DEFAULT_DISTANCE,
                json.has("page_turn") ? GuideBook.PageTurn.valueOf(json.get("page_turn").getAsString().toUpperCase(java.util.Locale.ROOT)) : GuideBook.PageTurn.BEND,
                json.has("reveal") ? reveal(json.get("reveal")) : GuideReveals.DEFAULT,
                textures(json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject()),
                layout(json.has("layout") ? json.getAsJsonObject("layout") : new JsonObject()),
                // A handheld book is put away by a click off it unless it says otherwise; a screen book stays.
                json.has("close_on_click_outside") ? json.get("close_on_click_outside").getAsBoolean() : style.equals(GuideStyles.HANDHELD),
                handling(json.has("handheld") ? json.getAsJsonObject("handheld") : new JsonObject()),
                colors(json.has("colors") ? json.getAsJsonObject("colors") : new JsonObject()),
                coverText(json.has("cover_text") ? json.getAsJsonObject("cover_text") : new JsonObject(), string(json, "summary", "")),
                json.has("progress") ? GuideBook.Progress.valueOf(json.get("progress").getAsString().toUpperCase(java.util.Locale.ROOT)) : GuideBook.Progress.PLAYER,
                categories, entries);
    }

    private static final java.util.Set<String> PAGE_COLORS = java.util.Set.of("paper", "text", "muted", "heading", "accent", "link", "panel", "line");

    private static GuideBook.Colors colors(JsonObject json) {
        return new GuideBook.Colors(named(json, PAGE_COLORS), json.has("dark") ? named(json.getAsJsonObject("dark"), PAGE_COLORS) : Map.of(),
                json.has("tooltip") ? named(json.getAsJsonObject("tooltip"), java.util.Set.of("background", "frame", "title", "detail")) : Map.of());
    }

    // The colours of a block of a book's scheme, opaque unless given with their alpha.
    private static Map<String, Integer> named(JsonObject json, java.util.Set<String> names) {
        Map<String, Integer> colors = new HashMap<>();
        for (String name : names) {
            if (!json.has(name)) continue;
            String text = json.get(name).getAsString().strip();
            if (text.startsWith("#")) text = text.substring(1);
            int value = (int) Long.parseLong(text, 16);
            colors.put(name, text.length() > 6 ? value : 0xFF000000 | value);
        }
        return Map.copyOf(colors);
    }

    private static GuideBook.CoverText coverText(JsonObject json, String summary) {
        return new GuideBook.CoverText(line(json, "title", GuideBook.CoverText.TITLE), line(json, "subtitle", GuideBook.CoverText.SUBTITLE),
                line(json, "summary", GuideBook.CoverText.SUBTITLE), summary);
    }

    // A line of a cover's lettering, its values left out taken from its default; null when the book says nothing of it.
    private static GuideBook.CoverText.@Nullable Line line(JsonObject json, String key, GuideBook.CoverText.Line fallback) {
        if (!json.has(key)) return null;
        JsonObject line = json.getAsJsonObject(key);
        return new GuideBook.CoverText.Line(
                line.has("x") ? line.get("x").getAsFloat() : fallback.x(),
                line.has("y") ? line.get("y").getAsFloat() : fallback.y(),
                line.has("size") ? Mth.clamp(line.get("size").getAsFloat(), 0.1F, 4.0F) : fallback.size(),
                line.has("width") ? Mth.clamp(line.get("width").getAsFloat(), 0.5F, 16.0F) : fallback.width());
    }

    private static GuideBook.Handling handling(JsonObject json) {
        return new GuideBook.Handling(!json.has("look_around") || json.get("look_around").getAsBoolean(),
                !json.has("walk") || json.get("walk").getAsBoolean());
    }

    private static GuideEntry readEntry(Identifier book, String id, JsonObject json) {
        String category = json.has("category") ? json.get("category").getAsString() : id.contains("/") ? id.substring(0, id.indexOf('/')) : "";
        List<GuideEntry.Page> pages = new ArrayList<>();
        // An entry of nothing but text can say so: "text" in place of "pages".
        if (json.has("text")) pages.add(new GuideEntry.Page(readPage(book, id, 0, new JsonPrimitive(json.get("text").getAsString())), null, GuideLock.NONE));
        if (json.has("pages")) {
            int index = pages.size();
            for (JsonElement page : json.getAsJsonArray("pages")) {
                JsonObject object = page.isJsonObject() ? page.getAsJsonObject() : null;
                pages.add(new GuideEntry.Page(readPage(book, id, index++, page),
                        object != null && object.has("anchor") ? object.get("anchor").getAsString() : null,
                        object != null ? GuideLock.read(object) : GuideLock.NONE));
            }
        }
        return new GuideEntry(id, category, string(json, "name", id), GuideIcon.read(json.get("icon")), integer(json, "sort", 0),
                json.has("featured") && json.get("featured").getAsBoolean(), GuideLock.read(json), pages, json.has("reveal") ? reveal(json.get("reveal")) : null);
    }

    // A page that can't be read is shown as what went wrong, where it would have been, for the book's writer to see.
    private static GuidePage readPage(Identifier book, String entry, int index, JsonElement json) {
        try {
            JsonObject object;
            if (json.isJsonPrimitive()) {
                object = new JsonObject();
                object.addProperty("text", json.getAsString());
            } else {
                object = json.getAsJsonObject();
            }
            Identifier type = GuidePageTypes.id(object.has("type") ? object.get("type").getAsString() : "text");
            MapCodec<? extends GuidePage> codec = GuidePageTypes.get(type);
            if (codec == null) throw new IllegalArgumentException("no page type " + type);
            return codec.codec().parse(JsonOps.INSTANCE, object).getOrThrow(IllegalArgumentException::new);
        } catch (Exception e) {
            Folios.LOGGER.error("Couldn't read page {} of the entry {} of the guide {}: {}", index + 1, entry, book, e.getMessage());
            return new ErrorPage("Page " + (index + 1) + " couldn't be read: " + e.getMessage());
        }
    }

    // The models the book names, the default for the rest; "item_model" as well, as it was before "models".
    private static GuideBook.Models models(JsonObject book) {
        JsonObject json = book.has("models") ? book.getAsJsonObject("models") : new JsonObject();
        GuideBook.Models fallback = GuideBook.Models.DEFAULT;
        Identifier item = json.has("item") ? Identifier.parse(json.get("item").getAsString())
                : book.has("item_model") ? Identifier.parse(book.get("item_model").getAsString()) : fallback.item();
        Identifier open = json.has("open") ? Identifier.parse(json.get("open").getAsString()) : fallback.open();
        Identifier cover = fallback.cover();
        if (json.has("cover")) {
            String named = json.get("cover").getAsString();
            cover = named.equals("none") ? null : Identifier.parse(named);
        }
        boolean guiModel = json.has("gui") ? json.get("gui").getAsString().equals("model") : fallback.guiModel();
        return new GuideBook.Models(item, open, cover, !json.has("hands") || json.get("hands").getAsBoolean(), guiModel);
    }

    // A reveal: "none", an effect's id (for its default length), or {"effect": id, "seconds": n, "once": true, "unread": true}. An effect no mod has
    // registered is warned of and shows the content at once.
    private static GuideReveal.Use reveal(JsonElement json) {
        String effect;
        float seconds = GuideReveals.DEFAULT_SECONDS;
        boolean once = false;
        boolean unread = false;
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            effect = string(object, "effect", GuideReveals.DEFAULT.effect().toString());
            if (object.has("seconds")) seconds = Mth.clamp(object.get("seconds").getAsFloat(), 0.0F, 5.0F);
            once = object.has("once") && object.get("once").getAsBoolean();
            unread = object.has("unread") && object.get("unread").getAsBoolean();
        } else {
            effect = json.getAsString();
        }
        if (effect.equals("none")) return GuideReveal.Use.NONE;
        Identifier id = effect.contains(":") ? Identifier.parse(effect) : Identifier.fromNamespaceAndPath("folios", effect);
        if (GuideReveals.get(id) == null) {
            Folios.LOGGER.warn("A guide asks for the reveal effect {}, which no mod has registered", id);
            return GuideReveal.Use.NONE;
        }
        return new GuideReveal.Use(id, seconds, once, unread);
    }

    // A book's icon when it names none: its own guide book item, as the book's item is drawn.
    private static GuideIcon ownItem(Identifier book) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", "folios:guide_book");
        JsonObject components = new JsonObject();
        components.addProperty("folios:guide_book", book.toString());
        stack.add("components", components);
        return GuideIcon.read(stack);
    }

    // Each layer the book names, the default for the rest.
    private static GuideBook.Textures textures(JsonObject json) {
        GuideBook.Textures fallback = GuideBook.Textures.DEFAULT;
        return new GuideBook.Textures(texture(json, "page", fallback.page()), texture(json, "frame", fallback.frame()),
                texture(json, "cover", fallback.cover()), texture(json, "tab", fallback.tab()), texture(json, "tab_plate", fallback.tabPlate()),
                json.has("backdrop") ? Identifier.parse(json.get("backdrop").getAsString()) : fallback.backdrop());
    }

    // Each position the book sets, the default for the rest; kept within reach of the page.
    private static GuideBook.Layout layout(JsonObject json) {
        GuideBook.Layout fallback = GuideBook.Layout.DEFAULT;
        JsonObject tabs = json.has("tabs") ? json.getAsJsonObject("tabs") : new JsonObject();
        JsonObject buttons = json.has("turn_buttons") ? json.getAsJsonObject("turn_buttons") : new JsonObject();
        JsonObject numbers = json.has("page_numbers") ? json.getAsJsonObject("page_numbers") : new JsonObject();
        JsonObject close = json.has("close_button") ? json.getAsJsonObject("close_button") : new JsonObject();
        return new GuideBook.Layout(position(tabs, "x", fallback.tabX(), -20, 40), position(tabs, "y", fallback.tabY(), -20, 160),
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

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    /** "#RRGGBB", "RRGGBB" or a number. */
    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt() & 0xFFFFFF;
        String text = value.getAsString().strip();
        if (text.startsWith("#")) text = text.substring(1);
        return Integer.parseInt(text, 16) & 0xFFFFFF;
    }
}
