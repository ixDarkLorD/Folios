package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.FoliosSources;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class FoliosBuiltinSources {
    public static final Identifier ITEMS = Folios.rl("items");
    public static final Identifier BLOCKS = Folios.rl("blocks");
    public static final Identifier ENTITIES = Folios.rl("entities");
    public static final Identifier ADVANCEMENTS = Folios.rl("advancements");

    private FoliosBuiltinSources() {}

    public static boolean has(Identifier source) {
        return source.equals(ITEMS) || source.equals(BLOCKS) || source.equals(ENTITIES) || source.equals(ADVANCEMENTS);
    }

    public static @Nullable List<FoliosSources.Row> rows(Identifier source, @Nullable String of) {
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

    private static <T> List<FoliosSources.Row> registry(Registry<T> registry, @Nullable String of, BiConsumer<T, FoliosSources.Builder> fields) {
        List<T> entries = new ArrayList<>();
        if (of != null && of.startsWith("#")) {
            Identifier tag = Identifier.tryParse(of.substring(1));
            if (tag == null) return List.of();
            for (Holder<T> holder : registry.getTagOrEmpty(TagKey.create(registryKey(registry), tag))) entries.add(holder.value());
        } else {
            for (T entry : registry) {
                if (of == null || registry.getKey(entry).getNamespace().equals(of)) entries.add(entry);
            }
        }
        List<FoliosSources.Row> rows = new ArrayList<>();
        for (T entry : entries) {
            Identifier id = registry.getKey(entry);
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

    private static List<FoliosSources.Row> advancements(@Nullable String of) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return List.of();
        List<FoliosSources.Row> rows = new ArrayList<>();
        for (AdvancementNode node : minecraft.getConnection().getAdvancements().getTree().nodes()) {
            Identifier id = node.holder().id();
            if (!under(id, of)) continue;
            DisplayInfo display = node.holder().value().display().orElse(null);
            if (display == null) continue;
            boolean done = FoliosProgress.isDone(id);
            if (display.isHidden() && !done) continue;
            rows.add(FoliosSources.row(id.toString()).field("mod", id.getNamespace()).field("name", text(display.getTitle()))
                    .field("description", text(display.getDescription())).field("done", done).build());
        }
        return rows;
    }

    private static boolean under(Identifier id, @Nullable String of) {
        if (of == null) return true;
        if (!of.contains(":")) return id.getNamespace().equals(of);
        String written = id.toString();
        return written.equals(of) || written.startsWith(of.endsWith("/") ? of : of + "/");
    }

    private static String text(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : component.getString();
    }
}
