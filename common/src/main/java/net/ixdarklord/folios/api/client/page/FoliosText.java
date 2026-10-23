package net.ixdarklord.folios.api.client.page;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
 *     <li>{@code [label](target)} links to an entry ({@code entry:id}, {@code entry:id#anchor} or the bare id), a
 *     category ({@code category:id}) or a web page ({@code https://...}).</li>
 *     <li>{@code {#FF8800}coloured{/}} colours text; {@code {key:key.jump}} names the key bound to something.</li>
 *     <li>A backslash keeps the next character as it is: {@code \*}.</li>
 * </ul>
 * Any text that is a translation key is translated first, so a book can keep its words in lang files.
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
     * @param first  whether it starts its block (a bullet's mark is drawn on it)
     * @param marker the bullet's mark or number, drawn at {@code x - INDENT}
     */
    public record Line(FormattedCharSequence text, int x, int y, Kind kind, boolean first, String marker) {
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

    /** The text a raw string stands for: its translation if it is a translation key, otherwise itself. */
    public static String resolve(String raw) {
        Language language = Language.getInstance();
        return language.has(raw) ? language.getOrDefault(raw) : raw;
    }

    /** One line of marked-up text (a title or a label) as a component; block marks are kept as written. */
    public static Component inline(String raw) {
        return new Inline(resolve(raw)).parse();
    }

    /** Text without its marks, for searching. */
    public static String plain(String raw) {
        return inline(raw).getString();
    }

    /** Reads marked-up text and sets it out in lines no wider than {@code width}. */
    public static FoliosText of(Font font, String raw, int width) {
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
            // Headings and rules stand apart; a gap from an empty line comes first.
            boolean spaced = !lines.isEmpty() && (gap || kind == Kind.HEADING || kind == Kind.SUBHEADING || kind == Kind.RULE);
            if (spaced) y += kind == Kind.HEADING ? PARAGRAPH_GAP + 2 : PARAGRAPH_GAP;
            gap = false;
            if (kind == Kind.RULE) {
                lines.add(new Line(FormattedCharSequence.EMPTY, 0, y, kind, true, ""));
                y += 7;
                gap = false;
                continue;
            }
            int x = switch (kind) {
                case BULLET, NUMBERED -> INDENT * (level + 1) + (kind == Kind.NUMBERED ? 3 : 0);
                case QUOTE -> QUOTE_INDENT;
                default -> 0;
            };
            Component text = new Inline(body).parse();
            if (kind == Kind.HEADING || kind == Kind.SUBHEADING) text = text.copy().withStyle(ChatFormatting.BOLD);
            List<FormattedCharSequence> split = font.split(text, Math.max(16, width - x - (kind == Kind.QUOTE ? 3 : 0)));
            if (split.isEmpty()) split = List.of(FormattedCharSequence.EMPTY);
            for (int part = 0; part < split.size(); part++) {
                lines.add(new Line(split.get(part), x, y, kind, part == 0, part == 0 ? marker : ""));
                y += LINE_HEIGHT;
            }
            // A heading keeps a little room under it.
            if (kind == Kind.HEADING) y += 2;
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
        for (Line line : lines) moved.add(new Line(line.text, line.x, line.y - top, line.kind, line.first, line.marker));
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
            int lineX = x + line.x;
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
            // Each link is pressed as a whole: its run of characters on this line.
            String hovered = null;
            for (LinkRun run : linkRuns(font, text, lineX)) {
                if (painter.link(run.x0, lineY - 1, run.x1 - run.x0, LINE_HEIGHT, run.target)) hovered = run.target;
            }
            painter.text(colorLinks(text, palette, hovered), lineX, lineY, color);
            if (line.kind == Kind.HEADING && (index + 1 >= lines.size() || lines.get(index + 1).kind != Kind.HEADING)) {
                painter.fill(x, lineY + 10, x + 14, lineY + 11, palette.accent());
            }
        }
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

        Inline(String source) {
            this.source = source;
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
            Component inner = new Inline(label).parse();
            out.append(Component.empty().withStyle(linkStyle).append(inner));
            return end;
        }

        // {#RRGGBB} ... {/}, {key:name}: returns the index of the closing brace, or -1 when it isn't one.
        private int brace(int open) {
            int end = source.indexOf('}', open);
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
                    return -1;
                }
            }
            if (inside.startsWith("key:")) {
                flush();
                out.append(Component.keybind(inside.substring(4)).withStyle(style.withBold(true)));
                return end;
            }
            return -1;
        }

        private void flush() {
            if (pending.isEmpty()) return;
            out.append(Component.literal(pending.toString()).withStyle(style));
            pending.setLength(0);
        }
    }
}
