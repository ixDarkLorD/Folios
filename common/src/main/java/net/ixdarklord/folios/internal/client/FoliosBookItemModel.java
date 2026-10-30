package net.ixdarklord.folios.internal.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.FoliosBooks;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.FoliosRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class FoliosBookItemModel implements ItemModel {
    public static final Identifier TYPE = Folios.rl("guide_book");
    private final ItemModel fallback;
    private final @Nullable ItemModel flat;
    private final @Nullable ItemModel gui;
    private static boolean resolving;

    private FoliosBookItemModel(ItemModel fallback, @Nullable ItemModel flat, @Nullable ItemModel gui) {
        this.fallback = fallback;
        this.flat = flat;
        this.gui = gui;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        Identifier id = FoliosBooks.bookOf(item);
        FoliosBook book = id != null ? FoliosLibrary.book(id) : null;
        Identifier model = book != null ? book.itemModel() : null;
        boolean thirdPerson = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (thirdPerson && owner instanceof net.minecraft.world.entity.Entity holder && net.ixdarklord.folios.internal.client.handheld.SharedBooks.isShown(holder)) return;
        if (item.has(FoliosRegistry.READING.get()) && thirdPerson && book != null && book.models().open() != null) model = book.models().open();
        if (model != null && !resolving) {
            resolving = true;
            try {
                Minecraft.getInstance().getModelManager().getItemModel(model).update(output, item, resolver, displayContext, level, owner, seed);
            } finally {
                resolving = false;
            }
            return;
        }
        boolean flatInSlots = book == null || !book.models().guiModel();
        if (displayContext == ItemDisplayContext.GUI && flat != null && flatInSlots) flat.update(output, item, resolver, displayContext, level, owner, seed);
        else if (displayContext == ItemDisplayContext.GUI && gui != null) gui.update(output, item, resolver, displayContext, level, owner, seed);
        else fallback.update(output, item, resolver, displayContext, level, owner, seed);
    }

    public record Unbaked(ItemModel.Unbaked fallback, java.util.Optional<ItemModel.Unbaked> flat, java.util.Optional<ItemModel.Unbaked> gui) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemModels.CODEC.fieldOf("fallback").forGetter(Unbaked::fallback),
                ItemModels.CODEC.optionalFieldOf("flat").forGetter(Unbaked::flat),
                ItemModels.CODEC.optionalFieldOf("gui").forGetter(Unbaked::gui)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(BakingContext context, Matrix4fc transformation) {
            return new FoliosBookItemModel(fallback.bake(context, transformation), flat.map(model -> model.bake(context, transformation)).orElse(null),
                    gui.map(model -> model.bake(context, transformation)).orElse(null));
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            fallback.resolveDependencies(resolver);
            flat.ifPresent(model -> model.resolveDependencies(resolver));
            gui.ifPresent(model -> model.resolveDependencies(resolver));
        }
    }
}
