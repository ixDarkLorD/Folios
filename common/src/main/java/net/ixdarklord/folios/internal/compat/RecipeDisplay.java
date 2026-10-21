package net.ixdarklord.folios.internal.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

// Minecraft 26.1's recipe displays, made on 1.21.1 from the recipes the client already has: what a recipe page shows
// of each kind of recipe, every slot as the stacks it takes in turn.
public sealed interface RecipeDisplay {
    SlotDisplay result();

    SlotDisplay craftingStation();

    /** A recipe's display, or null for a kind of recipe pages don't show. */
    static @Nullable RecipeDisplay of(Recipe<?> recipe, RegistryAccess registries) {
        SlotDisplay result = SlotDisplay.of(recipe.getResultItem(registries));
        return switch (recipe) {
            case ShapedRecipe shaped -> new ShapedCraftingRecipeDisplay(shaped.getWidth(), shaped.getHeight(),
                    shaped.getIngredients().stream().map(SlotDisplay::of).toList(), result, SlotDisplay.of(Blocks.CRAFTING_TABLE.asItem()));
            case ShapelessRecipe shapeless -> new ShapelessCraftingRecipeDisplay(shapeless.getIngredients().stream().map(SlotDisplay::of).toList(), result,
                    SlotDisplay.of(Blocks.CRAFTING_TABLE.asItem()));
            case AbstractCookingRecipe cooking -> new FurnaceRecipeDisplay(SlotDisplay.of(cooking.getIngredients().getFirst()), SlotDisplay.fuel(), result,
                    SlotDisplay.of(station(cooking.getType())), cooking.getCookingTime());
            case StonecutterRecipe cutting -> new StonecutterRecipeDisplay(SlotDisplay.of(cutting.getIngredients().getFirst()), result,
                    SlotDisplay.of(Blocks.STONECUTTER.asItem()));
            case SmithingRecipe smithing -> {
                SlotDisplay base = SlotDisplay.matching(smithing::isBaseIngredient);
                yield new SmithingRecipeDisplay(SlotDisplay.matching(smithing::isTemplateIngredient), base, SlotDisplay.matching(smithing::isAdditionIngredient),
                        smithing instanceof SmithingTransformRecipe ? result : base, SlotDisplay.of(Blocks.SMITHING_TABLE.asItem()));
            }
            default -> null;
        };
    }

    private static Item station(RecipeType<?> type) {
        if (type == RecipeType.BLASTING) return Blocks.BLAST_FURNACE.asItem();
        if (type == RecipeType.SMOKING) return Blocks.SMOKER.asItem();
        if (type == RecipeType.CAMPFIRE_COOKING) return Blocks.CAMPFIRE.asItem();
        return Blocks.FURNACE.asItem();
    }

    /** A slot: the stacks it shows in turn. */
    record SlotDisplay(List<ItemStack> stacks) {
        public static final SlotDisplay EMPTY = new SlotDisplay(List.of());

        static SlotDisplay of(ItemStack stack) {
            return stack.isEmpty() ? EMPTY : new SlotDisplay(List.of(stack));
        }

        static SlotDisplay of(Item item) {
            return of(new ItemStack(item));
        }

        static SlotDisplay of(Ingredient ingredient) {
            return ingredient.isEmpty() ? EMPTY : new SlotDisplay(Arrays.asList(ingredient.getItems()));
        }

        // Every item that burns, as a furnace's fuel slot takes.
        static SlotDisplay fuel() {
            return new SlotDisplay(AbstractFurnaceBlockEntity.getFuel().keySet().stream().map(ItemStack::new).toList());
        }

        // Every item a test takes (smithing recipes only answer whether a stack fits).
        static SlotDisplay matching(Predicate<ItemStack> test) {
            List<ItemStack> stacks = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty() && test.test(stack)) stacks.add(stack);
            }
            return new SlotDisplay(List.copyOf(stacks));
        }
    }

    record ShapedCraftingRecipeDisplay(int width, int height, List<SlotDisplay> ingredients, SlotDisplay result, SlotDisplay craftingStation) implements RecipeDisplay {}

    record ShapelessCraftingRecipeDisplay(List<SlotDisplay> ingredients, SlotDisplay result, SlotDisplay craftingStation) implements RecipeDisplay {}

    record FurnaceRecipeDisplay(SlotDisplay ingredient, SlotDisplay fuel, SlotDisplay result, SlotDisplay craftingStation, int duration) implements RecipeDisplay {}

    record StonecutterRecipeDisplay(SlotDisplay input, SlotDisplay result, SlotDisplay craftingStation) implements RecipeDisplay {}

    record SmithingRecipeDisplay(SlotDisplay template, SlotDisplay base, SlotDisplay addition, SlotDisplay result, SlotDisplay craftingStation) implements RecipeDisplay {}
}
