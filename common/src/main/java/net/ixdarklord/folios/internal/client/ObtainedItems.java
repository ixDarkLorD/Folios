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

public final class ObtainedItems {
    private static @Nullable Map<Identifier, List<FoliosStack>> watched;
    private static @Nullable Inventory inventory;
    private static int changes = -1;

    private ObtainedItems() {}

    public static void clear() {
        watched = null;
        changes = -1;
    }

    public static boolean has(FoliosStack items) {
        Set<String> had = new HashSet<>();
        for (Identifier book : FoliosLibrary.books().keySet()) had.addAll(FoliosMemory.of(book).obtained());
        if (had.isEmpty()) return false;
        for (ItemStack stack : items.stacks()) {
            if (had.contains(id(stack))) return true;
        }
        return false;
    }

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
