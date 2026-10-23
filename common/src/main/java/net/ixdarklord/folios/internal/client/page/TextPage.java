package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPageElements;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// A page: a title, and "elements", what it shows one under another: text, a recipe, a multiblock, a structure, a build,
// an entity, a picture, a spotlight, links, a quest... Each is written {"type": "recipe", "recipe": ...}, as an element
// of its type is (a plain string is text), with its own title and text if it has them; "text" on the page itself is
// text before them. An element that doesn't fit in what is left of the page starts the next, and text runs on over as
// many pages as it needs. A page written as one element ({"type": "recipe", ...}) is a page of that element alone.
public record TextPage(Optional<String> title, String text, List<FoliosPageElement> elements) implements FoliosPageElement {
    private static final Codec<FoliosPageElement> ELEMENT = ExtraCodecs.JSON.comapFlatMap(TextPage::element, element -> new JsonObject());

    public static final MapCodec<TextPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("title").forGetter(TextPage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(TextPage::text),
            ELEMENT.listOf(0, 64).optionalFieldOf("elements", List.of()).forGetter(TextPage::elements)
    ).apply(instance, TextPage::new));

    /** The room left between elements. */
    private static final int GAP = 6;

    // An element: of any type that lays itself out as a title, a part of its own and text do (not a spread, nor a
    // page of elements itself).
    private static DataResult<FoliosPageElement> element(JsonElement json) {
        JsonObject object;
        if (json.isJsonPrimitive()) {
            object = new JsonObject();
            object.addProperty("text", json.getAsString());
        } else if (json.isJsonObject()) {
            object = json.getAsJsonObject();
        } else {
            return DataResult.error(() -> "an element is an object with a type, or a string of text");
        }
        var type = FoliosPageElements.id(object.has("type") ? object.get("type").getAsString() : "text");
        MapCodec<? extends FoliosPageElement> codec = FoliosPageElements.get(type);
        if (codec == null) return DataResult.error(() -> "no element type " + type);
        return codec.codec().parse(JsonOps.INSTANCE, object).flatMap(page -> {
            if (page.spread()) return DataResult.error(() -> "a spread is a page of its own, not an element");
            if (page instanceof TextPage text && !text.elements.isEmpty()) return DataResult.error(() -> "an element can't have elements of its own");
            return DataResult.success((FoliosPageElement) page);
        });
    }

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        if (elements.isEmpty()) return layout.flow(title.orElse(null), 0, text, null);
        // What to set, in order: the page's text, then each element's title, what it shows and its text.
        List<Piece> pieces = new ArrayList<>();
        if (!text.isEmpty()) pieces.add(new Words(layout.text(text)));
        // The page's icon heads its own title, not its elements'.
        net.ixdarklord.folios.api.client.page.FoliosIcon icon = layout.titleIcon();
        layout.titleIcon(net.ixdarklord.folios.api.client.page.FoliosIcon.NONE);
        for (FoliosPageElement element : elements) {
            List<FoliosLayout.Flow> flows = layout.capture(element);
            if (flows.isEmpty()) Folios.LOGGER.warn("A page's element ({}) lays itself out in a way an element can't be", element.getClass().getSimpleName());
            for (FoliosLayout.Flow flow : flows) {
                if (flow.title() != null && !flow.title().isEmpty()) {
                    String heading = flow.title();
                    pieces.add(new Fixed(FoliosLayout.TITLE_HEIGHT, (painter, y) -> FoliosLayout.title(painter, FoliosText.inline(heading), y)));
                }
                if (flow.part() != null && flow.reserved() > 0) pieces.add(new Fixed(flow.reserved(), flow.part()));
                if (flow.text() != null && !flow.text().isEmpty()) pieces.add(new Words(layout.text(flow.text())));
            }
        }
        layout.titleIcon(icon);
        return set(title, icon, pieces, layout.contentBottom());
    }

    // One thing to set on a page: a title or what an element shows, which stays whole, or text, which can be split.
    private sealed interface Piece permits Fixed, Words {}

    private record Fixed(int height, Part part) implements Piece {}

    private record Words(FoliosText text) implements Piece {}

    private record Placed(int y, Part part) {}

    // The pieces set one under another down the page, onto as many pages as they take.
    private static List<Laid> set(Optional<String> title, net.ixdarklord.folios.api.client.page.FoliosIcon icon, List<Piece> pieces, int bottom) {
        List<List<Placed>> pages = new ArrayList<>();
        List<Placed> page = new ArrayList<>();
        pages.add(page);
        int y = FoliosLayout.CONTENT_TOP;
        if (title.isPresent() && !title.get().isEmpty()) {
            String heading = title.get();
            page.add(new Placed(y, (painter, py) -> FoliosLayout.title(painter, FoliosText.inline(heading), py, icon)));
            y += FoliosLayout.TITLE_HEIGHT;
        }
        // Whether nothing is on this page yet (but its title): the next piece then goes at its top, with no gap.
        boolean fresh = true;
        int full = bottom - FoliosLayout.CONTENT_TOP;
        for (Piece piece : pieces) {
            int at = fresh ? y : y + GAP;
            switch (piece) {
                case Fixed fixed -> {
                    // Whole, so on the next page if it doesn't fit here (on an empty page, as it is).
                    if (!fresh && at + fixed.height() > bottom) {
                        page = new ArrayList<>();
                        pages.add(page);
                        at = FoliosLayout.CONTENT_TOP;
                    }
                    page.add(new Placed(at, fixed.part()));
                    y = at + fixed.height();
                }
                case Words words -> {
                    List<FoliosText> split = words.text().split(Math.max(0, bottom - at), full);
                    for (int index = 0; index < split.size(); index++) {
                        FoliosText text = split.get(index);
                        if (index > 0) {
                            page = new ArrayList<>();
                            pages.add(page);
                            at = FoliosLayout.CONTENT_TOP;
                        }
                        // An empty first piece: none of it fitted here, so it starts on the next page.
                        if (text.isEmpty()) continue;
                        page.add(new Placed(at, (painter, py) -> text.draw(painter, FoliosLayout.CONTENT_LEFT, py)));
                        y = at + text.height();
                    }
                }
            }
            fresh = false;
        }
        List<Laid> laid = new ArrayList<>();
        for (List<Placed> placed : pages) {
            if (placed.isEmpty() && !laid.isEmpty()) continue;
            laid.add(painter -> {
                for (Placed one : placed) one.part().draw(painter, one.y());
            });
        }
        return laid;
    }

    @Override
    public String searchText() {
        StringBuilder words = new StringBuilder(FoliosText.resolve(title.orElse(""))).append(' ').append(FoliosText.resolve(text));
        for (FoliosPageElement element : elements) words.append(' ').append(element.searchText());
        return words.toString();
    }

    // Its elements' items and objectives are the page's: pressing a spotlit item anywhere comes here, a quest counts.
    @Override
    public List<FoliosStack> items() {
        List<FoliosStack> items = new ArrayList<>();
        for (FoliosPageElement element : elements) items.addAll(element.items());
        return items;
    }

    @Override
    public List<FoliosObjective> objectives() {
        List<FoliosObjective> objectives = new ArrayList<>();
        for (FoliosPageElement element : elements) objectives.addAll(element.objectives());
        return objectives;
    }
}
