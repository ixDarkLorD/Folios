package net.ixdarklord.folios.internal.client.page;

import net.ixdarklord.folios.api.client.page.FoliosText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.coolcatcanvas.api.client.viewport.SceneViewport;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Optional;

// A creature in a little window of its own, turning slowly (drag the window to turn it on a screen), named under it.
public record EntityPageElement(ResourceLocation entity, Optional<String> title, String text, float zoom, float spin, float yaw, int height) implements FoliosPageElement {
    public static final MapCodec<EntityPageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(EntityPageElement::entity),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(EntityPageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(EntityPageElement::text),
            Codec.floatRange(0.25F, 4.0F).optionalFieldOf("zoom", 1.0F).forGetter(EntityPageElement::zoom),
            Codec.FLOAT.optionalFieldOf("spin", 24.0F).forGetter(EntityPageElement::spin),
            Codec.FLOAT.optionalFieldOf("yaw", 45.0F).forGetter(EntityPageElement::yaw),
            Codec.intRange(40, 130).optionalFieldOf("height", 74).forGetter(EntityPageElement::height)
    ).apply(instance, EntityPageElement::new));

    /** Ids for the entities pages show, counting down from -1 so they meet no entity in the world. */
    static final java.util.concurrent.atomic.AtomicInteger SHOWN_IDS = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        int contentWidth = layout.contentWidth();
        int height = layout.scaled(this.height);
        SceneViewport view = layout.viewport(contentWidth * FoliosLayout.SCENE_DETAIL, height * FoliosLayout.SCENE_DETAIL);
        // Drawn every frame: a scene drawn fewer times turns in uneven steps.
        view.refreshRate(240);
        // Stood on a ground of grid lines, with its shadow under it.
        view.ground(0, 1, 0x30808080, 0x55A0A0A0, true);
        SceneControl control = new SceneControl(view, yaw, 30, spin);
        Entity[] shown = {null};
        boolean named = title.isEmpty();
        return layout.flow(title.orElse(null), height + (named ? 12 : 0), text, (painter, y) -> {
            FoliosPalette palette = painter.palette();
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).orElse(null);
            if (shown[0] == null && type != null && Minecraft.getInstance().level != null) {
                shown[0] = type.create(Minecraft.getInstance().level);
                if (shown[0] != null) {
                    // Never in the world, but drawn as if it were, which asks its id: one of its own, below any real one.
                    shown[0].setId(SHOWN_IDS.decrementAndGet());
                    PreviewEntities.track(shown[0]);
                    view.entity(shown[0], 0, 0, 0, 0);
                    // Framed to fit the window whole: wide enough for it turned corner-on, and for all its height at the
                    // window's proportions, with a little room round it; zoom then brings it nearer or further.
                    float width = shown[0].getBbWidth() * 1.45F;
                    float tall = shown[0].getBbHeight() * (contentWidth / (float) height);
                    // Small creatures are drawn no larger than a block and a half fills the window: their models (wings,
                    // fins) reach well past their boxes.
                    view.frame(Math.max(Math.max(width, tall) * 1.75F, 2.2F) / zoom, 0, shown[0].getBbHeight() * 0.52, 0);
                }
            }
            int left = FoliosLayout.CONTENT_LEFT;
            Shapes.panel(painter, left, y, left + contentWidth, y + height);
            control.draw(painter, left + 1, y + 1, contentWidth - 2, height - 2);
            if (named) {
                Component name = type != null ? type.getDescription() : Component.literal(entity.toString());
                painter.centeredText(name.copy().withStyle(ChatFormatting.BOLD), painter.pageWidth() / 2, y + height + 4, palette.heading());
            }
        });
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
