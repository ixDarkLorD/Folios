package net.ixdarklord.folios.api.client.page;

import java.util.List;

/**
 * A page of an entry, as its JSON describes it. Before it is shown it is {@linkplain #lay laid out}: set to the page
 * size in the reader's language, which may take more than one page (text that runs on continues over the next).
 * That is done once per book and language, not every frame.
 * <p>
 * A mod adds its own kind of page by registering a codec for it ({@link GuidePageTypes#register}); its JSON object
 * is what the codec reads, with {@code "type"} naming it.
 * <pre>{@code
 * public record CounterPage(String title, int count) implements GuidePage {
 *     public static final MapCodec<CounterPage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
 *             Codec.STRING.fieldOf("title").forGetter(CounterPage::title),
 *             Codec.INT.fieldOf("count").forGetter(CounterPage::count)).apply(i, CounterPage::new));
 *
 *     public List<Laid> lay(GuideLayout layout) {
 *         return layout.flow(title, 20, null, (painter, y) -> painter.text(Component.literal("× " + count), 10, y, painter.palette().text()));
 *     }
 * }
 * }</pre>
 */
public interface GuidePage {
    /** Sets the page out: one or more pages, each drawn on its own. */
    List<Laid> lay(GuideLayout layout);

    /**
     * Whether the page spreads over both pages of an open book: its laid pages then come in pairs, a left page and the
     * right page beside it, and it starts on a left page (an empty page is put before it if need be).
     */
    default boolean spread() {
        return false;
    }

    /** Words of the page a search should find it by. */
    default String searchText() {
        return "";
    }

    /** The items this page is about: anywhere in the book, pressing one of them opens this page. */
    default List<GuideStack> items() {
        return List.of();
    }

    /**
     * What the page asks the player to do, if it is a quest: an entry with objectives shows how far along they are on
     * its row, and a toast says when they are all done.
     */
    default List<net.ixdarklord.folios.api.client.GuideObjective> objectives() {
        return List.of();
    }

    /** A page ready to draw. */
    @FunctionalInterface
    interface Laid {
        void draw(GuidePainter painter);
    }

    /**
     * A laid page that is half of a {@linkplain GuidePage#spread spread}: the book draws its frame joined with the
     * other half's over the fold, and one page number for both, at the fold.
     */
    interface SpreadHalf extends Laid {}

    /** Something drawn on a page from a given height down, such as what comes before a page's text. */
    @FunctionalInterface
    interface Part {
        void draw(GuidePainter painter, int y);
    }
}
