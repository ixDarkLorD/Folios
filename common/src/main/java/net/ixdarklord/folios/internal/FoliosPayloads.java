package net.ixdarklord.folios.internal;

import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public final class FoliosPayloads {
    public static final Identifier NO_BOOK = Folios.rl("none");

    private FoliosPayloads() {}

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

    public record Progress(Identifier book, int hand, FoliosRecord record) implements CustomPacketPayload {
        public static final Type<Progress> TYPE = new Type<>(Folios.rl("guide_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Progress::book,
                ByteBufCodecs.VAR_INT, Progress::hand,
                FoliosRecord.STREAM_CODEC, Progress::record,
                Progress::new);

        @Override
        public Type<Progress> type() {
            return TYPE;
        }
    }

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

    public record Source(Identifier source, List<net.ixdarklord.folios.api.FoliosSources.Row> rows, List<String> removed) implements CustomPacketPayload {
        public static final Type<Source> TYPE = new Type<>(Folios.rl("source"));
        private static final StreamCodec<RegistryFriendlyByteBuf, net.ixdarklord.folios.api.FoliosSources.Row> ROW = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, net.ixdarklord.folios.api.FoliosSources.Row::id,
                ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, java.util.Map<String, String>>map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8,
                        ByteBufCodecs.STRING_UTF8, 64), net.ixdarklord.folios.api.FoliosSources.Row::fields,
                net.ixdarklord.folios.api.FoliosSources.Row::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, Source> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Source::source,
                ROW.apply(ByteBufCodecs.list(4096)), Source::rows,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)), Source::removed,
                Source::new);

        @Override
        public Type<Source> type() {
            return TYPE;
        }
    }

    public record Quests(java.util.Map<Identifier, String> quests) implements CustomPacketPayload {
        public static final Type<Quests> TYPE = new Type<>(Folios.rl("quests"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Quests> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.<RegistryFriendlyByteBuf, Identifier, String, java.util.Map<Identifier, String>>map(java.util.HashMap::new, Identifier.STREAM_CODEC,
                        ByteBufCodecs.STRING_UTF8, 4096), Quests::quests,
                Quests::new);

        @Override
        public Type<Quests> type() {
            return TYPE;
        }
    }

    public record Claim(Identifier quest) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(Folios.rl("claim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Claim> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Claim::quest,
                Claim::new);

        @Override
        public Type<Claim> type() {
            return TYPE;
        }
    }

    public record Viewing(Identifier book, String entry) implements CustomPacketPayload {
        public static final Type<Viewing> TYPE = new Type<>(Folios.rl("viewing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Viewing> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Viewing::book,
                ByteBufCodecs.STRING_UTF8, Viewing::entry,
                Viewing::new);

        @Override
        public Type<Viewing> type() {
            return TYPE;
        }
    }

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

    public record StructureRequest(List<Identifier> structures) implements CustomPacketPayload {
        public static final Type<StructureRequest> TYPE = new Type<>(Folios.rl("guide_structure_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StructureRequest> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(64)), StructureRequest::structures,
                StructureRequest::new);

        @Override
        public Type<StructureRequest> type() {
            return TYPE;
        }
    }

    public static final int MOST_STRUCTURE_BLOCKS = 32768;

    public record Structure(Identifier id, int status, List<BlockState> palette, List<Integer> blocks) implements CustomPacketPayload {
        public static final int FOUND = 0;
        public static final int MISSING = 1;
        public static final int TOO_LARGE = 2;
        public static final Type<Structure> TYPE = new Type<>(Folios.rl("guide_structure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Structure> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Structure::id,
                ByteBufCodecs.VAR_INT, Structure::status,
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).apply(ByteBufCodecs.list(MOST_STRUCTURE_BLOCKS)), Structure::palette,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST_STRUCTURE_BLOCKS * 4)), Structure::blocks,
                Structure::new);

        @Override
        public Type<Structure> type() {
            return TYPE;
        }
    }

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
