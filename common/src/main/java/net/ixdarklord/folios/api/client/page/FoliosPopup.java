package net.ixdarklord.folios.api.client.page;

import net.minecraft.network.chat.Component;

/**
 * A small window a link opens over the book: under the pointer while it is over the link's words, and pinned in place
 * once they are pressed, until the reader presses elsewhere or Esc. It has a title, centred along its top, and a body of
 * its own drawing under it, through a painter as a page is drawn, so a scene in it can be turned and zoomed while it is
 * pinned. Folios's own show an entity, a structure, a build or a multiblock ({@code entity:}, {@code structure:},
 * {@code build:} and {@code multiblock:} links); a mod adds its own with {@link net.ixdarklord.folios.api.client.FoliosLinks}.
 * <p>
 * A popup is made once for each link of a laid-out book and kept while the book is, so it can hold a scene.
 */
public interface FoliosPopup {
    /** Its title, along its top. */
    Component title();

    /** How wide its body is, in page pixels. */
    default int width() {
        return 120;
    }

    /** How tall its body is, in page pixels. */
    default int height() {
        return 80;
    }

    /**
     * Draws its body, {@code width()} by {@code height()} from the painter's 0, 0.
     *
     * @param pinned whether it is pinned, and can be pressed, dragged and scrolled; while it only follows the pointer it
     *               is for looking at
     */
    void draw(FoliosPainter painter, boolean pinned);
}
