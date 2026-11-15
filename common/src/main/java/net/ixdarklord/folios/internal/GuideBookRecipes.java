package net.ixdarklord.folios.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

public final class GuideBookRecipes {
    private GuideBookRecipes() {}

    public static void readTag(JsonObject json, ItemStack result) {
        if (result.hasTag() || !json.has("nbt") || !result.is(FoliosRegistry.BOOK_ITEM.get())) return;
        JsonElement nbt = json.get("nbt");
        try {
            result.setTag(TagParser.parseTag(nbt.isJsonObject() ? nbt.toString() : nbt.getAsString()));
        } catch (CommandSyntaxException e) {
            throw new JsonParseException("Invalid nbt tag: " + e.getMessage());
        }
    }
}
