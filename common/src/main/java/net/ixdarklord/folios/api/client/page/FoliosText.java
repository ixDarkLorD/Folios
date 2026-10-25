package net.ixdarklord.folios.api.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.FoliosProgress;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.ixdarklord.folios.internal.client.page.Shapes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Text written for a book, set out in lines to a width. It is a small part of Markdown, so long text reads well in
 * a JSON file:
 * <ul>
 *     <li>A line is a line; an empty line starts a new paragraph.</li>
 *     <li>{@code # Heading} and {@code ## Smaller heading}; {@code ---} alone is a dividing rule.</li>
 *     <li>{@code - item} (or {@code * item}) is a bullet, {@code 1. item} a numbered one; two spaces before nest it.</li>
 *     <li>{@code > text} is set apart as a note.</li>
 *     <li>{@code **bold**}, {@code *italic*} (or {@code _italic_}), {@code __underlined__}, {@code ~~struck~~},
 *     {@code `code`}.</li>
 *     <li>{@code [label](target)} links to an entry ({@code entry:id}, {@code entry:id#marker} or the bare id), a
 *     category ({@code category:id}), an item's page ({@code item:minecraft:diamond}) or a web page ({@code https://...}).</li>
 *     <li>Tags in braces: {@code {#FF8800}coloured{/}} colours text; {@code {key:key.jump}} names the key bound to
 *     something; {@code {player}} is the reader's name; {@code {item:minecraft:diamond}} an item's name, linking to its
 *     page when the book has one; {@code {entry:tools/scribe#scribe}} a link with the entry's name;
 *     {@code {flag:mymod:hard_mode|on text|off text}} text by a flag; {@code {lang:key|first|second}} a translation,
 *     itself read as this markup, its {@code %s} filled from the rest. A tag that doesn't parse is shown as written.</li>
 *     <li>A backslash keeps the next character as it is: {@code \*}, {@code \{}.</li>
 * </ul>
 * Text that is a translation key as a whole is translated first. In JSON, a text field takes either a string of this
 * markup or {@code {"translate": "key", "with": [...], "fallback": "..."}} ({@link #CODEC}), so a book can keep all its
 * words in lang files.
 */
public final class FoliosText {
    /** The insertion text of a link's characters starts with this; the rest is where it goes. */
    public static final String LINK = "folios:";
    public static final int LINE_HEIGHT = 10;
    private static final int PARAGRAPH_GAP = 4;
    private static final int INDENT = 8;
    private static final int QUOTE_INDENT = 7;

    /** What a line is part of, which decides how it is drawn. */
    public enum Kind {
        PARAGRAPH,
        HEADING,
        SUBHEADING,
        BULLET,
        NUMBERED,
        QUOTE,
        RULE
    }

    /**
     * One line, at (x, y) from the text's top left.
     *
     * @param first   whether it starts its block (a bullet's mark is drawn on it)
     * @param marker  the bullet's mark or number, drawn at {@code x - INDENT}
     * @param shift   how far right of {@code x} its words start, for centred and right-aligned lines
     * @param stretch the room shared out between its words, for a justified line
     */
    public record Line(FormattedCharSequence text, int x, int y, Kind kind, boolean first, String marker, int shift, int stretch) {
        public int height() {
            return kind == Kind.RULE ? 7 : LINE_HEIGHT;
        }
    }

    private final List<Line> lines;
    private final int height;
    private final int width;

    private FoliosText(List<Line> lines, int height, int width) {
        this.lines = List.copyOf(lines);
        this.height = height;
        this.width = width;
    }

    public List<Line> lines() {
        return lines;
    }

    /** From the top of the first line to the bottom of the last. */
    public int height() {
        return height;
    }

    /** The width it was set out to. */
    public int width() {
        return width;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    // ---- Reading ----

    /**
     * Text as JSON writes it: a string of this markup, or {@code {"translate": "key", "with": ["first"], "fallback": "text"}}
     * for a translation (the arguments and fallback are markup too). Either way it is read as a string of markup.
     */
    public static final Codec<String> CODEC = ExtraCodecs.JSON.comapFlatMap(json -> {
        try {
            return DataResult.success(read(json));
        } catch (RuntimeException e) {
            return DataResult.error(e::getMessage);
        }
    }, JsonPrimitive::new);

    // Starts the part of a {lang:...} tag that is its fallback: only written by translation().
    private static final char FALLBACK = '\uE000';
    // How deep translations may use translations.
    private static final int DEPTH = 8;
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static @Nullable FoliosBook book;

    /** Text from JSON, as {@link #CODEC} reads it. */
    public static String read(JsonElement json) {
        if (json.isJsonPrimitive()) return json.getAsString();
        if (!json.isJsonObject() || !json.getAsJsonObject().has("translate")) {
            throw new IllegalArgumentException("Text is a string, or an object with \"translate\": " + json);
        }
        JsonObject object = json.getAsJsonObject();
        List<String> with = new ArrayList<>();
        if (object.has("with")) {
            for (JsonElement argument : object.getAsJsonArray("with")) with.add(read(argument));
        }
        return translation(object.get("translate").getAsString(), with, object.has("fallback") ? read(object.get("fallback")) : null);
    }

    /** Markup for a translation, {@code {lang:key|with...}}, with what to show if there is no such key (null to show the key). */
    public static String translation(String key, List<String> with, @Nullable String fallback) {
        StringBuilder tag = new StringBuilder("{lang:").append(key);
        for (String argument : with) tag.append('|').append(argument.replace("|", "\\|"));
        if (fallback != null) tag.append('|').append(FALLBACK).append(fallback.replace("|", "\\|"));
        return tag.append('}').toString();
    }

    /** Whether the language has text for a translation key. */
    public static boolean hasTranslation(String key) {
        return Language.getInstance().has(key);
    }

    /** Whether text comes to nothing once read (a translation with an empty fallback and no key, say). */
    public static boolean isBlank(String raw) {
        return raw.isEmpty() || resolve(raw).isBlank();
    }

    /**
     * Runs something with a book as the one text is read for: its {@code {entry:...}} tags take its entries' names, and
     * {@code {item:...}} links to its pages. A book sets itself while it lays out its pages and searches them.
     */
    public static <T> T in(FoliosBook book, java.util.function.Supplier<T> read) {
        FoliosBook outer = FoliosText.book;
        FoliosText.book = book;
        try {
            return read.get();
        } finally {
            FoliosText.book = outer;
        }
    }

    /** The text a raw string stands for: its translation if it is a translation key, with each {@code {lang:...}} put in its place. */
    public static String resolve(String raw) {
        Language language = Language.getInstance();
        String text = language.has(raw) ? language.getOrDefault(raw) : raw;
        return text.contains("{lang:") ? expand(text, 0) : text;
    }

    // Puts each {lang:...} in its place, its translation read the same way in turn.
    private static String expand(String text, int depth) {
        StringBuilder out = new StringBuilder(text.length());
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < length) {
                out.append(c).append(text.charAt(++i));
                continue;
            }
            int end = c == '{' && text.startsWith("{lang:", i) ? closing(text, i) : -1;
            if (end < 0) {
                out.append(c);
                continue;
            }
            List<String> parts = arguments(text.substring(i + 6, end));
            String key = parts.removeFirst().strip();
            String fallback = !parts.isEmpty() && !parts.getLast().isEmpty() && parts.getLast().charAt(0) == FALLBACK ? parts.removeLast().substring(1) : null;
            if (depth >= DEPTH) {
                logOnce("loop:" + key, "The translation {} uses translations more than {} deep (a loop?); it is shown as its key", key, DEPTH);
                out.append(key);
            } else {
                out.append(expand(format(translate(key, fallback), parts), depth + 1));
            }
            i = end;
        }
        return out.toString();
    }

    // A key's text: the language's, the fallback, or the key itself, logged once.
    private static String translate(String key, @Nullable String fallback) {
        Language language = Language.getInstance();
        if (language.has(key)) return language.getOrDefault(key);
        if (fallback != null) return fallback;
        logOnce("missing:" + key, "A book uses the translation {}, which no language file has", key);
        return key;
    }

    // Fills %s and %1$s as vanilla's translations do; %% is a percent sign.
    private static String format(String text, List<String> with) {
        if (text.indexOf('%') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        int next = 0;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c != '%' || i + 1 >= length) {
                out.append(c);
                continue;
            }
            if (text.charAt(i + 1) == '%') {
                out.append('%');
                i++;
            } else if (text.charAt(i + 1) == 's') {
                out.append(next < with.size() ? with.get(next) : "");
                next++;
                i++;
            } else {
                int dollar = text.indexOf("$s", i);
                String number = dollar > i + 1 ? text.substring(i + 1, dollar) : "";
                if (!number.isEmpty() && number.length() <= 2 && number.chars().allMatch(Character::isDigit)) {
                    int index = Integer.parseInt(number) - 1;
                    out.append(index >= 0 && index < with.size() ? with.get(index) : "");
                    i = dollar + 1;
                } else {
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    // Where the brace opening at `open` closes, braces inside counted and backslashed characters skipped; -1 for nowhere.
    private static int closing(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    // A tag's parts, split at each | outside braces; \| is a | in a part.
    private static List<String> arguments(String inside) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c == '\\' && i + 1 < inside.length()) {
                char next = inside.charAt(++i);
                if (next != '|') part.append(c);
                part.append(next);
            } else if (c == '|' && depth == 0) {
                parts.add(part.toString());
                part.setLength(0);
            } else {
                if (c == '{') depth++;
                if (c == '}') depth--;
                part.append(c);
            }
        }
        parts.add(part.toString());
        return parts;
    }

    private static void logOnce(String what, String message, Object... arguments) {
        if (LOGGED.add(what)) Folios.LOGGER.warn(message, arguments);
    }

    /** One line of marked-up text (a title or a label) as a component; block marks are kept as written. */
    public static Component inline(String raw) {
        return new Inline(resolve(raw), false).parse();
    }

    /** Text without its marks, for searching. */
    public static String plain(String raw) {
        return inline(raw).getString();
    }

    /** Reads marked-up text and sets it out in lines no wider than {@code width}. */
    public static FoliosText of(Font font, String raw, int width) {
        return of(font, raw, width, null);
    }

    /**
     * As {@link #of(Font, String, int)}, its lines aligned: null or {@link FoliosAlign#LEFT} as written. A paragraph
     * written {@code ->like this<-} is centred, and {@code ->like this} right-aligned, whatever the rest is.
     */
    public static FoliosText of(Font font, String raw, int width, @Nullable FoliosAlign align) {
        List<Line> lines = new ArrayList<>();
        int y = 0;
        boolean gap = false;
        int number = 0;
        String[] source = resolve(raw).replace("\r", "").split("\n", -1);
        for (int index = 0; index < source.length; index++) {
            String row = source[index];
            String trimmed = row.strip();
            if (trimmed.isEmpty()) {
                gap = !lines.isEmpty();
                number = 0;
                continue;
            }
            int leading = row.length() - row.stripLeading().length();
            int level = Math.min(3, leading / 2);
            Kind kind = Kind.PARAGRAPH;
            String body = trimmed;
            String marker = "";
            if (trimmed.equals("---") || trimmed.equals("***")) {
                kind = Kind.RULE;
            } else if (trimmed.startsWith("## ")) {
                kind = Kind.SUBHEADING;
                body = trimmed.substring(3);
            } else if (trimmed.startsWith("# ")) {
                kind = Kind.HEADING;
                body = trimmed.substring(2);
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") && !trimmed.startsWith("**")) {
                kind = Kind.BULLET;
                body = trimmed.substring(2);
                marker = level % 2 == 0 ? "•" : "◦";
            } else if (trimmed.startsWith("> ") || trimmed.equals(">")) {
                kind = Kind.QUOTE;
                body = trimmed.length() > 2 ? trimmed.substring(2) : "";
            } else {
                int dot = trimmed.indexOf(". ");
                if (dot > 0 && dot <= 3 && trimmed.substring(0, dot).chars().allMatch(Character::isDigit)) {
                    kind = Kind.NUMBERED;
                    body = trimmed.substring(dot + 2);
                    number++;
                    marker = number + ".";
                }
            }
            if (kind != Kind.NUMBERED && kind != Kind.BULLET) number = kind == Kind.PARAGRAPH && leading > 0 ? number : 0;
            // A paragraph or heading of its own alignment: ->centred<- or ->right.
            FoliosAlign lineAlign = align;
            if ((kind == Kind.PARAGRAPH || kind == Kind.HEADING || kind == Kind.SUBHEADING) && body.startsWith("->")) {
                boolean centred = body.endsWith("<-") && body.length() >= 4;
                body = (centred ? body.substring(2, body.length() - 2) : body.substring(2)).strip();
                lineAlign = centred ? FoliosAlign.CENTER : FoliosAlign.RIGHT;
            }
            // Lists and notes keep their marks at the left: only justified, never centred or moved right.
            if (lineAlign != FoliosAlign.JUSTIFY && kind != Kind.PARAGRAPH && kind != Kind.HEADING && kind != Kind.SUBHEADING) lineAlign = null;
            // Headings and rules stand apart; a gap from an empty line comes first.
            boolean spaced = !lines.isEmpty() && (gap || kind == Kind.HEADING || kind == Kind.SUBHEADING || kind == Kind.RULE);
            if (spaced) y += kind == Kind.HEADING ? PARAGRAPH_GAP + 2 : PARAGRAPH_GAP;
            gap = false;
            if (kind == Kind.RULE) {
                lines.add(new Line(FormattedCharSequence.EMPTY, 0, y, kind, true, "", 0, 0));
                y += 7;
                gap = false;
                continue;
            }
            int x = switch (kind) {
                case BULLET, NUMBERED -> INDENT * (level + 1) + (kind == Kind.NUMBERED ? 3 : 0);
                case QUOTE -> QUOTE_INDENT;
                default -> 0;
            };
            Component text = new Inline(body, true).parse();
            if (kind == Kind.HEADING || kind == Kind.SUBHEADING) text = text.copy().withStyle(ChatFormatting.BOLD);
            int room = Math.max(16, width - x - (kind == Kind.QUOTE ? 3 : 0));
            // Text starting with an item's slot ("- {item:...}") runs on beside it: its later lines start under the
            // item's name, not under the slot.
            int hang = hangingIndent(text);
            List<FormattedCharSequence> split = hang > 0 ? besideSlot(font, text, room, hang) : font.split(text, room);
            if (split.isEmpty()) split = List.of(FormattedCharSequence.EMPTY);
            for (int part = 0; part < split.size(); part++) {
                FormattedCharSequence line = split.get(part);
                int lineRoom = part > 0 ? room - hang : room;
                int spare = Math.max(0, lineRoom - font.width(line));
                int shift = 0;
                int stretch = 0;
                if (lineAlign == FoliosAlign.JUSTIFY) {
                    // Every line of a paragraph but its last fills the width.
                    if (part + 1 < split.size()) stretch = spare;
                } else if (lineAlign != null) {
                    shift = lineAlign.offset(lineRoom, lineRoom - spare);
                }
                // A line with an item's slot keeps room above and below for the slot standing past its text.
                int pad = slotRoom(line);
                y += pad;
                lines.add(new Line(line, part > 0 ? x + hang : x, y, kind, part == 0, part == 0 ? marker : "", shift, stretch));
                y += LINE_HEIGHT + pad;
            }
            // A heading keeps a little room under its rule.
            if (kind == Kind.HEADING) y += 5;
        }
        int height = lines.isEmpty() ? 0 : y - 1;
        return new FoliosText(lines, height, width);
    }

    /**
     * Splits the text into pieces that fit: the first {@code firstHeight} tall, the rest {@code nextHeight}. A heading
     * is never left alone at the foot of a piece. Each piece's lines start again at y 0.
     */
    public List<FoliosText> split(int firstHeight, int nextHeight) {
        List<FoliosText> pieces = new ArrayList<>();
        List<Line> piece = new ArrayList<>();
        int room = firstHeight;
        int top = -1;
        // No room for even a line on the first piece: it stays empty, and the text starts on the next.
        if (!lines.isEmpty() && firstHeight < lines.getFirst().height()) {
            pieces.add(rebase(List.of(), width));
            room = nextHeight;
        }
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            int start = top < 0 ? line.y : top;
            int bottom = line.y + line.height() - start;
            boolean heading = line.kind == Kind.HEADING || line.kind == Kind.SUBHEADING;
            boolean lonely = heading && index + 1 < lines.size() && lines.get(index + 1).y + LINE_HEIGHT - start > room;
            if (!piece.isEmpty() && (bottom > room || lonely)) {
                pieces.add(rebase(piece, width));
                piece = new ArrayList<>();
                room = nextHeight;
                top = -1;
            }
            if (top < 0) top = line.y;
            // A piece starting with a rule has no use for it.
            if (piece.isEmpty() && line.kind == Kind.RULE && !pieces.isEmpty()) {
                top = -1;
                continue;
            }
            piece.add(line);
        }
        if (!piece.isEmpty() || pieces.isEmpty()) pieces.add(rebase(piece, width));
        return pieces;
    }

    private static FoliosText rebase(List<Line> lines, int width) {
        if (lines.isEmpty()) return new FoliosText(List.of(), 0, width);
        int top = lines.getFirst().y;
        List<Line> moved = new ArrayList<>(lines.size());
        for (Line line : lines) moved.add(new Line(line.text, line.x, line.y - top, line.kind, line.first, line.marker, line.shift, line.stretch));
        Line last = moved.getLast();
        return new FoliosText(moved, last.y + last.height() - 1, width);
    }

    // ---- Drawing ----

    /** Draws the text with its top left at (x, y), its links pressable. */
    public void draw(FoliosPainter painter, int x, int y) {
        FoliosPalette palette = painter.palette();
        Font font = painter.font();
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            int lineX = x + line.x + line.shift;
            int lineY = y + line.y;
            switch (line.kind) {
                case RULE -> {
                    painter.fill(x, lineY + 3, x + 24, lineY + 4, palette.accent());
                    painter.fill(x + 26, lineY + 3, x + width, lineY + 4, palette.line());
                    continue;
                }
                case QUOTE -> {
                    // A note's lines join into one box; a gap above a line starts a new one.
                    boolean top = index == 0 || lines.get(index - 1).kind != Kind.QUOTE || lines.get(index - 1).y + LINE_HEIGHT < line.y;
                    painter.fill(x, lineY - (top ? 2 : 0), x + width, lineY + LINE_HEIGHT - 1, FoliosPalette.alpha(palette.accent(), 0x1C));
                    painter.fill(x, lineY - (top ? 2 : 0), x + 2, lineY + LINE_HEIGHT - 1, palette.accent());
                }
                case BULLET, NUMBERED -> {
                    if (line.first && !line.marker.isEmpty()) {
                        int markerX = lineX - (line.kind == Kind.NUMBERED ? font.width(line.marker) + 2 : 6);
                        painter.text(Component.literal(line.marker), markerX, lineY, palette.accent());
                    }
                }
                default -> {}
            }
            int color = switch (line.kind) {
                case HEADING, SUBHEADING -> palette.heading();
                default -> palette.text();
            };
            FormattedCharSequence text = line.text;
            if (slotRow(painter, text, x + line.x, lineY, width - line.x)) continue;
            if (line.stretch > 0 && justify(painter, text, lineX, lineY, line.stretch, color)) continue;
            slots(painter, text, lineX, lineY);
            // Each link is pressed as a whole: its run of characters on this line.
            String hovered = null;
            for (LinkRun run : linkRuns(font, text, lineX)) {
                if (painter.link(run.x0, lineY - 1, run.x1 - run.x0, LINE_HEIGHT, run.target)) hovered = run.target;
            }
            painter.text(colorLinks(text, palette, hovered), lineX, lineY, color);
            if (line.kind == Kind.HEADING && (index + 1 >= lines.size() || lines.get(index + 1).kind != Kind.HEADING)) {
                // Its divider, as a page title's without an icon: a plain rule across the text's width.
                painter.fill(x, lineY + 10, x + width, lineY + 11, palette.line());
            }
        }
    }

    // A justified line: its words drawn one by one, the spare room shared between the gaps (the first gaps take a pixel
    // more when it doesn't share evenly). Its links are pressed word by word. False for a line of one word.
    private static boolean justify(FoliosPainter painter, FormattedCharSequence text, int x, int y, int stretch, int color) {
        List<List<int[]>> words = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        List<int[]> word = new ArrayList<>();
        text.accept((index, style, codepoint) -> {
            styles.add(style);
            if (codepoint == ' ') {
                if (!word.isEmpty()) words.add(new ArrayList<>(word));
                word.clear();
            } else {
                word.add(new int[]{codepoint, styles.size() - 1});
            }
            return true;
        });
        if (!word.isEmpty()) words.add(new ArrayList<>(word));
        if (words.size() < 2) return false;
        Font font = painter.font();
        FoliosPalette palette = painter.palette();
        int gaps = words.size() - 1;
        // Gaps stretched past two spaces each read as holes: such a line is left as written.
        if (stretch > gaps * 2 * painter.font().width(" ")) return false;
        List<FormattedCharSequence> drawn = new ArrayList<>(words.size());
        for (List<int[]> letters : words) {
            drawn.add(sink -> {
                for (int[] letter : letters) {
                    if (!sink.accept(0, styles.get(letter[1]), letter[0])) return false;
                }
                return true;
            });
        }
        int space = font.width(" ");
        int[] at = new int[words.size()];
        int wordX = x;
        String hovered = null;
        for (int index = 0; index < drawn.size(); index++) {
            at[index] = wordX;
            for (LinkRun run : linkRuns(font, drawn.get(index), wordX)) {
                if (painter.link(run.x0, y - 1, run.x1 - run.x0, LINE_HEIGHT, run.target)) hovered = run.target;
            }
            wordX += font.width(drawn.get(index)) + space + stretch / gaps + (index < stretch % gaps ? 1 : 0);
        }
        for (int index = 0; index < drawn.size(); index++) {
            slots(painter, drawn.get(index), at[index], y);
            painter.text(colorLinks(drawn.get(index), palette, hovered), at[index], y, color);
        }
        return true;
    }

    // An {item:...} tag's slot: a blank character of the slot font holds its room in the line, and the item is drawn
    // there in a slot frame, with its tooltip. The character is SLOT plus the slot's size, which is 8 to 18 pixels.
    private static final int SLOT = 0xE1F0;
    private static final int SLOT_SIZE = 14;
    private static final int SLOT_MIN = 8;
    private static final int SLOT_MAX = 18;
    private static final FontDescription SLOT_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("folios", "slot"));
    private static final String SLOT_ITEM = "folios$item:";

    // How far a text's later lines start in, under the words after its first slot: 0 unless it starts with a slot
    // and has more than slots after it.
    private static int hangingIndent(Component text) {
        String plain = text.getString();
        if (plain.isEmpty()) return 0;
        int size = plain.codePointAt(0) - SLOT;
        if (size < SLOT_MIN || size > SLOT_MAX) return 0;
        boolean words = plain.codePoints().skip(1).anyMatch(codepoint -> codepoint != ' ' && (codepoint - SLOT < SLOT_MIN || codepoint - SLOT > SLOT_MAX));
        return words ? size + 2 : 0;
    }

    // A text starting with a slot, set out with the slot on its first line and the rest in the room beside it.
    private static List<FormattedCharSequence> besideSlot(Font font, Component text, int room, int hang) {
        Style[] slotStyle = {Style.EMPTY};
        String[] slot = {""};
        List<FormattedText> rest = new ArrayList<>();
        text.visit((style, string) -> {
            if (slot[0].isEmpty() && !string.isEmpty()) {
                int length = Character.charCount(string.codePointAt(0));
                slot[0] = string.substring(0, length);
                slotStyle[0] = style;
                string = string.substring(length);
            }
            if (!string.isEmpty()) rest.add(FormattedText.of(string, style));
            return Optional.empty();
        }, Style.EMPTY);
        List<FormattedText> parts = font.getSplitter().splitLines(FormattedText.composite(rest), Math.max(16, room - hang), Style.EMPTY);
        List<FormattedCharSequence> lines = new ArrayList<>(parts.size());
        for (int part = 0; part < parts.size(); part++) {
            FormattedText line = part == 0 ? FormattedText.composite(FormattedText.of(slot[0], slotStyle[0]), parts.get(0)) : parts.get(part);
            lines.add(Language.getInstance().getVisualOrder(line));
        }
        if (lines.isEmpty()) lines.add(Language.getInstance().getVisualOrder(FormattedText.of(slot[0], slotStyle[0])));
        return lines;
    }

    // How far the largest slot on a line stands past it, above and below.
    private static int slotRoom(FormattedCharSequence text) {
        int[] most = {0};
        text.accept((position, style, codepoint) -> {
            int size = codepoint - SLOT;
            if (size >= SLOT_MIN && size <= SLOT_MAX) most[0] = Math.max(most[0], (size - 9) / 2);
            return true;
        });
        return most[0];
    }

    private static void slots(FoliosPainter painter, FormattedCharSequence text, int x, int y) {
        Font font = painter.font();
        float[] at = {x};
        text.accept((position, style, codepoint) -> {
            int size = codepoint - SLOT;
            if (size >= SLOT_MIN && size <= SLOT_MAX) slot(painter, style.getInsertion(), size, Math.round(at[0]), y);
            at[0] += font.width(FormattedCharSequence.codepoint(codepoint, style));
            return true;
        });
    }

    // A line of slots alone, a row of a grid ({slot:...} in a live list's rows, say): its slots spread evenly across the
    // width, every row as far apart, so the grid fills the page and its columns line up. False for any other line.
    private static boolean slotRow(FoliosPainter painter, FormattedCharSequence text, int x, int y, int room) {
        List<Integer> sizes = new ArrayList<>();
        List<String> items = new ArrayList<>();
        boolean slotsOnly = text.accept((position, style, codepoint) -> {
            int size = codepoint - SLOT;
            if (size < SLOT_MIN || size > SLOT_MAX) return false;
            sizes.add(size);
            items.add(style.getInsertion());
            return true;
        });
        if (!slotsOnly || sizes.isEmpty()) return false;
        int size = 0;
        for (int each : sizes) size = Math.max(size, each);
        // As many as a line holds, each with the 2 pixels after it, as the lines were broken.
        int columns = Math.max(1, room / (size + 2));
        float pitch = columns > 1 ? (room - size) / (float) (columns - 1) : size + 2;
        for (int index = 0; index < sizes.size(); index++) {
            slot(painter, items.get(index), sizes.get(index), x + Math.round(index * pitch) + (size - sizes.get(index)) / 2, y);
        }
        return true;
    }

    // One slot, `size` pixels square with its left at x, centred on the middle of the line of text at y.
    private static void slot(FoliosPainter painter, @Nullable String insertion, int size, int x, int y) {
        if (insertion == null || !insertion.startsWith(SLOT_ITEM)) return;
        Identifier id = Identifier.tryParse(insertion.substring(SLOT_ITEM.length()));
        Item item = id != null ? BuiltInRegistries.ITEM.getOptional(id).orElse(null) : null;
        if (item == null) return;
        int top = y + 4 - size / 2;
        Shapes.slot(painter, x, top, size);
        painter.push();
        painter.translate(x + 1, top + 1);
        painter.scale((size - 2) / 16F);
        painter.item(new ItemStack(item), 0, 0);
        painter.pop();
    }

    /** The widest line. */
    public int width(Font font) {
        int width = 0;
        for (Line line : lines) width = Math.max(width, line.x + font.width(line.text));
        return width;
    }

    private record LinkRun(int x0, int x1, String target) {}

    // The links on a line: where each begins and ends, in page x, and where it goes.
    private static List<LinkRun> linkRuns(Font font, FormattedCharSequence text, int lineX) {
        List<LinkRun> runs = new ArrayList<>();
        float[] at = {lineX};
        text.accept((position, style, codepoint) -> {
            float width = font.width(FormattedCharSequence.codepoint(codepoint, style));
            String insertion = style.getInsertion();
            if (insertion != null && insertion.startsWith(LINK)) {
                String target = insertion.substring(LINK.length());
                int start = Math.round(at[0]);
                int end = Math.round(at[0] + width);
                if (!runs.isEmpty() && runs.getLast().target.equals(target) && runs.getLast().x1 == start) {
                    runs.set(runs.size() - 1, new LinkRun(runs.getLast().x0, end, target));
                } else {
                    runs.add(new LinkRun(start, end, target));
                }
            }
            at[0] += width;
            return true;
        });
        return runs;
    }

    // Links take the palette's link colour, the hovered one underlined; text coloured by its writer keeps its colour.
    private static FormattedCharSequence colorLinks(FormattedCharSequence text, FoliosPalette palette, @Nullable String hovered) {
        return output -> text.accept((position, style, codepoint) -> {
            String insertion = style.getInsertion();
            if (insertion != null && insertion.startsWith(LINK)) {
                boolean over = hovered != null && insertion.length() == LINK.length() + hovered.length() && insertion.endsWith(hovered);
                style = style.withColor(over ? FoliosPalette.mix(palette.link(), palette.heading(), 0.35F) : palette.link()).withUnderlined(over ? Boolean.TRUE : style.isUnderlined());
            } else if (style.getColor() == null && isCode(style)) {
                style = style.withColor(palette.muted());
            }
            return output.accept(position, style, codepoint);
        });
    }

    private static final String CODE = "folios$code";

    private static boolean isCode(Style style) {
        return CODE.equals(style.getInsertion());
    }

    // ---- Inline marks ----

    private static final class Inline {
        private final String source;
        private final MutableComponent out = Component.empty();
        private final StringBuilder pending = new StringBuilder();
        private Style style = Style.EMPTY;
        // Whether {item:...} tags take a slot with the item before their name: in page text, not in titles and labels.
        private final boolean slots;

        Inline(String source, boolean slots) {
            this.source = source;
            this.slots = slots;
        }

        Component parse() {
            int length = source.length();
            for (int i = 0; i < length; i++) {
                char c = source.charAt(i);
                if (c == '\\' && i + 1 < length) {
                    pending.append(source.charAt(++i));
                } else if (source.startsWith("**", i)) {
                    toggle(Mark.BOLD);
                    i++;
                } else if (source.startsWith("__", i)) {
                    toggle(Mark.UNDERLINE);
                    i++;
                } else if (source.startsWith("~~", i)) {
                    toggle(Mark.STRIKE);
                    i++;
                } else if (c == '*' || c == '_' && wordEdge(i)) {
                    toggle(Mark.ITALIC);
                } else if (c == '`') {
                    int end = source.indexOf('`', i + 1);
                    if (end < 0) {
                        pending.append(c);
                        continue;
                    }
                    flush();
                    out.append(Component.literal(source.substring(i + 1, end)).withStyle(style.withInsertion(CODE)));
                    i = end;
                } else if (c == '[') {
                    int end = link(i);
                    if (end > 0) i = end;
                    else pending.append(c);
                } else if (c == '{') {
                    int end = brace(i);
                    if (end > 0) i = end;
                    else pending.append(c);
                } else {
                    pending.append(c);
                }
            }
            flush();
            return out;
        }

        // An underscore inside a word (snake_case) is just an underscore.
        private boolean wordEdge(int i) {
            boolean before = i > 0 && Character.isLetterOrDigit(source.charAt(i - 1));
            boolean after = i + 1 < source.length() && Character.isLetterOrDigit(source.charAt(i + 1));
            return !(before && after);
        }

        private enum Mark { BOLD, ITALIC, UNDERLINE, STRIKE }

        private void toggle(Mark mark) {
            flush();
            style = switch (mark) {
                case BOLD -> style.withBold(!style.isBold() ? Boolean.TRUE : null);
                case ITALIC -> style.withItalic(!style.isItalic() ? Boolean.TRUE : null);
                case UNDERLINE -> style.withUnderlined(!style.isUnderlined() ? Boolean.TRUE : null);
                case STRIKE -> style.withStrikethrough(!style.isStrikethrough() ? Boolean.TRUE : null);
            };
        }

        // [label](target): returns the index of the closing parenthesis, or -1 when it isn't a link.
        private int link(int open) {
            int close = source.indexOf("](", open);
            if (close < 0) return -1;
            int end = source.indexOf(')', close + 2);
            if (end < 0) return -1;
            String label = source.substring(open + 1, close);
            String target = source.substring(close + 2, end).strip();
            if (target.isEmpty()) return -1;
            flush();
            Style linkStyle = style.withInsertion(LINK + target);
            if (target.startsWith("http://") || target.startsWith("https://")) {
                linkStyle = linkStyle.withHoverEvent(new HoverEvent.ShowText(Component.literal(target)));
            }
            Component inner = new Inline(label, slots).parse();
            out.append(Component.empty().withStyle(linkStyle).append(inner));
            return end;
        }

        // {#RRGGBB} ... {/}, {key:name}, {player}, {item:id}, {entry:id}, {flag:id|on|off}: returns the index of the closing
        // brace, or -1 when it isn't one (it is then shown as written).
        private int brace(int open) {
            int end = closing(source, open);
            if (end < 0) return -1;
            String inside = source.substring(open + 1, end);
            if (inside.equals("/")) {
                flush();
                style = style.withColor((TextColor) null);
                return end;
            }
            if (inside.startsWith("#") && (inside.length() == 7)) {
                try {
                    int color = Integer.parseInt(inside.substring(1), 16);
                    flush();
                    style = style.withColor(color);
                    return end;
                } catch (NumberFormatException e) {
                    return unknown(inside);
                }
            }
            if (inside.startsWith("key:")) {
                flush();
                out.append(Component.keybind(inside.substring(4)).withStyle(style.withBold(true)));
                return end;
            }
            if (inside.equals("player")) {
                flush();
                Minecraft minecraft = Minecraft.getInstance();
                out.append(Component.literal(minecraft.player != null ? minecraft.player.getGameProfile().name() : minecraft.getUser().getName()).withStyle(style));
                return end;
            }
            if (inside.startsWith("item:") || inside.startsWith("slot:")) {
                boolean bare = inside.startsWith("slot:");
                // {item:id|size}: the slot's size in pixels, 8 to 18.
                List<String> parts = arguments(inside.substring(5));
                int size = SLOT_SIZE;
                if (parts.size() > 2) return unknown(inside);
                if (parts.size() == 2) {
                    try {
                        size = Integer.parseInt(parts.get(1).strip());
                    } catch (NumberFormatException e) {
                        return unknown(inside);
                    }
                    if (size < SLOT_MIN || size > SLOT_MAX) return unknown(inside);
                }
                Identifier id = Identifier.tryParse(parts.get(0).strip());
                Item item = id != null ? BuiltInRegistries.ITEM.getOptional(id).orElse(null) : null;
                if (item == null) return unknown(inside);
                flush();
                ItemStack stack = new ItemStack(item);
                // A link only when the book has a page about it.
                Style named = book != null && book.about(stack) != null ? style.withInsertion(LINK + "item:" + id) : style;
                // Its slot, plain: no underline or strike through it, and not part of the link (the item's own
                // tooltip says where it can be read about).
                if (slots) out.append(Component.literal(Character.toString(SLOT + size)).withStyle(Style.EMPTY.withFont(SLOT_FONT).withInsertion(SLOT_ITEM + id)));
                // {slot:...} is the slot alone, to set items in a row or a grid; where there are no slots, its name.
                if (!bare || !slots) out.append(stack.getHoverName().copy().withStyle(named));
                return end;
            }
            if (inside.startsWith("entity:")) {
                Identifier id = Identifier.tryParse(inside.substring(7).strip());
                net.minecraft.world.entity.EntityType<?> type = id != null ? BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null) : null;
                if (type == null) return unknown(inside);
                flush();
                // Its name, a link showing it in a popup window.
                out.append(Component.empty().withStyle(style.withInsertion(LINK + "entity:" + id)).append(type.getDescription()));
                return end;
            }
            if (inside.startsWith("entry:")) {
                String target = inside.substring(6).strip();
                if (target.isEmpty()) return unknown(inside);
                String id = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
                FoliosEntry entry = book != null ? book.entry(id) : null;
                flush();
                Component name = entry != null ? entry.title() : Component.literal(id.substring(id.lastIndexOf('/') + 1).replace('_', ' '));
                out.append(Component.empty().withStyle(style.withInsertion(LINK + "entry:" + target)).append(name));
                return end;
            }
            if (inside.startsWith("flag:")) {
                List<String> parts = arguments(inside.substring(5));
                if (parts.size() > 3 || parts.getFirst().isBlank()) return unknown(inside);
                String chosen = FoliosProgress.flag(parts.getFirst().strip()) ? (parts.size() > 1 ? parts.get(1) : "") : (parts.size() > 2 ? parts.get(2) : "");
                flush();
                if (!chosen.isEmpty()) out.append(Component.empty().withStyle(style).append(new Inline(chosen, slots).parse()));
                return end;
            }
            return unknown(inside);
        }

        // A tag Folios doesn't know, or one it can't read: shown as written, and logged the first time.
        private int unknown(String inside) {
            logOnce("tag:" + inside, "A book has the tag {{}}, which isn't one Folios knows or can read; it is shown as written", inside);
            return -1;
        }

        private void flush() {
            if (pending.isEmpty()) return;
            out.append(Component.literal(pending.toString()).withStyle(style));
            pending.setLength(0);
        }
    }
}
