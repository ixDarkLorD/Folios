package net.ixdarklord.folios.internal.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ixdarklord.folios.api.FoliosFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class BookUpgrader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record Result(Map<String, JsonObject> files, Map<String, String> lang, Map<String, List<String>> changes) {}

    private BookUpgrader() {}

    public static Result upgrade(String book, Map<String, JsonObject> files, String from, String to) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        files.forEach((path, json) -> out.put(path, json.deepCopy()));
        Map<String, String> lang = new LinkedHashMap<>();
        Map<String, List<String>> changes = new TreeMap<>();
        for (String format : FoliosFormat.KNOWN) {
            if (FoliosFormat.compare(format, from) <= 0 || FoliosFormat.compare(format, to) > 0) continue;
            JsonObject step = step(format);
            for (JsonElement change : step.getAsJsonArray("changes")) apply(book, change.getAsJsonObject(), out, lang, changes, false);
            JsonObject bookJson = out.get("book.json");
            if (bookJson != null) bookJson.addProperty("format_version", format);
        }
        if (out.containsKey("book.json")) note(changes, "book.json", "format_version set to \"" + to + "\"");
        return new Result(out, lang, changes);
    }

    public static Map<String, List<String>> check(String book, Map<String, JsonObject> files, String format) {
        Map<String, JsonObject> copy = new LinkedHashMap<>();
        files.forEach((path, json) -> copy.put(path, json.deepCopy()));
        Map<String, List<String>> changes = new TreeMap<>();
        Map<String, String> lang = new LinkedHashMap<>();
        for (JsonElement change : step(format).getAsJsonArray("changes")) apply(book, change.getAsJsonObject(), copy, lang, changes, true);
        return changes;
    }

    public static JsonObject step(String format) {
        try (InputStream stream = BookUpgrader.class.getResourceAsStream("/folios/upgrades/" + format + ".json")) {
            if (stream == null) throw new IllegalStateException("Folios has no upgrade to format " + format);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void apply(String book, JsonObject change, Map<String, JsonObject> files, Map<String, String> lang, Map<String, List<String>> changes,
                              boolean checking) {
        if (checking && change.has("check") && !change.get("check").getAsBoolean()) return;
        if (checking && change.has("run") && !(change.has("check") && change.get("check").getAsBoolean())) return;
        String in = change.get("in").getAsString();
        JsonObject where = change.has("where") ? change.getAsJsonObject("where") : null;
        if (change.has("run") && change.get("run").getAsString().equals("entry_folder")) {
            entryFolders(files, changes);
            return;
        }
        files.forEach((path, json) -> {
            for (Target target : targets(in, path, json)) {
                if (where != null && !matches(target.json, where)) continue;
                List<String> done = new ArrayList<>();
                if (change.has("rename")) {
                    for (Map.Entry<String, JsonElement> rename : change.getAsJsonObject("rename").entrySet()) {
                        String to = rename.getValue().getAsString();
                        if (!target.json.has(rename.getKey())) continue;
                        if (present(target.json.get(to))) {
                            done.add(kept(rename.getKey(), to));
                            continue;
                        }
                        target.json.add(to, target.json.remove(rename.getKey()));
                        done.add(rename.getKey() + " renamed to " + to);
                    }
                }
                if (change.has("remove")) {
                    for (JsonElement key : change.getAsJsonArray("remove")) {
                        if (target.json.remove(key.getAsString()) != null) done.add(key.getAsString() + " removed");
                    }
                }
                if (change.has("move")) {
                    JsonObject move = change.getAsJsonObject("move");
                    move(target.json, move.get("from").getAsString(), move.get("to").getAsString(), done);
                }
                if (change.has("run")) done.addAll(run(change.get("run").getAsString(), book, change, target.json, lang));
                for (String line : done) note(changes, path, target.where.isEmpty() ? line : target.where + ": " + line);
            }
        });
    }

    private static List<String> run(String name, String book, JsonObject change, JsonObject json, Map<String, String> lang) {
        List<String> done = new ArrayList<>();
        switch (name) {
            case "legacy_name" -> {
                if (!json.has("name")) {
                    String key = "guide." + book.replace(':', '.').replace('/', '.') + ".name";
                    json.addProperty("name", key);
                    done.add("name set to the translation 2.x used, " + key);
                }
            }
            case "cover_group" -> {
                if (json.has("cover") && json.get("cover").isJsonPrimitive()) {
                    JsonElement color = json.remove("cover");
                    put(json, "cover.color", color);
                    done.add("cover moved to cover.color");
                }
                for (String[] move : new String[][]{{"cover_texture", "cover.texture"}, {"cover_text", "cover.text"}, {"models.cover", "cover.model"},
                        {"textures.cover", "textures.boards"}}) {
                    move(json, move[0], move[1], done);
                }
            }
            case "progress_scope" -> {
                if (json.has("progress") && present(json.get("progress_scope"))) done.add(kept("progress", "progress_scope"));
                else if (json.has("progress")) {
                    String scope = json.remove("progress").getAsString().equals("book") ? "copy" : "player";
                    json.addProperty("progress_scope", scope);
                    done.add("progress is progress_scope: \"" + scope + "\"");
                }
            }
            case "color_strings" -> {
                for (String key : new String[]{"accent", "color", "cover.color"}) colorString(json, key, done);
                for (String group : new String[]{"colors", "colors.dark", "panel"}) {
                    JsonElement at = get(json, group);
                    if (at == null || !at.isJsonObject()) continue;
                    for (String key : List.copyOf(at.getAsJsonObject().keySet())) colorString(json, group + "." + key, done);
                }
            }
            case "entry_text" -> {
                if (json.has("text")) {
                    JsonElement text = json.remove("text");
                    JsonArray pages = new JsonArray();
                    pages.add(text);
                    JsonElement had = json.get("pages");
                    if (had != null && had.isJsonArray()) pages.addAll(had.getAsJsonArray());
                    else if (present(had)) pages.add(had);
                    json.add("pages", pages);
                    done.add("text made the first of pages");
                }
            }
            case "plural_lists" -> {
                for (String[] keys : new String[][]{{"structures", "structure"}, {"builds", "build"}}) {
                    if (!json.has(keys[0])) continue;
                    JsonArray all = new JsonArray();
                    for (JsonElement part : new JsonElement[]{json.remove(keys[1]), json.remove(keys[0])}) {
                        if (part == null) continue;
                        if (part.isJsonArray()) all.addAll(part.getAsJsonArray());
                        else all.add(part);
                    }
                    json.add(keys[1], all.size() == 1 ? all.get(0) : all);
                    done.add(keys[0] + " joined into " + keys[1]);
                }
            }
            case "shown_labels" -> {
                for (String key : new String[]{"structure", "build"}) {
                    JsonElement value = json.get(key);
                    if (value == null) continue;
                    List<JsonElement> shown = new ArrayList<>();
                    if (value.isJsonArray()) value.getAsJsonArray().forEach(shown::add);
                    else shown.add(value);
                    for (JsonElement one : shown) {
                        if (!one.isJsonObject() || !one.getAsJsonObject().has("name")) continue;
                        if (present(one.getAsJsonObject().get("label"))) {
                            done.add(key + ": " + kept("name", "label"));
                            continue;
                        }
                        one.getAsJsonObject().add("label", one.getAsJsonObject().remove("name"));
                        done.add(key + ": name renamed to label");
                    }
                }
            }
            case "spread_multiblock" -> {
                if (json.has("build") && json.get("build").isJsonObject() && present(json.get("multiblock"))) {
                    done.add(kept("build", "multiblock"));
                } else if (json.has("build") && json.get("build").isJsonObject()) {
                    json.add("multiblock", json.remove("build"));
                    done.add("build (blocks written in place) renamed to multiblock");
                }
                JsonElement nodes = get(json, "map.nodes");
                if (nodes != null && nodes.isJsonArray()) {
                    for (JsonElement node : nodes.getAsJsonArray()) {
                        if (!node.isJsonObject() || !node.getAsJsonObject().has("name")) continue;
                        if (present(node.getAsJsonObject().get("label"))) {
                            done.add("map: a place's " + kept("name", "label"));
                            continue;
                        }
                        node.getAsJsonObject().add("label", node.getAsJsonObject().remove("name"));
                        done.add("map: a place's name renamed to label");
                    }
                }
            }
            case "requires" -> {
                JsonArray conditions = new JsonArray();
                for (String key : new String[]{"advancement", "flag", "obtained"}) {
                    if (!json.has(key)) continue;
                    JsonObject condition = new JsonObject();
                    condition.add(key, json.remove(key));
                    conditions.add(condition);
                    done.add(key + " made a condition of requires");
                }
                if (conditions.isEmpty()) break;
                JsonElement had = json.remove("requires");
                if (present(had)) {
                    if (had.isJsonArray()) conditions.addAll(had.getAsJsonArray());
                    else conditions.add(had);
                }
                json.add("requires", conditions.size() == 1 ? conditions.get(0) : conditions);
            }
            default -> throw new IllegalArgumentException("No upgrade step " + name);
        }
        return done;
    }

    private static void colorString(JsonObject json, String path, List<String> done) {
        JsonElement value = get(json, path);
        if (value == null || !value.isJsonPrimitive()) return;
        com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
        String written = null;
        if (primitive.isNumber()) {
            try {
                long whole = primitive.getAsBigDecimal().longValueExact();
                if (whole >= Integer.MIN_VALUE && whole <= 0xFFFFFFFFL) {
                    long argb = whole & 0xFFFFFFFFL;
                    written = argb > 0xFFFFFF ? String.format("#%08X", argb) : String.format("#%06X", argb);
                }
            } catch (ArithmeticException e) {
            }
        } else if (primitive.isString()) {
            String text = primitive.getAsString().strip();
            boolean marked = text.startsWith("#");
            String hex = marked ? text.substring(1) : text;
            if (hex.matches("[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}")) {
                written = "#" + (marked ? hex : hex.toUpperCase(java.util.Locale.ROOT));
                if (written.equals(primitive.getAsString())) return;
            }
        }
        if (written == null) {
            done.add(path + " is " + value + ", which isn't a colour: left for you to write as \"#RRGGBB\"");
            return;
        }
        take(json, path);
        put(json, path, new com.google.gson.JsonPrimitive(written));
        done.add(path + " written as \"" + written + "\"");
    }

    private static void entryFolders(Map<String, JsonObject> files, Map<String, List<String>> changes) {
        for (String path : List.copyOf(files.keySet())) {
            if (!path.startsWith("entries/")) continue;
            JsonObject json = files.get(path);
            if (!json.has("category")) continue;
            JsonElement named = json.get("category");
            if (!named.isJsonPrimitive()) {
                note(changes, path, "category is " + named + ", which isn't a category's id: left for you to fix");
                continue;
            }
            String category = named.getAsString();
            String inside = path.substring("entries/".length());
            int slash = inside.indexOf('/');
            String folder = slash >= 0 ? inside.substring(0, slash) : "";
            if (folder.equals(category)) {
                json.remove("category");
                note(changes, path, "category removed (the folder says it)");
                continue;
            }
            String moved = "entries/" + category + "/" + (slash >= 0 ? inside.substring(slash + 1) : inside);
            if (files.containsKey(moved)) {
                note(changes, path, "can't move to " + moved + ", the folder of its category " + category
                        + ": another entry is there. Left where it is, with its category, for you to settle");
                continue;
            }
            json.remove("category");
            files.remove(path);
            files.put(moved, json);
            note(changes, path, "moved to " + moved + ", the folder of its category " + category
                    + " (its id changes: update links to it by hand)");
        }
    }

    private record Target(JsonObject json, String where) {}

    private static List<Target> targets(String in, String path, JsonObject json) {
        List<Target> found = new ArrayList<>();
        String kind = path.equals("book.json") ? "book" : path.startsWith("categories/") ? "category" : path.startsWith("entries/") ? "entry"
                : path.startsWith("builds/") ? "build" : "";
        if (in.equals(kind) || in.equals("lockable") && (kind.equals("category") || kind.equals("entry"))) found.add(new Target(json, ""));
        if (!kind.equals("entry") || !json.has("pages") || !json.get("pages").isJsonArray()) return found;
        if (!List.of("page", "element", "objective", "lockable").contains(in)) return found;
        JsonArray pages = json.getAsJsonArray("pages");
        for (int index = 0; index < pages.size(); index++) {
            if (!pages.get(index).isJsonObject()) continue;
            JsonObject page = pages.get(index).getAsJsonObject();
            String where = "page " + (index + 1);
            if (in.equals("page") || in.equals("lockable")) {
                found.add(new Target(page, where));
                continue;
            }
            List<Target> elements = new ArrayList<>();
            if (!page.has("elements")) elements.add(new Target(page, where));
            else if (page.get("elements").isJsonArray()) {
                JsonArray list = page.getAsJsonArray("elements");
                for (int element = 0; element < list.size(); element++) {
                    if (list.get(element).isJsonObject()) elements.add(new Target(list.get(element).getAsJsonObject(), where + ", element " + (element + 1)));
                }
            }
            if (in.equals("element")) {
                found.addAll(elements);
                continue;
            }
            for (Target element : elements) {
                if (!element.json.has("objectives") || !element.json.get("objectives").isJsonArray()) continue;
                JsonArray objectives = element.json.getAsJsonArray("objectives");
                for (int objective = 0; objective < objectives.size(); objective++) {
                    if (objectives.get(objective).isJsonObject()) found.add(new Target(objectives.get(objective).getAsJsonObject(), element.where + ", objective " + (objective + 1)));
                }
            }
        }
        return found;
    }

    private static boolean matches(JsonObject json, JsonObject where) {
        for (Map.Entry<String, JsonElement> value : where.entrySet()) {
            JsonElement has = json.get(value.getKey());
            if (has == null && value.getKey().equals("type")) has = new com.google.gson.JsonPrimitive("text");
            if (!value.getValue().equals(has)) return false;
        }
        return true;
    }

    private static JsonElement get(JsonObject json, String path) {
        String[] keys = path.split("\\.");
        JsonElement at = json;
        for (String key : keys) {
            if (!at.isJsonObject() || !at.getAsJsonObject().has(key)) return null;
            at = at.getAsJsonObject().get(key);
        }
        return at;
    }

    private static JsonElement take(JsonObject json, String path) {
        String[] keys = path.split("\\.");
        List<JsonObject> chain = new ArrayList<>();
        JsonObject at = json;
        for (int index = 0; index < keys.length - 1; index++) {
            if (!at.has(keys[index]) || !at.get(keys[index]).isJsonObject()) return null;
            chain.add(at);
            at = at.getAsJsonObject(keys[index]);
        }
        JsonElement value = at.remove(keys[keys.length - 1]);
        if (value == null) return null;
        for (int index = chain.size() - 1; index >= 0 && at.size() == 0; index--) {
            chain.get(index).remove(keys[index]);
            at = chain.get(index);
        }
        return value;
    }

    private static void move(JsonObject json, String from, String to, List<String> done) {
        if (get(json, from) == null) return;
        if (present(get(json, to))) {
            done.add(kept(from, to));
            return;
        }
        put(json, to, take(json, from));
        done.add(from + " moved to " + to);
    }

    private static boolean present(JsonElement value) {
        return value != null && !value.isJsonNull();
    }

    private static String kept(String from, String to) {
        return from + " left as it is: " + to + " is already there (merge them by hand)";
    }

    private static void put(JsonObject json, String path, JsonElement value) {
        String[] keys = path.split("\\.");
        JsonObject at = json;
        for (int index = 0; index < keys.length - 1; index++) {
            if (!at.has(keys[index]) || !at.get(keys[index]).isJsonObject()) at.add(keys[index], new JsonObject());
            at = at.getAsJsonObject(keys[index]);
        }
        at.add(keys[keys.length - 1], value);
    }

    private static void note(Map<String, List<String>> changes, String path, String line) {
        changes.computeIfAbsent(path, file -> new ArrayList<>()).add(line);
    }

    private static Path folios(Path bookJson) {
        for (Path at = bookJson.getParent(); at != null && at.getParent() != null; at = at.getParent()) {
            if (at.getFileName().toString().equals("folios") && at != bookJson.getParent()) return at;
        }
        return null;
    }

    private static void check(Path in) throws IOException {
        List<Path> books;
        try (Stream<Path> walk = Files.walk(in)) {
            books = walk.filter(path -> path.getFileName().toString().equals("book.json") && folios(path) != null).toList();
        }
        for (Path bookJson : books) {
            Path folder = bookJson.getParent();
            Map<String, JsonObject> files = new LinkedHashMap<>();
            try (Stream<Path> walk = Files.walk(folder)) {
                for (Path file : walk.filter(path -> path.toString().endsWith(".json")).toList()) {
                    files.put(folder.relativize(file).toString().replace('\\', '/'), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                }
            }
            JsonElement named = files.get("book.json").get("format_version");
            if (named == null || FoliosFormat.compare(FoliosFormat.of(named.getAsString()), FoliosFormat.CURRENT) < 0) continue;
            Map<String, List<String>> old = check(folder.getFileName().toString(), files, FoliosFormat.CURRENT);
            System.out.println(folder.getFileName() + (old.isEmpty() ? ": clean" : ":"));
            old.forEach((path, lines) -> lines.forEach(line -> System.out.println("  " + path + ": " + line)));
        }
    }

    public static void main(String[] args) throws IOException {
        if (args[0].equals("--check")) {
            check(Path.of(args[1]));
            return;
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        List<Path> books;
        try (Stream<Path> walk = Files.walk(in)) {
            books = walk.filter(path -> path.getFileName().toString().equals("book.json") && folios(path) != null).toList();
        }
        Map<Path, Map<String, String>> langs = new LinkedHashMap<>();
        for (Path bookJson : books) {
            Path folder = bookJson.getParent();
            Path folios = folios(bookJson);
            Path assets = folios.getParent();
            String id = assets.getFileName() + ":" + folios.relativize(folder).toString().replace(java.io.File.separatorChar, '/');
            Map<String, JsonObject> files = new LinkedHashMap<>();
            try (Stream<Path> walk = Files.walk(folder)) {
                for (Path file : walk.filter(path -> path.toString().endsWith(".json")).toList()) {
                    files.put(folder.relativize(file).toString().replace('\\', '/'), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                }
            }
            JsonElement named = files.get("book.json").get("format_version");
            String from = named != null ? FoliosFormat.of(named.getAsString()) : FoliosFormat.LEGACY;
            Result result = upgrade(id, files, from, FoliosFormat.CURRENT);
            for (Map.Entry<String, JsonObject> file : result.files().entrySet()) {
                Path target = out.resolve(in.relativize(folder.resolve(file.getKey())));
                Files.createDirectories(target.getParent());
                Files.writeString(target, GSON.toJson(file.getValue()) + "\n");
            }
            langs.computeIfAbsent(assets.resolve("lang/en_us.json"), path -> new LinkedHashMap<>()).putAll(result.lang());
        }
        for (Map.Entry<Path, Map<String, String>> lang : langs.entrySet()) {
            if (lang.getValue().isEmpty()) continue;
            JsonObject merged = Files.exists(lang.getKey()) ? JsonParser.parseString(Files.readString(lang.getKey())).getAsJsonObject() : new JsonObject();
            lang.getValue().forEach(merged::addProperty);
            Path target = out.resolve(in.relativize(lang.getKey()));
            Files.createDirectories(target.getParent());
            Files.writeString(target, GSON.toJson(merged) + "\n");
        }
    }
}
