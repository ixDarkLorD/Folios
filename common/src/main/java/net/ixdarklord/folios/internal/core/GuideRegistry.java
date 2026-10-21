package net.ixdarklord.folios.internal.core;

import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.api.registry.RegistryEntry;
import net.ixdarklord.folios.api.GuideBookItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

// The guide book item any book can be given as, and the keys of its NBT: the one naming its book, and the one marking
// it read in hand.
public final class GuideRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Folios.MOD_ID, Registries.ITEM);

    /** The book a guide book item is, by id: {@code {folios_book:"mymod:handbook"}}. */
    public static final String GUIDE_BOOK = "folios_book";
    /**
     * On a guide book while its holder reads it in their hands: others see them hold it up open. Taken off a player's
     * book when they stop (or it leaves their hand); on an armor stand's, it stays, so it can be shown reading.
     */
    public static final String READING = "folios_reading";
    public static final RegistryEntry<GuideBookItem> BOOK_ITEM = ITEMS.register("guide_book",
            () -> new GuideBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private GuideRegistry() {}

    static void register() {
        ITEMS.register();
    }

    public static boolean isReading(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(READING);
    }

    public static void setReading(ItemStack stack, boolean reading) {
        if (reading) stack.getOrCreateTag().putBoolean(READING, true);
        else stack.removeTagKey(READING);
    }
}
