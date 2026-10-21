package net.ixdarklord.folios.internal;

import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.util.List;
import java.util.Optional;

// The guide's packets. Books are client resources, so the server only opens them and answers what the client
// can't know: the recipes a page shows (clients aren't sent recipes) and the advancements that unlock entries.
public final class GuidePayloads {
    /** The book named in a {@link ReadingAt} saying none is open. */
    public static final Identifier NO_BOOK = Folios.rl("none");

    private GuidePayloads() {}

    /** The server opening a book, at an entry or its start. */
    public record Open(Identifier book, Optional<String> entry) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Folios.rl("guide_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Open::book,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional), Open::entry,
                Open::new);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** A client saying its player started or stopped reading the handheld book in a hand (-1 for no hand). */
    public record Reading(boolean reading, int hand) implements CustomPacketPayload {
        public static final Type<Reading> TYPE = new Type<>(Folios.rl("guide_reading"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reading> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Reading::reading,
                ByteBufCodecs.VAR_INT, Reading::hand,
                Reading::new);

        @Override
        public Type<Reading> type() {
            return TYPE;
        }
    }

    /**
     * A client saying which spread of the handheld book its player has open, or that none is ({@code open} false);
     * {@code shut} while it is held up shut, about to be opened.
     */
    public record ReadingAt(boolean open, Identifier book, String chapter, int page, boolean shut) implements CustomPacketPayload {
        public static final Type<ReadingAt> TYPE = new Type<>(Folios.rl("guide_reading_at"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReadingAt> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ReadingAt::open,
                Identifier.STREAM_CODEC, ReadingAt::book,
                ByteBufCodecs.stringUtf8(256), ReadingAt::chapter,
                ByteBufCodecs.VAR_INT, ReadingAt::page,
                ByteBufCodecs.BOOL, ReadingAt::shut,
                ReadingAt::new);

        @Override
        public Type<ReadingAt> type() {
            return TYPE;
        }
    }

    /** The server telling a player which spread someone near them has open in their hands, to read along. */
    public record SharedReading(int reader, ReadingAt at) implements CustomPacketPayload {
        public static final Type<SharedReading> TYPE = new Type<>(Folios.rl("guide_shared_reading"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SharedReading> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SharedReading::reader,
                ReadingAt.STREAM_CODEC, SharedReading::at,
                SharedReading::new);

        @Override
        public Type<SharedReading> type() {
            return TYPE;
        }
    }

    /** A client asking how recipes look. */
    public record RecipeRequest(List<Identifier> recipes) implements CustomPacketPayload {
        public static final Type<RecipeRequest> TYPE = new Type<>(Folios.rl("guide_recipe_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RecipeRequest> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(64)), RecipeRequest::recipes,
                RecipeRequest::new);

        @Override
        public Type<RecipeRequest> type() {
            return TYPE;
        }
    }

    /** How one recipe looks, as the recipe book shows it; empty for a recipe the server doesn't have. */
    public record Recipe(Identifier id, List<RecipeDisplay> displays) implements CustomPacketPayload {
        public static final Type<Recipe> TYPE = new Type<>(Folios.rl("guide_recipe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Recipe> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Recipe::id,
                RecipeDisplay.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Recipe::displays,
                Recipe::new);

        @Override
        public Type<Recipe> type() {
            return TYPE;
        }
    }

    /** The advancements a player has completed: all of them ({@code reset}), or ones newly completed or revoked. */
    public record Advancements(boolean reset, List<Identifier> done, List<Identifier> undone) implements CustomPacketPayload {
        public static final Type<Advancements> TYPE = new Type<>(Folios.rl("guide_advancements"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Advancements> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Advancements::reset,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), Advancements::done,
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), Advancements::undone,
                Advancements::new);

        @Override
        public Type<Advancements> type() {
            return TYPE;
        }
    }
}
