package net.ixdarklord.folios.internal.quest;

import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ItemMatch(Set<Item> items, List<TagKey<Item>> tags) {
    public static ItemMatch read(JsonElement json) {
        Set<Item> items = new LinkedHashSet<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        read(json, items, tags);
        if (items.isEmpty() && tags.isEmpty()) throw new IllegalArgumentException("No items in " + json);
        return new ItemMatch(Set.copyOf(items), List.copyOf(tags));
    }

    private static void read(JsonElement json, Set<Item> items, List<TagKey<Item>> tags) {
        if (json.isJsonArray()) {
            for (JsonElement one : json.getAsJsonArray()) read(one, items, tags);
            return;
        }
        String text = json.isJsonObject() ? json.getAsJsonObject().get("id").getAsString() : json.getAsString();
        text = text.strip();
        int star = text.lastIndexOf('*');
        if (star > 0) text = text.substring(0, star).strip();
        if (text.startsWith("#")) {
            tags.add(TagKey.create(Registries.ITEM, new ResourceLocation(text.substring(1))));
            return;
        }
        ResourceLocation id = new ResourceLocation(text);
        items.add(BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IllegalArgumentException("No item " + id)));
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (items.contains(stack.getItem())) return true;
        for (TagKey<Item> tag : tags) if (stack.is(tag)) return true;
        return false;
    }
}
