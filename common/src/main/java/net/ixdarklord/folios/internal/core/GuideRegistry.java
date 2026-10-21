package net.ixdarklord.folios.internal.core;

import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.api.registry.RegistryEntry;
import net.ixdarklord.folios.api.GuideBookItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

// The guide book item any book can be given as, the component naming its book, and the one marking it read in hand.
public final class GuideRegistry {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Folios.MOD_ID, Registries.DATA_COMPONENT_TYPE);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Folios.MOD_ID, Registries.ITEM);

    public static final RegistryEntry<DataComponentType<Identifier>> GUIDE_BOOK = COMPONENTS.register("guide_book",
            () -> DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());
    /**
     * On a guide book while its holder reads it in their hands: others see them hold it up open. Taken off a player's
     * book when they stop (or it leaves their hand); on a mannequin's, it stays, so it can be shown reading.
     */
    public static final RegistryEntry<DataComponentType<Unit>> READING = COMPONENTS.register("guide_reading",
            () -> DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).ignoreSwapAnimation().build());
    public static final RegistryEntry<GuideBookItem> BOOK_ITEM = ITEMS.register("guide_book",
            () -> new GuideBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
                    .setId(ResourceKey.create(Registries.ITEM, Folios.rl("guide_book")))));

    private GuideRegistry() {}

    static void register() {
        COMPONENTS.register();
        ITEMS.register();
    }
}
