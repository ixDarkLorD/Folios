package net.ixdarklord.folios.internal.client.export;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class HtmlText {
    interface Links {
        String entry(String id);

        String category(String id);

        String itemIcon(Identifier item);

        @Nullable String itemPage(Identifier item);
    }

    private final Links links;

    HtmlText(Links links) {
        this.links = links;
    }

    String blocks(String raw) {
        StringBuilder out = new StringBuilder();
        List<String> lines = List.of(raw.replace("\r", "").split("\n", -1));
        List<String> paragraph = new ArrayList<>();
        String list = null;
        for (String line : lines) {
            String trimmed = line.strip();
            boolean bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ") && !trimmed.startsWith("**");
            boolean numbered = trimmed.matches("\\d+\\. .*");
            if (trimmed.isEmpty()) {
                this.flush(out, paragraph);
                list = this.closeList(out, list);
                continue;
            }
            if (bullet || numbered) {
                this.flush(out, paragraph);
                String kind = bullet ? "ul" : "ol";
                if (!kind.equals(list)) {
                    list = this.closeList(out, list);
                    out.append('<').append(kind).append('>');
                    list = kind;
                }
                String item = bullet ? trimmed.substring(2) : trimmed.substring(trimmed.indexOf(". ") + 2);
                int depth = (line.length() - line.stripLeading().length()) / 2;
                out.append("<li class=\"depth").append(Math.min(depth, 3)).append("\">").append(this.inline(item)).append("</li>");
                continue;
            }
            list = this.closeList(out, list);
            if (trimmed.equals("---") || trimmed.equals("***")) {
                this.flush(out, paragraph);
                out.append("<hr>");
            } else if (trimmed.startsWith("## ")) {
                this.flush(out, paragraph);
                out.append("<h4>").append(this.inline(trimmed.substring(3))).append("</h4>");
            } else if (trimmed.startsWith("# ")) {
                this.flush(out, paragraph);
                out.append("<h3>").append(this.inline(trimmed.substring(2))).append("</h3>");
            } else if (trimmed.startsWith("> ") || trimmed.equals(">")) {
                this.flush(out, paragraph);
                out.append("<blockquote>").append(this.inline(trimmed.length() > 1 ? trimmed.substring(2) : "")).append("</blockquote>");
            } else {
                paragraph.add(line);
            }
        }
        this.flush(out, paragraph);
        this.closeList(out, list);
        return out.toString();
    }

    private String closeList(StringBuilder out, String list) {
        if (list != null) out.append("</").append(list).append('>');
        return null;
    }

    private void flush(StringBuilder out, List<String> paragraph) {
        if (paragraph.isEmpty()) return;
        String joined = String.join("\n", paragraph).strip();
        paragraph.clear();
        String align = "";
        if (joined.startsWith("->") && joined.endsWith("<-")) {
            joined = joined.substring(2, joined.length() - 2);
            align = " class=\"center\"";
        } else if (joined.startsWith("->")) {
            joined = joined.substring(2);
            align = " class=\"right\"";
        }
        out.append("<p").append(align).append('>').append(this.inline(joined).replace("\n", "<br>")).append("</p>");
    }

    String inline(String raw) {
        StringBuilder out = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        boolean code = false;
        int colours = 0;
        for (int at = 0; at < raw.length(); at++) {
            char c = raw.charAt(at);
            if (c == '\\' && at + 1 < raw.length()) {
                out.append(escape(String.valueOf(raw.charAt(++at))));
                continue;
            }
            if (code && c != '`') {
                out.append(escape(String.valueOf(c)));
                continue;
            }
            if (raw.startsWith("**", at)) {
                out.append(bold ? "</b>" : "<b>");
                bold = !bold;
                at++;
            } else if (raw.startsWith("__", at)) {
                out.append(underline ? "</u>" : "<u>");
                underline = !underline;
                at++;
            } else if (raw.startsWith("~~", at)) {
                out.append(strike ? "</s>" : "<s>");
                strike = !strike;
                at++;
            } else if (c == '`') {
                out.append(code ? "</code>" : "<code>");
                code = !code;
            } else if ((c == '*' || c == '_') && this.toggles(raw, at, italic)) {
                out.append(italic ? "</i>" : "<i>");
                italic = !italic;
            } else if (c == '[') {
                int close = raw.indexOf("](", at);
                int end = close < 0 ? -1 : raw.indexOf(')', close);
                if (close < 0 || end < 0) {
                    out.append('[');
                    continue;
                }
                String href = this.href(raw.substring(close + 2, end).strip());
                String label = this.inline(raw.substring(at + 1, close));
                out.append(href == null ? label : "<a href=\"" + escape(href) + "\">" + label + "</a>");
                at = end;
            } else if (c == '{') {
                int end = raw.indexOf('}', at);
                if (end < 0) {
                    out.append('{');
                    continue;
                }
                String tag = raw.substring(at + 1, end);
                if (tag.equals("/")) {
                    if (colours > 0) {
                        out.append("</span>");
                        colours--;
                    }
                } else if (tag.matches("#[0-9A-Fa-f]{6}")) {
                    out.append("<span style=\"color:").append(tag).append("\">");
                    colours++;
                } else {
                    out.append(this.tag(tag));
                }
                at = end;
            } else {
                out.append(escape(String.valueOf(c)));
            }
        }
        if (code) out.append("</code>");
        if (italic) out.append("</i>");
        if (strike) out.append("</s>");
        if (underline) out.append("</u>");
        if (bold) out.append("</b>");
        out.append("</span>".repeat(colours));
        return out.toString();
    }

    private boolean toggles(String raw, int at, boolean open) {
        boolean before = at > 0 && Character.isLetterOrDigit(raw.charAt(at - 1));
        boolean after = at + 1 < raw.length() && Character.isLetterOrDigit(raw.charAt(at + 1));
        if (raw.charAt(at) == '_' && before && after) return false;
        return open ? !after || !before : after;
    }

    private String tag(String tag) {
        if (tag.startsWith("key:")) {
            String name = tag.substring(4);
            for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
                if (mapping.getName().equals(name)) return "<kbd>" + escape(mapping.getTranslatedKeyMessage().getString()) + "</kbd>";
            }
            return "<kbd>" + escape(name) + "</kbd>";
        }
        if (tag.equals("player")) return "<i>the reader</i>";
        if (tag.startsWith("item:") || tag.startsWith("slot:")) {
            String written = tag.substring(5);
            int bar = written.indexOf('|');
            Identifier id = Identifier.tryParse(bar < 0 ? written : written.substring(0, bar));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return escape(tag);
            String icon = "<img class=\"item\" src=\"" + links.itemIcon(id) + "\" alt=\"\">";
            String named = tag.startsWith("slot:") ? icon : icon + escape(new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString());
            String page = links.itemPage(id);
            return page == null ? named : "<a href=\"" + escape(page) + "\">" + named + "</a>";
        }
        if (tag.startsWith("entry:")) {
            String target = tag.substring(6);
            String entry = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
            return "<a href=\"" + escape(links.entry(entry)) + "\">" + escape(entry) + "</a>";
        }
        if (tag.startsWith("entity:")) {
            Identifier id = Identifier.tryParse(tag.substring(7));
            if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return escape(tag);
            return escape(BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription().getString());
        }
        if (tag.startsWith("flag:")) {
            String[] parts = tag.split("\\|", -1);
            return parts.length > 2 ? this.inline(parts[2]) : "";
        }
        return escape("{" + tag + "}");
    }

    private @Nullable String href(String target) {
        if (target.startsWith("https://") || target.startsWith("http://")) return target;
        if (target.startsWith("category:")) return links.category(target.substring(9));
        if (target.startsWith("item:")) {
            Identifier id = Identifier.tryParse(target.substring(5));
            return id == null ? null : links.itemPage(id);
        }
        if (target.contains(":") && !target.startsWith("entry:")) return null;
        String entry = target.startsWith("entry:") ? target.substring(6) : target;
        if (entry.contains("#")) entry = entry.substring(0, entry.indexOf('#'));
        return links.entry(entry);
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
