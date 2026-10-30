package net.ixdarklord.folios.internal.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record QuestRewards(List<ItemStack> items, List<Choice> random, int rolls) {
    private static final int MOST = 32;

    public record Choice(ItemStack stack, int weight) {}

    public boolean isEmpty() {
        return items.isEmpty() && random.isEmpty();
    }

    public static QuestRewards read(JsonObject json, HolderLookup.Provider registries) {
        List<ItemStack> items = new ArrayList<>();
        if (json.has("items")) {
            for (JsonElement element : json.getAsJsonArray("items")) {
                ItemStack stack = stack(element, registries);
                if (!stack.isEmpty() && items.size() < MOST) items.add(stack);
            }
        }
        List<Choice> random = new ArrayList<>();
        if (json.has("random")) {
            for (JsonElement element : json.getAsJsonArray("random")) {
                boolean weighed = element.isJsonObject() && element.getAsJsonObject().has("item");
                ItemStack stack = stack(weighed ? element.getAsJsonObject().get("item") : element, registries);
                int weight = weighed && element.getAsJsonObject().has("weight") ? Math.max(1, element.getAsJsonObject().get("weight").getAsInt()) : 1;
                if (!stack.isEmpty() && random.size() < MOST) random.add(new Choice(stack, weight));
            }
        }
        int rolls = json.has("random_rolls") ? Math.max(1, Math.min(MOST, json.get("random_rolls").getAsInt())) : 1;
        return new QuestRewards(List.copyOf(items), List.copyOf(random), rolls);
    }

    private static ItemStack stack(JsonElement json, HolderLookup.Provider registries) {
        if (json.isJsonPrimitive()) {
            String text = json.getAsString().strip();
            int count = 1;
            int star = text.lastIndexOf('*');
            if (star > 0) {
                try {
                    count = Integer.parseInt(text.substring(star + 1).strip());
                    text = text.substring(0, star).strip();
                } catch (NumberFormatException ignored) {
                }
            }
            Identifier id = Identifier.tryParse(text);
            int amount = Math.max(1, count);
            return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item, amount)).orElse(ItemStack.EMPTY);
        }
        if (json.isJsonObject()) {
            return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json).result().orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    public void give(ServerPlayer player) {
        List<ItemStack> given = new ArrayList<>();
        for (ItemStack stack : items) given.add(stack.copy());
        RandomSource random = player.getRandom();
        for (int roll = 0; roll < rolls && !this.random.isEmpty(); roll++) given.add(pick(random).stack().copy());
        for (ItemStack stack : given) {
            if (player.getInventory().add(stack) || stack.isEmpty()) continue;
            var drop = player.createItemStackToDrop(stack, false, false);
            if (drop == null) continue;
            drop.setNoPickUpDelay();
            drop.setTarget(player.getUUID());
            drop.level().addFreshEntity(drop);
        }
    }

    private Choice pick(RandomSource source) {
        int total = 0;
        for (Choice choice : random) total += choice.weight();
        int at = source.nextInt(total);
        for (Choice choice : random) {
            at -= choice.weight();
            if (at < 0) return choice;
        }
        return random.getLast();
    }
}
