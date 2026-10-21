package net.ixdarklord.folios.api.client.page;

import com.mojang.serialization.MapCodec;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The kinds of page a book's entries can have, by the {@code "type"} their JSON names. A type without a namespace is
 * Folios's: {@code "text"} is {@code folios:text}. Built in:
 * <ul>
 *     <li>{@code text}: {@code title}, {@code text}.</li>
 *     <li>{@code spotlight}: an item shown large ({@code item}, see {@link GuideStack}), {@code title}, {@code text}.</li>
 *     <li>{@code image}: {@code images} (texture paths, shown in turn), {@code title}, {@code text}, {@code border}.</li>
 *     <li>{@code recipe}: {@code recipe} or {@code recipes} (up to two ids), {@code title}, {@code text}; crafting,
 *     smelting, stonecutting and smithing recipes are drawn as such.</li>
 *     <li>{@code entity}: {@code entity} (a type id), {@code scale}, {@code offset}, {@code spin}, {@code title}, {@code text}.</li>
 *     <li>{@code multiblock}: a structure from {@code pattern} (layers top to bottom, each a list of rows) and
 *     {@code mapping} (character to block state), {@code title}, {@code text}.</li>
 *     <li>{@code links}: {@code links} (entry ids, or {@code {"entry": ..., "label": ...}}), {@code title}, {@code text}.</li>
 *     <li>{@code quest}: {@code objectives} (see {@link net.ixdarklord.folios.api.client.GuideObjective}), {@code title},
 *     {@code text}, {@code reward}: a checklist with progress bars, live as the player plays; its entry shows its progress
 *     on its row, and a toast says when it is done.</li>
 *     <li>{@code empty}: nothing, to start what follows on a new spread.</li>
 * </ul>
 * Every page may also have {@code "anchor"} (a name links can reach it by, {@code entry:id#anchor}) and the fields of
 * a {@link net.ixdarklord.folios.api.client.GuideLock}.
 */
public final class GuidePageTypes {
    private static final Map<Identifier, MapCodec<? extends GuidePage>> TYPES = new ConcurrentHashMap<>();

    private GuidePageTypes() {}

    /** Adds a kind of page; register it while the client initialises, before resources load. */
    public static void register(Identifier type, MapCodec<? extends GuidePage> codec) {
        TYPES.put(type, codec);
    }

    public static @Nullable MapCodec<? extends GuidePage> get(Identifier type) {
        return TYPES.get(type);
    }

    public static Set<Identifier> types() {
        return Set.copyOf(TYPES.keySet());
    }

    /** A page type as written: a bare name is CoolCatLib's. */
    public static Identifier id(String written) {
        return written.indexOf(':') >= 0 ? Identifier.parse(written) : Folios.rl(written);
    }
}
