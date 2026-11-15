package net.ixdarklord.foliostest;

import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.folios.api.FoliosSources;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosGuides;
import net.ixdarklord.folios.api.client.FoliosLinks;
import net.ixdarklord.folios.api.client.page.FoliosRecipeLayout;
import net.ixdarklord.folios.api.client.page.FoliosRecipeLayouts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.List;

// The demo books' client side: their secret entry (folios/*/entries/secrets/the_end.json) opens with this flag, and
// the handbook's live lists (entries/basics/live_lists.json) observe the pickaxes source.
public final class FoliosTestClient implements ClientModConstructor {
    @Override
    public void onConstructMod() {
        FoliosGuides.setFlag("foliostest:secrets", () -> true);
        FoliosAutotest.init();
        FoliosSources.register(new ResourceLocation("foliostest:pickaxes"), () -> BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_pickaxe"))
                .map(item -> FoliosSources.row(BuiltInRegistries.ITEM.getKey(item).toString())
                        .field("name", item.getDescriptionId()).field("durability", new ItemStack(item).getMaxDamage()).build())
                .toList());
        FoliosRecipeLayouts.register(StonecutterRecipe.class, new FoliosRecipeLayout<>() {
            @Override
            public void lay(StonecutterRecipe recipe, Builder layout) {
                layout.size(96, 44);
                layout.input(0, 4, recipe.getIngredients().get(0));
                layout.flame(5, 26, 60);
                layout.progress(22, 7, 40);
                layout.output(40, 0, recipe.getResultItem(Minecraft.getInstance().level.registryAccess())).tooltip(Component.literal("Cut clean"));
                layout.input(70, 4, List.of(new ItemStack(Items.GRAVEL))).chance(0.25F);
                layout.gauge(90, 0, 6, 26, 0.6F, 0xFFD94A3A, Component.literal("120 FE"));
            }

            @Override
            public void draw(StonecutterRecipe recipe, GuiGraphics graphics, double mouseX, double mouseY) {
                graphics.fill(68, 28, 96, 29, 0xFFB0A090);
                graphics.drawString(Minecraft.getInstance().font, "2.0s", 20, 34, 0xFF8A7A6A, false);
            }

            @Override
            public List<Component> tooltip(StonecutterRecipe recipe, double mouseX, double mouseY) {
                return mouseX >= 22 && mouseX < 36 && mouseY >= 7 && mouseY < 18 ? List.of(Component.literal("Cutting: 2.0s")) : List.of();
            }
        });
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
