package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonObject;
import net.ixdarklord.folios.api.FoliosSources;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An element's {@code "observe"}: text written from a live list ({@link FoliosSources}), a line of markup for each of its
 * rows, put after the element's own text and written again whenever the list changes.
 * <pre>{@code
 * "observe": {
 *   "source": "whycantisleep:sleep_modifiers", "of": "...",
 *   "row": "- **{$name}** ({$points}): {$description}",
 *   "sort": "name", "where": {"field": "points", "below": 0}, "limit": 10,
 *   "header": "...", "footer": "...", "separator": "\n", "empty": "*None yet.*"
 * }
 * }</pre>
 * {@code "of"} narrows a source Folios gives itself ({@code folios:items} of a tag, say: see FoliosBuiltinSources).
 * {@code {$field}} is a row's field (its {@code id} too); a field holding a translation key is translated. {@code sort}
 * names a field ({@code "-name"} for the other way; numbers sort as numbers); {@code where} keeps rows whose field
 * {@code is}, is {@code not}, is {@code above} or {@code below} a value.
 */
public record Observed(ResourceLocation source, @Nullable String of, String row, String header, String footer, String separator,
                       @Nullable String sort, @Nullable JsonObject where, int limit, String empty) {
    private static final Pattern FIELD = Pattern.compile("\\{\\$([a-zA-Z0-9_]+)}");
    private static final Set<ResourceLocation> MISSING = ConcurrentHashMap.newKeySet();

    /** What a page's or an element's JSON observes; null for one that observes nothing. */
    public static @Nullable Observed read(JsonObject json) {
        if (!json.has("observe")) return null;
        JsonObject observe = json.getAsJsonObject("observe");
        return new Observed(ResourceLocation.parse(observe.get("source").getAsString()), observe.has("of") ? observe.get("of").getAsString() : null,
                text(observe, "row", "- {$id}"), text(observe, "header", ""),
                text(observe, "footer", ""), observe.has("separator") ? observe.get("separator").getAsString() : "\n",
                observe.has("sort") ? observe.get("sort").getAsString() : null, observe.has("where") ? observe.getAsJsonObject("where") : null,
                observe.has("limit") ? Math.max(1, observe.get("limit").getAsInt()) : Integer.MAX_VALUE, text(observe, "empty", ""));
    }

    private static String text(JsonObject json, String key, String fallback) {
        return json.has(key) ? FoliosText.read(json.get(key)) : fallback;
    }

    /** The text as the list is now. */
    public String text() {
        if (!FoliosSources.exists(source) && MISSING.add(source)) {
            Folios.LOGGER.warn("A book observes the source {}, which nothing has given (yet)", source);
        }
        List<FoliosSources.Row> rows = new ArrayList<>();
        for (FoliosSources.Row one : FoliosSources.rows(source, of)) if (this.keeps(one)) rows.add(one);
        if (sort != null) {
            boolean down = sort.startsWith("-");
            String field = down ? sort.substring(1) : sort;
            Comparator<FoliosSources.Row> order = (a, b) -> compare(a.field(field), b.field(field));
            rows.sort(down ? order.reversed() : order);
        }
        if (rows.size() > limit) rows = rows.subList(0, limit);
        if (rows.isEmpty()) return empty;
        StringBuilder out = new StringBuilder();
        if (!header.isEmpty()) out.append(header).append('\n');
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0) out.append(separator);
            out.append(this.fill(rows.get(index)));
        }
        if (!footer.isEmpty()) out.append('\n').append(footer);
        return out.toString();
    }

    // The row's template, each {$field} its value (a translation key's text through {lang:...}).
    private String fill(FoliosSources.Row one) {
        Matcher matcher = FIELD.matcher(row);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = one.field(matcher.group(1));
            if (FoliosText.hasTranslation(value)) value = "{lang:" + value + "}";
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private boolean keeps(FoliosSources.Row one) {
        if (where == null || !where.has("field")) return true;
        String value = one.field(where.get("field").getAsString());
        if (where.has("is")) return value.equals(where.get("is").getAsString());
        if (where.has("not")) return !value.equals(where.get("not").getAsString());
        if (where.has("above")) return compare(value, where.get("above").getAsString()) > 0;
        if (where.has("below")) return compare(value, where.get("below").getAsString()) < 0;
        return true;
    }

    // Numbers as numbers, the rest as text.
    private static int compare(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }
}
