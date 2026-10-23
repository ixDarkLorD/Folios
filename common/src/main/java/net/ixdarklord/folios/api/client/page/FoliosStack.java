package net.ixdarklord.folios.api.client.page;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Items as a page names them, worked out only when they're first drawn (item tags need the world's registries, which a
 * book loaded with resources doesn't have yet). In JSON it is one of:
 * <ul>
 *     <li>{@code "minecraft:diamond"}, or {@code "minecraft:diamond*4"} for a count;</li>
 *     <li>{@code "#minecraft:logs"}: every item of a tag, shown in turn;</li>
 *     <li>an item stack object, {@code {"id": "minecraft:potion", "count": 1, "nbt": "{Potion:\"minecraft:healing\"}"}}
 *     ({@code "nbt"} as SNBT text or a JSON object);</li>
 *     <li>a list of any of these, shown in turn.</li>
 * </ul>
 */
public final class FoliosStack {
    public static final Codec<FoliosStack> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> DataResult.success(new FoliosStack(dynamic.convert(JsonOps.INSTANCE).getValue())),
            stack -> new com.mojang.serialization.Dynamic<>(JsonOps.INSTANCE, stack.json));
    /** How long each of several items is shown, in seconds. */
    public static final float CYCLE_SECONDS = 1.25F;

    private final JsonElement json;
    private @Nullable List<ItemStack> stacks;

    private FoliosStack(JsonElement json) {
        this.json = json;
    }

    /** One item. */
    public static FoliosStack of(ResourceLocation item) {
        return new FoliosStack(new com.google.gson.JsonPrimitive(item.toString()));
    }

    /** Every stack it stands for; a missing item is a barrier, so the mistake shows. */
    public List<ItemStack> stacks() {
        if (stacks == null) {
            List<ItemStack> resolved = new ArrayList<>();
            resolve(json, resolved);
            // Before a world is joined, tags and components can't be read yet: try again later.
            if (Minecraft.getInstance().level == null) return resolved.isEmpty() ? List.of(ItemStack.EMPTY) : resolved;
            stacks = resolved.isEmpty() ? List.of(new ItemStack(Items.BARRIER)) : List.copyOf(resolved);
        }
        return stacks;
    }

    /** The stack shown at a time, in seconds: each in turn when there are several. */
    public ItemStack at(float seconds) {
        List<ItemStack> all = stacks();
        return all.get(Mth.positiveModulo((int) (seconds / CYCLE_SECONDS), all.size()));
    }

    public ItemStack first() {
        return stacks().get(0);
    }

    /** Forgets what it resolved to, for after registries or tags change. */
    public void forget() {
        stacks = null;
    }

    private static void resolve(JsonElement json, List<ItemStack> out) {
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) resolve(element, out);
        } else if (json.isJsonPrimitive()) {
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
            if (text.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(text.substring(1));
                if (tag == null) return;
                for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))) out.add(new ItemStack(item, count));
                return;
            }
            ResourceLocation id = ResourceLocation.tryParse(text);
            if (id == null) return;
            int amount = count;
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> out.add(new ItemStack(item, amount)));
        } else if (json.isJsonObject()) {
            com.google.gson.JsonObject object = json.getAsJsonObject();
            ResourceLocation id = ResourceLocation.tryParse(net.minecraft.util.GsonHelper.getAsString(object, "id", ""));
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) return;
            ItemStack stack = new ItemStack(item, net.minecraft.util.GsonHelper.getAsInt(object, "count", 1));
            JsonElement nbt = object.get("nbt");
            if (nbt != null) {
                try {
                    stack.setTag(nbt.isJsonPrimitive() ? net.minecraft.nbt.TagParser.parseTag(nbt.getAsString())
                            : (net.minecraft.nbt.CompoundTag) JsonOps.INSTANCE.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, nbt));
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | ClassCastException e) {
                    return;
                }
            }
            out.add(stack);
        }
    }
}
