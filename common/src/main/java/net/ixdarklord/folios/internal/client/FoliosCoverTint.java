package net.ixdarklord.folios.internal.client;

import com.mojang.serialization.MapCodec;
import net.ixdarklord.folios.api.FoliosBooks;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

// The "folios:guide_cover" item tint: a colour of the book a stack names, so one model suits every book. "part" is
// "cover" (the default: the cover's colour) or "accent" (the book's own colour, for a gem or inlay).
public record FoliosCoverTint(boolean accent) implements ItemTintSource {
    public static final Identifier TYPE = Folios.rl("guide_cover");
    public static final MapCodec<FoliosCoverTint> MAP_CODEC = com.mojang.serialization.Codec.STRING.optionalFieldOf("part", "cover")
            .xmap(part -> new FoliosCoverTint(part.equals("accent")), tint -> tint.accent ? "accent" : "cover");
    private static final int FALLBACK = 0xFF3A2E58;
    private static final int FALLBACK_ACCENT = 0xFF8B6CFF;

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier id = FoliosBooks.bookOf(stack);
        FoliosBook book = id != null ? FoliosLibrary.book(id) : null;
        if (book == null) return accent ? FALLBACK_ACCENT : FALLBACK;
        return 0xFF000000 | (accent ? book.accent() : book.cover());
    }

    @Override
    public MapCodec<FoliosCoverTint> type() {
        return MAP_CODEC;
    }
}
