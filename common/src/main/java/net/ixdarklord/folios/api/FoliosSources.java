package net.ixdarklord.folios.api;

import net.ixdarklord.folios.internal.FoliosSourceData;
import net.ixdarklord.folios.internal.client.FoliosBuiltinSources;
import net.ixdarklord.folios.internal.client.FoliosProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Live lists a book's pages can show: a page element with {@code "observe": {"source": "mymod:spells", "row": "..."}}
 * writes a line for each row of the source, and is written again whenever the list changes, so content added by addons
 * or data packs shows in the book without anyone editing it.
 * <p>
 * A source is rows, each an id and named fields (text, which may be markup or a translation key):
 * <pre>{@code
 * // On the client: rows the client knows (registries, client config, what addons register).
 * FoliosSources.register(MyMod.id("spells"), () -> Spells.all().stream()
 *         .map(spell -> FoliosSources.row(spell.id().toString()).field("name", spell.nameKey()).field("mana", spell.cost()))
 *         .toList());
 * FoliosSources.changed(MyMod.id("spells"));                  // an addon added one
 *
 * // On the server: rows that live there (data-driven content), sent to every player by Folios.
 * FoliosSources.registerServer(MyMod.id("rituals"), server -> Rituals.all(server).stream()
 *         .map(ritual -> FoliosSources.row(ritual.id().toString()).field("name", ritual.title()))
 *         .toList());
 * FoliosSources.changedServer(server, MyMod.id("rituals"));   // resent to everyone
 * }</pre>
 * Server sources are sent to each player as they join and after every data pack reload. A data pack can also add rows to
 * any source with no code: {@code data/<namespace>/folios/sources/<name>.json},
 * {@code {"source": "mymod:rituals", "rows": [{"id": "mypack:moon", "name": "Moonlit"}]}}; a row with an id already in
 * the source takes its place, {@code {"id": "mymod:old", "remove": true}} takes a row out (whoever gave it), and
 * {@code "requires_mods": ["create"]}, on a file or a row, leaves it out unless those mods are loaded.
 * <p>
 * Folios gives a few sources itself, worked out from the game: {@code folios:items}, {@code folios:blocks},
 * {@code folios:entities} and {@code folios:advancements}, narrowed by the page's {@code "of"} (a tag or a mod).
 */
public final class FoliosSources {
    private static final Map<Identifier, Supplier<List<Row>>> CLIENT = new ConcurrentHashMap<>();
    private static final Map<Identifier, Function<MinecraftServer, List<Row>>> SERVER = new ConcurrentHashMap<>();

    private FoliosSources() {}

    /** A row of a source: its id, and its fields by name. */
    public record Row(String id, Map<String, String> fields) {
        public Row {
            fields = Map.copyOf(fields);
        }

        /** A field's text; empty when the row hasn't it. {@code "id"} is the row's id unless a field says otherwise. */
        public String field(String name) {
            String value = fields.get(name);
            if (value != null) return value;
            return name.equals("id") ? id : "";
        }

        /** This row with another's fields over its own: what a data pack's row with a used id does. */
        public Row with(Row other) {
            Map<String, String> merged = new LinkedHashMap<>(fields);
            merged.putAll(other.fields);
            return new Row(id, merged);
        }
    }

    /** A row to fill in: {@code FoliosSources.row(id).field("name", ...).field("cost", 3)}. */
    public static Builder row(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final Map<String, String> fields = new LinkedHashMap<>();

        private Builder(String id) {
            this.id = id;
        }

        /** A field: text (markup, or a translation key), a number or a flag, kept as text. */
        public Builder field(String name, Object value) {
            fields.put(name, String.valueOf(value));
            return this;
        }

        public Row build() {
            return new Row(id, fields);
        }
    }

    // ---- The client ----

    /** Adds a source the client works out itself (call on the client; asked when a page observing it is laid out). */
    public static void register(Identifier id, Supplier<List<Row>> rows) {
        CLIENT.put(id, rows);
    }

    /** Tells books a client source has changed, so pages observing it are written again. */
    public static void changed(Identifier id) {
        FoliosProgress.changed();
    }

    /** A source's rows as the client has them, as {@link #rows(Identifier, String)} with nothing to narrow it. */
    public static List<Row> rows(Identifier id) {
        return rows(id, null);
    }

    /**
     * A source's rows as the client has them: a source Folios gives itself ({@code folios:items}, ...), narrowed by
     * {@code of}; then those the server sent; then those of a client source of the same id, a row whose id is already
     * there taking its place; less those data packs removed. Empty for a source no one has given.
     */
    public static List<Row> rows(Identifier id, @Nullable String of) {
        Map<String, Row> rows = new LinkedHashMap<>();
        List<Row> builtin = FoliosBuiltinSources.rows(id, of);
        if (builtin != null) {
            for (Row row : builtin) rows.put(row.id(), row);
        }
        for (Row row : FoliosSourceData.received(id)) rows.merge(row.id(), row, Row::with);
        Supplier<List<Row>> client = CLIENT.get(id);
        if (client != null) {
            for (Row row : client.get()) rows.merge(row.id(), row, Row::with);
        }
        for (String removed : FoliosSourceData.removed(id)) rows.remove(removed);
        return List.copyOf(rows.values());
    }

    /** Whether anyone has given the source: Folios itself, a server, a client source, or a data pack. */
    public static boolean exists(Identifier id) {
        return FoliosBuiltinSources.has(id) || CLIENT.containsKey(id) || FoliosSourceData.hasReceived(id);
    }

    // ---- The server ----

    /** Adds a source the server works out (data-driven content), sent to every player. */
    public static void registerServer(Identifier id, Function<MinecraftServer, List<Row>> rows) {
        SERVER.put(id, rows);
    }

    /** Sends a server source to every player again, after it changed. */
    public static void changedServer(MinecraftServer server, Identifier id) {
        FoliosSourceData.send(server, id);
    }

    /** A server source's rows now (less those data packs removed, with those they added); for Folios's sending. */
    public static List<Row> serverRows(MinecraftServer server, Identifier id) {
        Map<String, Row> rows = new LinkedHashMap<>();
        Function<MinecraftServer, List<Row>> source = SERVER.get(id);
        if (source != null) {
            for (Row row : source.apply(server)) rows.merge(row.id(), row, Row::with);
        }
        for (String removed : FoliosSourceData.packRemoved(id)) rows.remove(removed);
        for (Row row : FoliosSourceData.packRows(id)) rows.merge(row.id(), row, Row::with);
        return List.copyOf(rows.values());
    }

    /** Every server source's id, a data pack's too. */
    public static List<Identifier> serverSources() {
        List<Identifier> all = new ArrayList<>(SERVER.keySet());
        for (Identifier id : FoliosSourceData.packSources()) if (!all.contains(id)) all.add(id);
        return all;
    }
}
