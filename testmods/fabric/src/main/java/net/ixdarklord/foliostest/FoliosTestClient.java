package net.ixdarklord.foliostest;

import net.ixdarklord.folios.api.FoliosSources;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosGuides;
import net.ixdarklord.folios.api.client.FoliosLinks;
import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// The demo books' client side: their secret entry (folios/*/entries/secrets/the_end.json) opens with this flag, and
// the handbook's live lists (entries/basics/live_lists.json) observe the pickaxes source.
public final class FoliosTestClient implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        FoliosGuides.setFlag("foliostest:secrets", () -> true);
        FoliosSources.register(Identifier.parse("foliostest:pickaxes"), () -> BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_pickaxe"))
                .map(item -> FoliosSources.row(BuiltInRegistries.ITEM.getKey(item).toString())
                        .field("name", item.getDescriptionId()).field("durability", new ItemStack(item).getMaxDamage()).build())
                .toList());
        FoliosLinks.register("say", new FoliosLinks.Kind() {
            @Override
            public List<Component> tooltip(FoliosBook book, String target) {
                return List.of(Component.literal("Says: " + target));
            }

            @Override
            public boolean press(FoliosBook book, String target) {
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(Component.literal(target));
                return true;
            }
        });
    }
}
