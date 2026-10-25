package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.FoliosSources;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The sources Folios gives itself, worked out from the game as a page observing one is laid out, so a data pack can
 * list live game content with no code. {@code "of"} on the page's {@code observe} narrows each:
 * <ul>
 *     <li>{@code folios:items}, {@code folios:blocks}, {@code folios:entities}: a registry's entries, of a tag
 *     ({@code "of": "#minecraft:logs"}) or a mod ({@code "of": "create"}); fields {@code name} (a translation key),
 *     {@code mod}, and for items {@code durability} and {@code stack}, for entities {@code category};</li>
 *     <li>{@code folios:advancements}: the advancements the player has been shown, of a mod or under an id
 *     ({@code "of": "minecraft:story"}); fields {@code name}, {@code description}, and {@code done} ({@code true} or
 *     {@code false}, the reader's own). Hidden ones are left out until done.</li>
 * </ul>
 */
public final class FoliosBuiltinSources {
    public static final ResourceLocation ITEMS = Folios.rl("items");
    public static final ResourceLocation BLOCKS = Folios.rl("blocks");
    public static final ResourceLocation ENTITIES = Folios.rl("entities");
    public static final ResourceLocation ADVANCEMENTS = Folios.rl("advancements");

    private FoliosBuiltinSources() {}

    public static boolean has(ResourceLocation source) {
        return source.equals(ITEMS) || source.equals(BLOCKS) || source.equals(ENTITIES) || source.equals(ADVANCEMENTS);
    }

    /** The source's rows now, narrowed by {@code of} (null for all); null for a source that isn't one of these. */
    public static @Nullable List<FoliosSources.Row> rows(ResourceLocation source, @Nullable String of) {
        if (source.equals(ITEMS)) return registry(BuiltInRegistries.ITEM, of, (item, row) -> {
            ItemStack stack = new ItemStack(item);
            row.field("name", item.getDescriptionId()).field("durability", stack.getMaxDamage()).field("stack", stack.getMaxStackSize());
        });
        if (source.equals(BLOCKS)) return registry(BuiltInRegistries.BLOCK, of, (block, row) -> row.field("name", block.getDescriptionId()));
        if (source.equals(ENTITIES)) return registry(BuiltInRegistries.ENTITY_TYPE, of,
                (type, row) -> row.field("name", type.getDescriptionId()).field("category", type.getCategory().getName()));
        if (source.equals(ADVANCEMENTS)) return advancements(of);
        return null;
    }

    // A registry's entries, of a tag ("#namespace:path") or a mod (a namespace), in the registry's order.
    private static <T> List<FoliosSources.Row> registry(Registry<T> registry, @Nullable String of, BiConsumer<T, FoliosSources.Builder> fields) {
        List<T> entries = new ArrayList<>();
        if (of != null && of.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(of.substring(1));
            if (tag == null) return List.of();
            for (Holder<T> holder : registry.getTagOrEmpty(TagKey.create(registryKey(registry), tag))) entries.add(holder.value());
        } else {
            for (T entry : registry) {
                if (of == null || registry.getKey(entry).getNamespace().equals(of)) entries.add(entry);
            }
        }
        List<FoliosSources.Row> rows = new ArrayList<>();
        for (T entry : entries) {
            ResourceLocation id = registry.getKey(entry);
            FoliosSources.Builder row = FoliosSources.row(id.toString()).field("mod", id.getNamespace());
            fields.accept(entry, row);
            rows.add(row.build());
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceKey<? extends Registry<T>> registryKey(Registry<T> registry) {
        return (ResourceKey<? extends Registry<T>>) registry.key();
    }

    // The advancements the client was sent, of a mod or under an id.
    private static List<FoliosSources.Row> advancements(@Nullable String of) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return List.of();
        List<FoliosSources.Row> rows = new ArrayList<>();
        for (Advancement advancement : minecraft.getConnection().getAdvancements().getAdvancements().getAllAdvancements()) {
            ResourceLocation id = advancement.getId();
            if (!under(id, of)) continue;
            DisplayInfo display = advancement.getDisplay();
            if (display == null) continue;
            boolean done = FoliosProgress.isDone(id);
            if (display.isHidden() && !done) continue;
            rows.add(FoliosSources.row(id.toString()).field("mod", id.getNamespace()).field("name", text(display.getTitle()))
                    .field("description", text(display.getDescription())).field("done", done).build());
        }
        return rows;
    }

    // Of a mod ("minecraft"), or under an id ("minecraft:story" takes minecraft:story and minecraft:story/...).
    private static boolean under(ResourceLocation id, @Nullable String of) {
        if (of == null) return true;
        if (!of.contains(":")) return id.getNamespace().equals(of);
        String written = id.toString();
        return written.equals(of) || written.startsWith(of.endsWith("/") ? of : of + "/");
    }

    // A component's translation key when it is one (so the page translates it), else its text.
    private static String text(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : component.getString();
    }
}
