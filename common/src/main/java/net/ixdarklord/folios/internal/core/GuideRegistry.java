package net.ixdarklord.folios.internal.core;

import com.mojang.serialization.Codec;
import net.ixdarklord.coolcatcore.api.attachment.Attachment;
import net.ixdarklord.coolcatcore.api.attachment.AttachmentRegistry;
import net.ixdarklord.coolcatcore.api.attachment.SyncPolicy;
import net.ixdarklord.coolcatcore.api.registry.DeferredRegister;
import net.ixdarklord.coolcatcore.api.registry.RegistryEntry;
import net.ixdarklord.folios.api.FoliosBookItem;
import net.ixdarklord.folios.internal.GuideRecord;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.Map;

// The guide book item any book can be given as, the component naming its book, and the one marking it read in hand;
// and where what is read is kept: with each player, and on the stacks of books that keep their own.
public final class GuideRegistry {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Folios.MOD_ID, Registries.DATA_COMPONENT_TYPE);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Folios.MOD_ID, Registries.ITEM);

    public static final RegistryEntry<DataComponentType<ResourceLocation>> GUIDE_BOOK = COMPONENTS.register("guide_book",
            () -> DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build());
    /**
     * On a guide book while its holder reads it in their hands: others see them hold it up open. Taken off a player's
     * book when they stop (or it leaves their hand); on a mannequin's, it stays, so it can be shown reading.
     */
    public static final RegistryEntry<DataComponentType<Unit>> READING = COMPONENTS.register("guide_reading",
            () -> DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(Unit.INSTANCE)).build());
    private static final AttachmentRegistry ATTACHMENTS = AttachmentRegistry.create(Folios.MOD_ID);
    /** What a player has done with each book (see {@link GuideRecord}): saved with them, sent only to them, kept when they die. */
    public static final Attachment<Map<ResourceLocation, GuideRecord>> PLAYER_PROGRESS = ATTACHMENTS.builder("progress",
                    Codec.unboundedMap(ResourceLocation.CODEC, GuideRecord.CODEC), Map.<ResourceLocation, GuideRecord>of())
            .sync(SyncPolicy.SELF).copyOnDeath().build();
    /** What has been done with this copy of a book that keeps its own ({@code "progress": "book"}): its marks go with it. */
    public static final Attachment<GuideRecord> BOOK_PROGRESS = ATTACHMENTS.builder("book_progress", GuideRecord.CODEC, GuideRecord.EMPTY).build();
    public static final RegistryEntry<FoliosBookItem> BOOK_ITEM = ITEMS.register("guide_book",
            () -> new FoliosBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private GuideRegistry() {}

    static void register() {
        COMPONENTS.register();
        ITEMS.register();
        ATTACHMENTS.register();
    }
}
