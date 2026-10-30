package net.ixdarklord.folios.internal.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.ixdarklord.folios.api.FoliosBooks;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class BookLookup {
    private BookLookup() {}

    public static final KeyMapping KEY = new KeyMapping("key.folios.lookup", GLFW.GLFW_KEY_B, KeyMapping.Category.INVENTORY);

    static final long HOLD_MILLIS = 650;
    private static final long HOVER_MILLIS = 150;

    public record Found(Identifier book, FoliosBook.Location location, ItemStack bookItem) {
        boolean same(@Nullable Found other) {
            return other != null && book.equals(other.book) && location.equals(other.location);
        }
    }

    private static @Nullable Found shown;
    private static long shownAt;
    private static long heldSince = -1;
    private static @Nullable ClientTooltipComponent pending;
    private static boolean held;

    public static @Nullable Found find(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack.isEmpty() || FoliosBooks.bookOf(stack) != null) return null;
        Inventory inventory = player.getInventory();
        Found found = book(inventory.getSelectedItem(), stack);
        for (int slot = 0; found == null && slot < inventory.getContainerSize(); slot++) found = book(inventory.getItem(slot), stack);
        return found;
    }

    private static @Nullable Found book(ItemStack carried, ItemStack about) {
        Identifier id = FoliosBooks.bookOf(carried);
        FoliosBook book = id != null ? FoliosLibrary.book(id) : null;
        if (book == null) return null;
        FoliosBook.Location location = book.about(about);
        if (location == null) return null;
        FoliosEntry entry = book.entry(location.entry());
        if (entry == null || !entry.isOpen()) return null;
        return new Found(id, location, carried.copyWithCount(1));
    }

    public static void hovering(ItemStack stack) {
        Found found = find(stack);
        if (found == null) {
            pending = null;
            return;
        }
        if (!found.same(shown)) heldSince = -1;
        shown = found;
        shownAt = FoliosClock.millis();
        pending = new LookupTooltip(found);
    }

    public static @Nullable ClientTooltipComponent takePending() {
        ClientTooltipComponent taken = pending;
        pending = null;
        return taken;
    }

    public static void doneHovering() {
        pending = null;
    }

    static float progress(Found found) {
        if (heldSince < 0 || !found.same(shown)) return 0;
        return Math.min(1, (FoliosClock.millis() - heldSince) / (float) HOLD_MILLIS);
    }

    public static void tick(Minecraft minecraft) {
        long now = FoliosClock.millis();
        if (shown == null || now - shownAt > HOVER_MILLIS || !(minecraft.gui.screen() instanceof AbstractContainerScreen<?>)) {
            shown = null;
            heldSince = -1;
            return;
        }
        if (!held) {
            heldSince = -1;
            return;
        }
        if (heldSince < 0) heldSince = now;
        if (now - heldSince < HOLD_MILLIS) return;
        Found found = shown;
        shown = null;
        heldSince = -1;
        FoliosClient.open(found.book(), found.location());
    }

    public static void key(KeyEvent event, int action) {
        if (KEY.matches(event)) held = action != GLFW.GLFW_RELEASE;
    }

    public static void button(int button, int action) {
        InputConstants.Key key = InputConstants.getKey(KEY.saveString());
        if (key.getType() == InputConstants.Type.MOUSE && key.getValue() == button) held = action != GLFW.GLFW_RELEASE;
    }
}
