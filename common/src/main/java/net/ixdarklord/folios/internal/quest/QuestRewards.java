package net.ixdarklord.folios.internal.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A quest's {@code "rewards"}: items it always gives, and random gifts, each drawn from a list:
 * <pre>{@code
 * "rewards": {
 *   "items": ["minecraft:diamond*3", {"id": "minecraft:potion", "components": {...}}],
 *   "random": [{"item": "minecraft:emerald*4", "weight": 3}, "minecraft:gold_ingot*8"],
 *   "random_rolls": 1
 * }
 * }</pre>
 * Read the same way on the server, which gives them, and on the client, which shows them.
 */
public record QuestRewards(List<ItemStack> items, List<Choice> random, int rolls) {
    /** The most items, and random choices, a reward lists. */
    private static final int MOST = 32;

    /** A random gift's choice, picked {@code weight} times as often as one of weight 1. */
    public record Choice(ItemStack stack, int weight) {}

    public boolean isEmpty() {
        return items.isEmpty() && random.isEmpty();
    }

    /** Rewards as written, their items made with the registries given (the server's, or the client's world's). */
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

    // An item stack as written: "minecraft:diamond", "minecraft:diamond*4", or a stack object; empty if it can't be read.
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
                    // Not a count after all.
                }
            }
            ResourceLocation id = ResourceLocation.tryParse(text);
            int amount = Math.max(1, count);
            return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item, amount)).orElse(ItemStack.EMPTY);
        }
        if (json.isJsonObject()) {
            return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json).result().orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    /** Gives them to a player: into their inventory, or at their feet where it is full; the random gifts drawn now. */
    public void give(ServerPlayer player) {
        List<ItemStack> given = new ArrayList<>();
        for (ItemStack stack : items) given.add(stack.copy());
        RandomSource random = player.getRandom();
        for (int roll = 0; roll < rolls && !this.random.isEmpty(); roll++) given.add(pick(random).stack().copy());
        for (ItemStack stack : given) {
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
        }
    }

    // One of the random choices, each as likely as its weight.
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
