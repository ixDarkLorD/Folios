package net.ixdarklord.folios.api.client.page;

import com.mojang.serialization.MapCodec;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The types of element a page shows, by the {@code "type"} their JSON names. A type without a namespace is Folios's:
 * {@code "text"} is {@code folios:text}.
 * <p>
 * A page is a {@code title} and its {@code "elements"}, set one under another, each with its own {@code title} and
 * {@code text} if it has them (a plain string is a text element); one that doesn't fit in what is left of the page
 * starts the next, and text runs on. A page written as one element ({@code {"type": "recipe", ...}}) is a page of that
 * element alone, and a page's own {@code "text"} comes before its elements:
 * <pre>{@code
 * {"title": "Torches", "elements": ["Light the way:", {"type": "recipe", "recipe": "minecraft:torch"}, {"type": "build", "build": "tower"}]}
 * }</pre>
 * Built in:
 * <ul>
 *     <li>{@code text}: {@code title}, {@code text}; as a page, {@code elements}.</li>
 *     <li>{@code spotlight}: an item shown large ({@code item}, see {@link GuideStack}), {@code title}, {@code text}.</li>
 *     <li>{@code image}: {@code images} (texture paths, shown in turn), {@code title}, {@code text}, {@code border}.</li>
 *     <li>{@code recipe}: {@code recipe} or {@code recipes} (up to two ids), {@code title}, {@code text}; crafting,
 *     smelting, stonecutting and smithing recipes are drawn as such.</li>
 *     <li>{@code entity}: {@code entity} (a type id), {@code scale}, {@code offset}, {@code spin}, {@code title}, {@code text}.</li>
 *     <li>{@code multiblock}: a structure from {@code pattern} (layers top to bottom, each a list of rows) and
 *     {@code mapping} (character to block state, or to a tag of blocks any of which will do, {@code "#minecraft:logs"},
 *     shown in turn), {@code title}, {@code text}. Each block is counted by the items it takes: a door's or a bed's
 *     second half comes with the first, and a double slab is two.</li>
 *     <li>{@code structure}: structures from data packs ({@code data/<namespace>/structures/<path>.nbt}), asked of the
 *     server: {@code structure} or {@code structures} (ids, or {@code {"id": ..., "name": ...}}), {@code selector}
 *     ({@code "arrows"}, the default, or {@code "numbers"}) to pick between several, {@code height}, {@code title},
 *     {@code text}. Shown to look at: no list of blocks, nor a clasp to build it in the world.</li>
 *     <li>{@code build}: builds designed in the book's own files ({@code <book>/builds/<name>.json}: a {@code name},
 *     a {@code pattern} and {@code mapping} as a multiblock page's, and {@code blocks}, each
 *     {@code {"block": ..., "pos": [x, y, z]}} or a box {@code {"block": ..., "from": [...], "to": [...], "hollow": true}},
 *     in order, air clearing): {@code build} or {@code builds} (a bare name for this book's, {@code namespace:path}
 *     for any book's file under {@code folios/}), and the rest as a structure page.</li>
 *     <li>{@code links}: {@code links} (entry ids, or {@code {"entry": ..., "label": ...}}), {@code title}, {@code text}.</li>
 *     <li>{@code quest}: {@code objectives} (see {@link net.ixdarklord.folios.api.client.GuideObjective}), {@code title},
 *     {@code text}, {@code reward}: a checklist with progress bars, live as the player plays; its entry shows its progress
 *     on its row, and a toast says when it is done.</li>
 * </ul>
 * And two that are pages of their own, never elements: {@code spread} (one thing over both pages of the open book) and
 * {@code empty} (nothing, to start what follows on a new spread). An element type of a mod's own can be in a page's
 * elements when it lays itself out through {@link GuideLayout#flow}.
 * Every page may also have {@code "icon"} (shown before its title; an entry's first page shows the entry's icon unless
 * it names its own or {@code "none"}), {@code "anchor"} (a name links can reach it by, {@code entry:id#anchor}) and the fields of
 * a {@link net.ixdarklord.folios.api.client.GuideLock}.
 */
public final class GuidePageTypes {
    private static final Map<ResourceLocation, MapCodec<? extends GuidePage>> TYPES = new ConcurrentHashMap<>();

    private GuidePageTypes() {}

    /** Adds a type of element (or of page); register it while the client initialises, before resources load. */
    public static void register(ResourceLocation type, MapCodec<? extends GuidePage> codec) {
        TYPES.put(type, codec);
    }

    public static @Nullable MapCodec<? extends GuidePage> get(ResourceLocation type) {
        return TYPES.get(type);
    }

    public static Set<ResourceLocation> types() {
        return Set.copyOf(TYPES.keySet());
    }

    /** A page type as written: a bare name is CoolCatLib's. */
    public static ResourceLocation id(String written) {
        return written.indexOf(':') >= 0 ? new ResourceLocation(written) : Folios.rl(written);
    }
}
