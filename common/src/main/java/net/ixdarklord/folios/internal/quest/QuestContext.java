package net.ixdarklord.folios.internal.quest;

import net.ixdarklord.folios.api.FoliosQuests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What one check of a player's quests reads, gathered once for all of them: the player, what the server already knows of
 * their quests, and their inventory as it is now.
 */
final class QuestContext {
    final ServerPlayer player;
    // Kept up to date through a check, so later quests see earlier ones it finished.
    PlayerQuests state;
    private final List<ItemStack> inventory = new ArrayList<>();

    QuestContext(ServerPlayer player, PlayerQuests state) {
        this.player = player;
        this.state = state;
        var items = player.getInventory();
        for (int slot = 0; slot < items.getContainerSize(); slot++) {
            ItemStack stack = items.getItem(slot);
            if (!stack.isEmpty()) inventory.add(stack);
        }
    }

    /** The stacks the player carries now. */
    List<ItemStack> inventory() {
        return inventory;
    }

    boolean advancement(ResourceLocation id) {
        var server = player.level().getServer();
        net.minecraft.advancements.Advancement holder = server.getAdvancements().getAdvancement(id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    boolean read(@Nullable ResourceLocation book, String entry) {
        return book != null && state.isRead(book, entry);
    }

    boolean questDone(ResourceLocation quest) {
        return state.progress(quest).done();
    }

    /** A flag: the player's own, or one a mod works out ({@link FoliosQuests#registerFlag}); a leading ! reverses it. */
    boolean flag(String flag) {
        boolean not = flag.startsWith("!");
        String name = not ? flag.substring(1) : flag;
        boolean on = state.flags().contains(name) || FoliosQuests.flag(player, name);
        return on != not;
    }

    int carried(@Nullable ItemMatch items) {
        if (items == null) return 0;
        int count = 0;
        for (ItemStack stack : inventory) if (items.matches(stack)) count += stack.getCount();
        return count;
    }

    boolean obtained(@Nullable ItemMatch items) {
        if (items == null) return false;
        for (ResourceLocation id : state.obtained()) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null && items.matches(new ItemStack(item))) return true;
        }
        return this.carried(items) > 0;
    }

    float value(ResourceLocation source) {
        return FoliosQuests.value(player, source);
    }
}
