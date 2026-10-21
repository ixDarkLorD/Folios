package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The item model types, so "folios:guide_book" can be one of them on either loader.
@Mixin(ItemModels.class)
public interface ItemModelsAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> folios$idMapper() {
        throw new AssertionError();
    }
}
