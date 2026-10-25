package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.FoliosLock;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The items the player has had, for "obtained" locks and objectives, which open once an item has been in the
// inventory, whatever becomes of it after. Books are client resources, so the client keeps watch: only for the items
// some book's locks and objectives name, and only when the inventory has changed. What it finds is kept with each such
// book's reading (FoliosMemory, which the server keeps), so it lasts across games and computers.
public final class ObtainedItems {
    // Each book's "obtained" items, worked out once a world is joined (tags need its registries); null until then.
    private static @Nullable Map<Identifier, List<FoliosStack>> watched;
    private static @Nullable Inventory inventory;
    private static int changes = -1;

    private ObtainedItems() {}

    /** Forgets what is watched, for after books reload. */
    public static void clear() {
        watched = null;
        changes = -1;
    }

    /** Whether any of these items has been had, by the record of any book that asks about them. */
    public static boolean has(FoliosStack items) {
        Set<String> had = new HashSet<>();
        for (Identifier book : FoliosLibrary.books().keySet()) had.addAll(FoliosMemory.of(book).obtained());
        if (had.isEmpty()) return false;
        for (ItemStack stack : items.stacks()) {
            if (had.contains(id(stack))) return true;
        }
        return false;
    }

    /** Looks at the inventory when it has changed since last time, noting the items books ask about that are in it. */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            inventory = null;
            return;
        }
        Inventory now = minecraft.player.getInventory();
        if (now == inventory && now.getTimesChanged() == changes) return;
        inventory = now;
        changes = now.getTimesChanged();
        Map<Identifier, List<FoliosStack>> asked = watched();
        if (asked.isEmpty()) return;
        Set<String> present = new HashSet<>();
        for (int slot = 0; slot < now.getContainerSize(); slot++) {
            ItemStack stack = now.getItem(slot);
            if (!stack.isEmpty()) present.add(id(stack));
        }
        boolean found = false;
        for (Map.Entry<Identifier, List<FoliosStack>> book : asked.entrySet()) {
            FoliosMemory memory = FoliosMemory.of(book.getKey());
            boolean noted = false;
            for (FoliosStack items : book.getValue()) {
                for (ItemStack stack : items.stacks()) {
                    String id = id(stack);
                    if (present.contains(id) && memory.markObtained(id)) noted = true;
                }
            }
            if (noted) {
                memory.save();
                found = true;
            }
        }
        // Locks and objectives that just opened are laid out again.
        if (found) FoliosProgress.changed();
    }

    private static Map<Identifier, List<FoliosStack>> watched() {
        if (watched != null) return watched;
        Map<Identifier, List<FoliosStack>> found = new HashMap<>();
        for (FoliosBook book : FoliosLibrary.books().values()) {
            List<FoliosStack> items = new ArrayList<>();
            for (FoliosCategory category : book.visibleCategories()) add(items, category.lock());
            for (FoliosEntry entry : book.entries().values()) {
                add(items, entry.lock());
                for (FoliosEntry.Page page : entry.pages()) add(items, page.lock());
                for (FoliosObjective objective : entry.objectives()) items.addAll(objective.obtained());
            }
            if (!items.isEmpty()) found.put(book.id(), List.copyOf(items));
        }
        return watched = Map.copyOf(found);
    }

    private static void add(List<FoliosStack> items, FoliosLock lock) {
        for (FoliosObjective condition : lock.requires()) items.addAll(condition.obtained());
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
