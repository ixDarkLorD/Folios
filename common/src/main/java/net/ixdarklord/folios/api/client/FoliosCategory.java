package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.minecraft.network.chat.Component;

/**
 * A category of a book's entries. Read from {@code categories/<id>.json} in the book's folder.
 *
 * @param name        raw, as written ({@link #title()} reads it)
 * @param description raw marked-up text shown above its entries; empty for none
 * @param sort        lower comes first, then by name
 * @param color       its tab's colour (RGB, {@code "color"} in its JSON); -1 for the book's own
 */
public record FoliosCategory(String id, String name, String description, FoliosIcon icon, int sort, FoliosLock lock, int color) {
    public Component title() {
        return FoliosText.inline(name);
    }

    public boolean isOpen() {
        return lock.isOpen();
    }

    public boolean isVisible() {
        return lock.isVisible();
    }
}
