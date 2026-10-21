package net.ixdarklord.folios.internal.client;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.GuideBooks;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.GuideRegistry;
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

// The "folios:guide_book" item model: the model a stack's book asks for in its book.json ("models"/"item", an item
// model id like "mymod:handbook" for assets/mymod/items/handbook.json), or the fallback for books that don't; and while
// its holder reads it in their hands, as others see them, the book's open model ("models"/"open"), if it names one.
// In inventory slots it is the "flat" icon, unless the book asks for its model there ("models"/"gui": "model"): then
// "gui", the model without what is too small to read at that size (the cover's lettering), or the model itself.
public final class GuideBookItemModel implements ItemModel {
    public static final Identifier TYPE = Folios.rl("guide_book");
    private final ItemModel fallback;
    // The flat icon shown in inventory slots, for books that don't ask for their model there; null for none.
    private final @Nullable ItemModel flat;
    // The model in inventory slots, for books that ask for their model there; null for the model itself.
    private final @Nullable ItemModel gui;
    // A book's model may itself be a guide book model; it is followed only one step.
    private static boolean resolving;

    private GuideBookItemModel(ItemModel fallback, @Nullable ItemModel flat, @Nullable ItemModel gui) {
        this.fallback = fallback;
        this.flat = flat;
        this.gui = gui;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        Identifier id = GuideBooks.bookOf(item);
        GuideBook book = id != null ? GuideLibrary.book(id) : null;
        // Held up open by someone reading it, seen from outside them: the book's open model.
        Identifier model = book != null ? book.itemModel() : null;
        boolean thirdPerson = displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        // Held up by someone, shut or open, and drawn in their hands by SharedBooks: not as the item too.
        if (thirdPerson && owner instanceof net.minecraft.world.entity.Entity holder && net.ixdarklord.folios.internal.client.handheld.SharedBooks.isShown(holder)) return;
        if (item.has(GuideRegistry.READING.get()) && thirdPerson && book != null && book.models().open() != null) model = book.models().open();
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
            return new GuideBookItemModel(fallback.bake(context, transformation), flat.map(model -> model.bake(context, transformation)).orElse(null),
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
