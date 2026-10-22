package net.ixdarklord.folios.internal.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.coolcatcore.api.client.registry.ExtraModels;
import net.ixdarklord.folios.api.GuideBooks;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.internal.core.GuideRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// The guide book item, drawn in code (its item model is "builtin/entity"): the model a stack's book asks for in its
// book.json ("models"/"item", a model id like "mymod:handbook" for assets/mymod/models/item/handbook.json), or Folios's
// own; and while its holder reads it in their hands, as others see them, the book's open model ("models"/"open"), if
// it names one. In inventory slots it is the flat icon, unless the book asks for its model there ("models"/"gui": "model").
// Each model is drawn with its own display transform, its tinted faces in the book's colours (tint 0 the cover, 1 the
// book's own colour), and Folios's own 3D model with the book's name lettered on its cover.
public final class GuideBookItemModel {
    private static final ResourceLocation MODEL = Folios.rl("item/guide_book_3d");
    private static final ResourceLocation FLAT = Folios.rl("item/guide_book_flat");
    private static final int FALLBACK = 0xFF3A2E58;
    private static final int FALLBACK_ACCENT = 0xFF8B6CFF;
    private static final RandomSource RANDOM = RandomSource.create();

    private GuideBookItemModel() {}

    /** Folios's models, and those books name, to bake with the game's. */
    public static void registerModels() {
        ExtraModels.register(GuideBookItemModel::models);
    }

    // The models books name, read from their book.json files as the resources load.
    private static Collection<ResourceLocation> models(ResourceManager resources) {
        Set<ResourceLocation> models = new LinkedHashSet<>(List.of(MODEL, FLAT));
        resources.listResources("folios", id -> id.getPath().endsWith("/book.json")).forEach((file, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject named = json.has("models") ? json.getAsJsonObject("models") : new JsonObject();
                for (String key : new String[]{"item", "open"}) {
                    if (named.has(key)) models.add(itemModel(ResourceLocation.parse(named.get(key).getAsString())));
                }
                if (json.has("item_model")) models.add(itemModel(ResourceLocation.parse(json.get("item_model").getAsString())));
            } catch (Exception e) {
                Folios.LOGGER.debug("Couldn't read the models of {}", file, e);
            }
        });
        return models;
    }

    // A book's item model is named as on later versions ("mymod:handbook"): here it is the model "mymod:item/handbook".
    private static ResourceLocation itemModel(ResourceLocation id) {
        return id.getPath().startsWith("item/") ? id : id.withPrefix("item/");
    }

    public static void render(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ResourceLocation id = GuideBooks.bookOf(stack);
        GuideBook book = id != null ? GuideLibrary.book(id) : null;
        boolean thirdPerson = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        ResourceLocation model = book != null && book.itemModel() != null ? itemModel(book.itemModel()) : null;
        // Held up open by someone reading it, seen from outside them: the book's open model.
        if (stack.has(GuideRegistry.READING.get()) && thirdPerson && book != null && book.models().open() != null) model = itemModel(book.models().open());
        boolean lettered = false;
        if (model == null) {
            boolean flatInSlots = book == null || !book.models().guiModel();
            if (context == ItemDisplayContext.GUI && flatInSlots) {
                model = FLAT;
            } else {
                model = MODEL;
                lettered = true;
            }
        }
        int cover = tint(stack, false);
        // The flat icon's second layer is its trim, left untinted.
        int accent = model == FLAT ? -1 : tint(stack, true);
        BakedModel baked = ExtraModels.get(model);
        boolean leftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        baked.getTransforms().getTransform(context).apply(leftHand, pose);
        pose.translate(-0.5F, -0.5F, -0.5F);
        VertexConsumer consumer = buffers.getBuffer(Sheets.translucentItemSheet());
        for (Direction side : Direction.values()) quads(pose, consumer, baked.getQuads(null, side, RANDOM), cover, accent, light, overlay);
        quads(pose, consumer, baked.getQuads(null, null, RANDOM), cover, accent, light, overlay);
        if (lettered && id != null) GuideCoverText.DEFAULT.submit(id, pose, SubmitNodeCollector.of(buffers), light);
        pose.popPose();
    }

    private static void quads(PoseStack pose, VertexConsumer consumer, List<BakedQuad> quads, int cover, int accent, int light, int overlay) {
        for (BakedQuad quad : quads) {
            int color = !quad.isTinted() ? -1 : quad.getTintIndex() == 0 ? cover : quad.getTintIndex() == 1 ? accent : -1;
            consumer.putBulkData(pose.last(), quad, (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F, light, overlay);
        }
    }

    /** A colour of the book a stack names: its cover's, or its own (for a gem or inlay). */
    public static int tint(ItemStack stack, boolean accentPart) {
        ResourceLocation id = GuideBooks.bookOf(stack);
        GuideBook book = id != null ? GuideLibrary.book(id) : null;
        if (book == null) return accentPart ? FALLBACK_ACCENT : FALLBACK;
        return 0xFF000000 | (accentPart ? book.accent() : book.cover());
    }
}
