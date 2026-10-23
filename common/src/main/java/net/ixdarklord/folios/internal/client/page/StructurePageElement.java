package net.ixdarklord.folios.internal.client.page;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.GuidePayloads;
import net.ixdarklord.folios.internal.client.GuideStructures;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Structures from data packs (data/<namespace>/structure/<path>.nbt, as structure blocks save them and villages are
// built from), turning in a window: "structure" names one, "structures" several, picked between under the window.
// Clients don't have data packs, so their blocks are asked of the server the first time the page is shown.
public record StructurePageElement(List<Showcase.Shown> structures, Optional<String> title, String text, int height, Showcase.Selector selector) implements FoliosPageElement {
    public static final MapCodec<StructurePageElement> CODEC = RecordCodecBuilder.<StructurePageElement>mapCodec(instance -> instance.group(
            Showcase.Shown.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(StructurePageElement::structures),
            Showcase.Shown.CODEC.optionalFieldOf("structure").forGetter(page -> Optional.empty()),
            Codec.STRING.optionalFieldOf("title").forGetter(StructurePageElement::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(StructurePageElement::text),
            Codec.intRange(40, 130).optionalFieldOf("height", 78).forGetter(StructurePageElement::height),
            Showcase.Selector.CODEC.optionalFieldOf("selector", Showcase.Selector.ARROWS).forGetter(StructurePageElement::selector)
    ).apply(instance, (many, one, title, text, height, selector) -> {
        List<Showcase.Shown> all = new ArrayList<>();
        one.ifPresent(all::add);
        all.addAll(many);
        return new StructurePageElement(List.copyOf(all), title, text, height, selector);
    })).flatXmap(page -> page.structures.isEmpty() ? DataResult.error(() -> "a structure page needs \"structure\" or \"structures\"") : DataResult.success(page), DataResult::success);

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        return Showcase.lay(layout, title, text, height, selector, this.items(layout));
    }

    /** What it shows between its title and its text. */
    Body body(FoliosLayout layout) {
        return Showcase.body(layout, height, selector, this.items(layout));
    }

    private List<Showcase.Item> items(FoliosLayout layout) {
        List<Showcase.Item> items = new ArrayList<>();
        for (Showcase.Shown shown : structures) {
            Component name = shown.name().map(FoliosText::inline).orElse(null);
            ResourceLocation id = ResourceLocation.tryParse(shown.id());
            if (id == null) {
                Showcase.State bad = Showcase.State.saying(Component.translatableWithFallback("folios.structure.missing", "No structure %s", shown.id()));
                items.add(new Showcase.Item(name, () -> bad));
                continue;
            }
            Showcase.State loading = Showcase.State.saying(Component.translatableWithFallback("folios.structure.loading", "Loading the structure…"));
            Showcase.State missing = Showcase.State.saying(Component.translatableWithFallback("folios.structure.missing", "No structure %s", id.toString()));
            Showcase.State large = Showcase.State.saying(Component.translatableWithFallback("folios.structure.too_large", "%s is too large to show", id.toString()));
            items.add(new Showcase.Item(name, () -> {
                GuideStructures.Known known = GuideStructures.get(id);
                if (known == null) return loading;
                return switch (known.status()) {
                    case GuidePayloads.Structure.FOUND -> Showcase.State.of(known.blocks());
                    case GuidePayloads.Structure.TOO_LARGE -> large;
                    default -> missing;
                };
            }));
        }
        return items;
    }

    @Override
    public String searchText() {
        StringBuilder words = new StringBuilder(FoliosText.resolve(title.orElse(""))).append(' ').append(FoliosText.resolve(text));
        structures.forEach(shown -> shown.name().ifPresent(name -> words.append(' ').append(FoliosText.resolve(name))));
        return words.toString();
    }
}
