package net.ixdarklord.folios.internal.client.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPainter;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.FoliosReader;
import net.ixdarklord.folios.internal.client.FoliosSession;
import net.ixdarklord.folios.internal.client.page.Shapes;
import net.ixdarklord.folios.internal.client.page.TextPage;
import net.ixdarklord.folios.internal.client.paint.AbstractPainter;
import net.ixdarklord.folios.internal.client.paint.ScreenPainter;
import net.ixdarklord.folios.internal.client.screen.FoliosScreen;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class ExportJob {
    private static final int MARGIN = 4;
    private static final int FRAMES = 3;
    private static final int COLUMNS = 8;
    private static final int CELL = 18;
    private static final float SECONDS = 0;

    private static @Nullable ExportJob running;

    private sealed interface Shot permits PageShot, IconShot {}

    private record PageShot(FoliosPageElement.Laid laid, Path file) implements Shot {}

    private record Chapter(String title, @Nullable String category, List<String> pages) {}

    private record Icon(Consumer<FoliosPainter> draw, Path file) {}

    private record IconShot(List<Icon> icons) implements Shot {}

    private final FoliosBook book;
    private final FoliosSession session;
    private final FoliosReader reader;
    private final FoliosPalette palette;
    private final Path root;
    private final List<Shot> shots = new ArrayList<>();
    private final Map<Path, String> files = new LinkedHashMap<>();
    private final Map<String, Icon> icons = new LinkedHashMap<>();
    private final Map<String, Chapter> chapters = new LinkedHashMap<>();
    private final Map<Path, List<AbstractPainter.LinkSpot>> spots = new HashMap<>();
    private final JsonArray searchable = new JsonArray();
    private float scale = 1;
    private int at;
    private int frames;
    private int failed;
    private boolean done;

    private ExportJob(FoliosBook book, FoliosSession session) {
        this.book = book;
        this.session = session;
        this.reader = new FoliosReader(book, session, null);
        this.palette = FoliosScreen.pagePalette(book);
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("folios-export");
        this.root = base.resolve(book.id().getNamespace()).resolve(book.id().getPath());
    }

    public static Component start(ResourceLocation id) {
        if (running != null) return Component.translatableWithFallback("folios.export.busy", "A book is already being exported");
        FoliosBook book = FoliosLibrary.book(id);
        FoliosSession session = book == null ? null : FoliosLibrary.session(id);
        if (book == null || session == null) return Component.translatableWithFallback("folios.export.missing", "No guide book with that id");
        ExportJob job = new ExportJob(book, session);
        try {
            FoliosText.in(book, () -> {
                job.plan();
                return null;
            });
        } catch (RuntimeException e) {
            Folios.LOGGER.error("Couldn't export the guide {}", id, e);
            return Component.translatableWithFallback("folios.export.failed", "Couldn't export %s: %s", id.toString(), String.valueOf(e.getMessage()));
        }
        running = job;
        Minecraft.getInstance().setScreen(new ExportScreen(job));
        return Component.translatableWithFallback("folios.export.started", "Exporting %s...", book.title());
    }

    private void plan() {
        Map<String, String> pages = new LinkedHashMap<>();
        StringBuilder index = new StringBuilder();
        HtmlText top = this.text("");
        index.append("<header class=\"cover\">").append(this.icon("book", book.icon(), "")).append("<h1>").append(HtmlText.escape(book.title().getString())).append("</h1>");
        if (!book.subtitle().isBlank()) index.append("<p class=\"subtitle\">").append(top.inline(FoliosText.resolve(book.subtitle()))).append("</p>");
        index.append("</header>");
        if (!FoliosText.isBlank(book.landing())) index.append("<section class=\"landing\">").append(top.blocks(FoliosText.resolve(book.landing()))).append("</section>");
        List<FoliosEntry> order = new ArrayList<>();
        for (FoliosCategory category : book.visibleCategories()) {
            index.append("<section class=\"category\" id=\"").append(HtmlText.escape(category.id())).append("\"><h2>")
                    .append(this.icon("category_" + category.id(), category.icon(), "")).append(HtmlText.escape(category.title().getString())).append("</h2>");
            if (!category.description().isBlank()) index.append(top.blocks(FoliosText.resolve(category.description())));
            index.append("<ul class=\"entries\">");
            for (FoliosEntry entry : book.entries(category.id())) {
                order.add(entry);
                index.append("<li><a href=\"").append(HtmlText.escape(this.entryFile(entry.id(), ""))).append("\">")
                        .append(this.icon("entry_" + entry.id(), entry.icon(), "")).append(HtmlText.escape(entry.title().getString())).append("</a></li>");
            }
            index.append("</ul></section>");
        }
        files.put(root.resolve("contents.html"), this.page(book.title().getString(), "", index.toString()));
        this.chapter("home", book.title().getString(), null);
        for (FoliosCategory category : book.visibleCategories()) this.chapter("category:" + category.id(), category.title().getString(), null);
        for (FoliosEntry entry : order) {
            FoliosCategory category = book.category(entry.category());
            this.chapter("entry:" + entry.id(), entry.title().getString(), "category:" + entry.category());
            JsonObject found = new JsonObject();
            found.addProperty("key", "entry:" + entry.id());
            found.addProperty("title", entry.title().getString());
            found.addProperty("category", category != null ? category.title().getString() : "");
            StringBuilder words = new StringBuilder();
            for (FoliosEntry.Page page : entry.pages()) words.append(' ').append(FoliosText.plain(page.page().searchText()));
            found.addProperty("text", words.toString().strip());
            searchable.add(found);
        }
        for (int number = 0; number < order.size(); number++) {
            FoliosEntry entry = order.get(number);
            String up = "../".repeat(1 + (int) entry.id().chars().filter(c -> c == '/').count());
            files.put(root.resolve(this.entryFile(entry.id(), "")), this.page(entry.title().getString(), up,
                    this.entry(entry, up, number > 0 ? order.get(number - 1) : null, number + 1 < order.size() ? order.get(number + 1) : null, pages)));
        }
        List<Icon> sheet = new ArrayList<>();
        for (Icon icon : icons.values()) {
            sheet.add(icon);
            if (sheet.size() == COLUMNS * 4) {
                shots.add(new IconShot(List.copyOf(sheet)));
                sheet.clear();
            }
        }
        if (!sheet.isEmpty()) shots.add(new IconShot(List.copyOf(sheet)));
        files.put(root.resolve("style.css"), this.style());
    }

    private void chapter(String key, String title, @Nullable String category) {
        List<String> names = new ArrayList<>();
        List<FoliosPageElement.Laid> laid = reader.chapter(key);
        for (int index = 0; index < laid.size(); index++) {
            String name = "pages/" + slug(key) + "_" + index + ".png";
            names.add(name);
            shots.add(new PageShot(laid.get(index), root.resolve(name)));
        }
        chapters.put(key, new Chapter(title, category, names));
    }

    private String entry(FoliosEntry entry, String up, @Nullable FoliosEntry previous, @Nullable FoliosEntry next, Map<String, String> pages) {
        HtmlText text = this.text(up);
        StringBuilder out = new StringBuilder();
        FoliosCategory category = book.category(entry.category());
        out.append("<nav class=\"crumbs\"><a href=\"").append(up).append("contents.html\">").append(HtmlText.escape(book.title().getString())).append("</a>");
        if (category != null) {
            out.append(" &rsaquo; <a href=\"").append(up).append("contents.html#").append(HtmlText.escape(category.id())).append("\">")
                    .append(HtmlText.escape(category.title().getString())).append("</a>");
        }
        out.append(" &middot; <a href=\"").append(up).append("index.html#entry:").append(HtmlText.escape(entry.id())).append("\">Read it in the book</a>");
        out.append("</nav><h1>").append(this.icon("entry_" + entry.id(), entry.icon(), up)).append(HtmlText.escape(entry.title().getString())).append("</h1>");
        FoliosSession.EntryPages laid = session.entry(entry.id());
        for (int source = 0; source < entry.pages().size(); source++) {
            FoliosPageElement element = entry.pages().get(source).page();
            if (element instanceof TextPage plain && plain.elements().isEmpty()) {
                out.append("<section class=\"page\">");
                plain.title().ifPresent(title -> out.append("<h2>").append(text.inline(FoliosText.resolve(title))).append("</h2>"));
                out.append(text.blocks(FoliosText.resolve(plain.text()))).append("</section>");
                continue;
            }
            int from = laid.start(source);
            int to = source + 1 < entry.pages().size() ? laid.start(source + 1) : laid.pages().size();
            String alt = HtmlText.escape(FoliosText.plain(element.searchText()).strip());
            out.append("<section class=\"page pictures\">");
            for (int page = from; page < Math.max(from + 1, to) && page < laid.pages().size(); page++) {
                String name = "pages/" + slug("entry:" + entry.id()) + "_" + page + ".png";
                out.append("<img class=\"shot\" src=\"").append(up).append(name).append("\" alt=\"").append(alt).append("\">");
            }
            out.append("</section>");
        }
        out.append("<nav class=\"turn\">");
        if (previous != null) out.append("<a href=\"").append(HtmlText.escape(this.entryFile(previous.id(), up))).append("\">&larr; ")
                .append(HtmlText.escape(previous.title().getString())).append("</a>");
        out.append("<span></span>");
        if (next != null) out.append("<a href=\"").append(HtmlText.escape(this.entryFile(next.id(), up))).append("\">")
                .append(HtmlText.escape(next.title().getString())).append(" &rarr;</a>");
        return out.append("</nav>").toString();
    }

    private HtmlText text(String up) {
        return new HtmlText(new HtmlText.Links() {
            @Override
            public String entry(String id) {
                return ExportJob.this.entryFile(id, up);
            }

            @Override
            public String category(String id) {
                return up + "contents.html#" + id;
            }

            @Override
            public String itemIcon(ResourceLocation item) {
                String name = "items/" + slug(item.toString()) + ".png";
                icons.computeIfAbsent(name, key -> new Icon(painter -> painter.item(new ItemStack(BuiltInRegistries.ITEM.get(item)), 0, 0), root.resolve(key)));
                return up + name;
            }

            @Override
            public @Nullable String itemPage(ResourceLocation item) {
                FoliosBook.Location location = book.about(new ItemStack(BuiltInRegistries.ITEM.get(item)));
                return location == null ? null : ExportJob.this.entryFile(location.entry(), up);
            }
        });
    }

    private String entryFile(String id, String up) {
        return up + "entries/" + id + ".html";
    }

    private String icon(String key, FoliosIcon icon, String up) {
        if (icon.isEmpty()) return "";
        String name = "icons/" + slug(key) + ".png";
        icons.computeIfAbsent(name, file -> new Icon(painter -> icon.draw(painter, 0, 0), root.resolve(file)));
        return "<img class=\"icon\" src=\"" + up + name + "\" alt=\"\">";
    }

    private static String slug(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private String page(String title, String up, String body) {
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>"
                + HtmlText.escape(title) + "</title>\n<link rel=\"stylesheet\" href=\"" + up + "style.css\">\n</head>\n<body>\n<main>\n" + body
                + "\n</main>\n<footer>Made with <a href=\"https://modrinth.com/mod/folios\">Folios</a></footer>\n</body>\n</html>\n";
    }

    private String style() {
        return """
                :root { --paper: %s; --ink: %s; --muted: %s; --accent: %s; --line: %s; --panel: %s; }
                * { box-sizing: border-box; }
                body { margin: 0; background: color-mix(in srgb, var(--paper) 88%%, #000); color: var(--ink); font: 17px/1.6 Georgia, "Times New Roman", serif; }
                main { max-width: 760px; margin: 32px auto; padding: 32px 40px; background: var(--paper); border: 1px solid var(--line); border-radius: 6px; }
                h1, h2, h3, h4 { line-height: 1.25; }
                h1 { font-size: 30px; margin: 0 0 18px; }
                h2 { font-size: 22px; border-bottom: 2px solid var(--accent); padding-bottom: 4px; }
                a { color: var(--accent); }
                img.icon, img.item { width: 24px; height: 24px; vertical-align: -5px; margin-right: 6px; image-rendering: pixelated; }
                img.item { width: 20px; height: 20px; margin: 0 3px 0 0; }
                img.shot { display: block; max-width: 100%%; width: 408px; margin: 12px auto; image-rendering: pixelated; border: 1px solid var(--line); border-radius: 4px; }
                .pictures { display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; }
                .cover { text-align: center; }
                .cover img.icon { width: 64px; height: 64px; display: block; margin: 0 auto 8px; }
                .subtitle { color: var(--muted); font-style: italic; margin-top: -10px; }
                ul.entries { list-style: none; padding: 0; columns: 2; }
                ul.entries li { margin: 4px 0; break-inside: avoid; }
                blockquote { margin: 12px 0; padding: 8px 14px; background: var(--panel); border-left: 4px solid var(--accent); border-radius: 3px; }
                code { color: var(--muted); }
                kbd { font-weight: bold; padding: 0 4px; border: 1px solid var(--line); border-radius: 3px; background: var(--panel); }
                hr { border: 0; border-top: 1px solid var(--line); margin: 20px 0; }
                .center { text-align: center; } .right { text-align: right; }
                li.depth1 { margin-left: 22px; } li.depth2 { margin-left: 44px; } li.depth3 { margin-left: 66px; }
                .crumbs { font-size: 14px; color: var(--muted); margin-bottom: 8px; }
                .turn { display: flex; justify-content: space-between; margin-top: 32px; border-top: 1px solid var(--line); padding-top: 12px; }
                footer { text-align: center; font-size: 13px; color: var(--muted); margin-bottom: 32px; }
                """.formatted(hex(palette.paper()), hex(palette.text()), hex(palette.muted()), hex(palette.accent()), hex(palette.line()), hex(palette.panel()));
    }

    private static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    void draw(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, palette.paper());
        if (done || at >= shots.size()) return;
        int guiScale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        float wanted = 3F / guiScale;
        int pageWidth = session.layout().pageWidth();
        int pageHeight = session.layout().pageHeight();
        scale = Math.min(wanted, Math.min((width - MARGIN * 2) / (float) pageWidth, (height - MARGIN * 2 - 12) / (float) pageHeight));
        Shot shot = shots.get(at);
        if (shot instanceof PageShot page) {
            ScreenPainter painter = new ScreenPainter(graphics, MARGIN, MARGIN, scale, palette, session, reader, SECONDS, Float.NaN, Float.NaN, new ArrayList<>());
            List<AbstractPainter.LinkSpot> found = new ArrayList<>();
            painter.collectLinks(found);
            try {
                Shapes.pageFrame(painter, book.textures().frame(), pageWidth, pageHeight, page.laid(), false);
                page.laid().draw(painter);
            } finally {
                painter.end();
            }
            spots.put(page.file(), found);
        } else if (shot instanceof IconShot sheet) {
            for (int index = 0; index < sheet.icons().size(); index++) {
                ScreenPainter painter = new ScreenPainter(graphics, MARGIN + (index % COLUMNS) * CELL * scale, MARGIN + (index / COLUMNS) * CELL * scale, scale,
                        palette, session, reader, SECONDS, Float.NaN, Float.NaN, new ArrayList<>());
                try {
                    sheet.icons().get(index).draw().accept(painter);
                } finally {
                    painter.end();
                }
            }
        }
        Component progress = Component.translatableWithFallback("folios.export.progress", "Exporting %s: %s of %s", book.title(), at + 1, shots.size());
        graphics.text(Minecraft.getInstance().font, progress, MARGIN, height - 11, palette.muted(), false);
        frames++;
    }

    public static void tick(Minecraft minecraft) {
        ExportJob job = running;
        if (job == null) return;
        if (job.done) {
            job.finish(minecraft);
            return;
        }
        if (!(minecraft.screen instanceof ExportScreen)) {
            job.cancel();
            return;
        }
        if (job.at >= job.shots.size()) {
            job.done = true;
            minecraft.setScreen(null);
            return;
        }
        if (job.frames < FRAMES) return;
        job.capture(minecraft, job.shots.get(job.at));
        job.at++;
        job.frames = 0;
    }

    private void capture(Minecraft minecraft, Shot shot) {
        int guiScale = Math.max(1, (int) minecraft.getWindow().getGuiScale());
        float pixels = scale * guiScale;
        List<Cut> cuts = new ArrayList<>();
        if (shot instanceof PageShot page) {
            cuts.add(new Cut(MARGIN * guiScale, MARGIN * guiScale, Math.round(session.layout().pageWidth() * pixels), Math.round(session.layout().pageHeight() * pixels), page.file()));
        } else if (shot instanceof IconShot sheet) {
            for (int index = 0; index < sheet.icons().size(); index++) {
                int x = Math.round(MARGIN * guiScale + (index % COLUMNS) * CELL * pixels);
                int y = Math.round(MARGIN * guiScale + (index / COLUMNS) * CELL * pixels);
                cuts.add(new Cut(x, y, Math.round(16 * pixels), Math.round(16 * pixels), sheet.icons().get(index).file()));
            }
        }
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            for (Cut cut : cuts) this.save(image, cut);
        }
    }

    private record Cut(int x, int y, int width, int height, Path file) {}

    private void save(NativeImage image, Cut cut) {
        int width = Math.min(cut.width(), image.getWidth() - cut.x());
        int height = Math.min(cut.height(), image.getHeight() - cut.y());
        if (width <= 0 || height <= 0) return;
        try (NativeImage out = new NativeImage(width, height, false)) {
            image.copyRect(out, cut.x(), cut.y(), 0, 0, width, height, false, false);
            Files.createDirectories(cut.file().getParent());
            out.writeToFile(cut.file());
        } catch (IOException e) {
            failed++;
            Folios.LOGGER.warn("Couldn't write {}: {}", cut.file(), e.getMessage());
        }
    }

    private void finish(Minecraft minecraft) {
        running = null;
        files.put(root.resolve("index.html"), BookSite.html(book.title().getString()));
        files.put(root.resolve("book.js"), "window.FOLIOS = " + this.data() + ";\n");
        try {
            for (Map.Entry<Path, String> file : files.entrySet()) {
                Files.createDirectories(file.getKey().getParent());
                Files.writeString(file.getKey(), file.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Folios.LOGGER.error("Couldn't write the export of {}", book.id(), e);
            failed++;
        }
        if (minecraft.player == null) return;
        String index = root.resolve("index.html").toAbsolutePath().toString();
        Component where = Component.literal(index).withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, index)));
        minecraft.player.sendSystemMessage(failed == 0
                ? Component.translatableWithFallback("folios.export.done", "Exported %s to %s", book.title(), where)
                : Component.translatableWithFallback("folios.export.partly", "Exported %s to %s, but %s files couldn't be written (see the log)", book.title(), where, failed)
                .withStyle(ChatFormatting.YELLOW));
    }

    private String data() {
        int width = session.layout().pageWidth();
        int height = session.layout().pageHeight();
        JsonObject data = new JsonObject();
        data.addProperty("title", book.title().getString());
        data.addProperty("start", "home");
        data.addProperty("pageWidth", width);
        data.addProperty("pageHeight", height);
        JsonObject colors = new JsonObject();
        colors.addProperty("paper", hex(palette.paper()));
        colors.addProperty("ink", hex(palette.text()));
        colors.addProperty("muted", hex(palette.muted()));
        colors.addProperty("accent", hex(palette.accent()));
        colors.addProperty("line", hex(palette.line()));
        colors.addProperty("cover", hex(book.cover()));
        data.add("colors", colors);
        JsonObject all = new JsonObject();
        JsonArray order = new JsonArray();
        for (Map.Entry<String, Chapter> chapter : chapters.entrySet()) {
            order.add(chapter.getKey());
            JsonObject shown = new JsonObject();
            shown.addProperty("title", chapter.getValue().title());
            if (chapter.getValue().category() != null) shown.addProperty("category", chapter.getValue().category());
            JsonArray pages = new JsonArray();
            for (String name : chapter.getValue().pages()) {
                JsonObject page = new JsonObject();
                page.addProperty("src", name);
                JsonArray links = new JsonArray();
                for (AbstractPainter.LinkSpot spot : spots.getOrDefault(root.resolve(name), List.of())) {
                    JsonObject link = this.link(spot.target());
                    if (link == null) continue;
                    link.addProperty("x", spot.x0() / width);
                    link.addProperty("y", spot.y0() / height);
                    link.addProperty("w", (spot.x1() - spot.x0()) / width);
                    link.addProperty("h", (spot.y1() - spot.y0()) / height);
                    links.add(link);
                }
                page.add("links", links);
                pages.add(page);
            }
            shown.add("pages", pages);
            all.add(chapter.getKey(), shown);
        }
        data.add("chapters", all);
        data.add("order", order);
        JsonArray tabs = new JsonArray();
        tabs.add(this.tab("home", book.title().getString(), "icons/book.png"));
        for (FoliosCategory category : book.visibleCategories()) {
            tabs.add(this.tab("category:" + category.id(), category.title().getString(), "icons/" + slug("category_" + category.id()) + ".png"));
        }
        data.add("tabs", tabs);
        data.add("entries", searchable);
        return data.toString();
    }

    private JsonObject tab(String key, String title, String icon) {
        JsonObject tab = new JsonObject();
        tab.addProperty("key", key);
        tab.addProperty("title", title);
        if (icons.containsKey(icon)) tab.addProperty("icon", icon);
        return tab;
    }

    private @Nullable JsonObject link(String target) {
        JsonObject link = new JsonObject();
        if (target.startsWith("https://") || target.startsWith("http://")) {
            link.addProperty("href", target);
            return link;
        }
        FoliosSession.Target found = session.resolve(target);
        if (found == null) return null;
        if (found.url() != null) {
            link.addProperty("href", found.url());
        } else if (found.category() != null) {
            link.addProperty("go", "category:" + found.category());
            link.addProperty("page", 0);
        } else if (found.entry() != null) {
            link.addProperty("go", "entry:" + found.entry());
            link.addProperty("page", Math.max(0, found.page()));
        } else {
            return null;
        }
        return link;
    }

    void cancel() {
        if (running != this || done) return;
        running = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.translatableWithFallback("folios.export.cancelled", "Export cancelled"));
    }
}
