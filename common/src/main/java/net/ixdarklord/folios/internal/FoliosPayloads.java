package net.ixdarklord.folios.internal;

import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

// The guide's packets. Books are client resources, so the server only opens them and answers what the client
// can't know: the structures a page shows (data packs' own) and the advancements that unlock entries.
public final class FoliosPayloads {
    /** The book named in a {@link ReadingAt} saying none is open. */
    public static final ResourceLocation NO_BOOK = Folios.rl("none");

    private FoliosPayloads() {}

    /** The server opening a book, at an entry or its start. */
    public record Open(ResourceLocation book, Optional<String> entry) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Folios.rl("guide_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Open::book,
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
    public record ReadingAt(boolean open, ResourceLocation book, String chapter, int page, boolean shut) implements CustomPacketPayload {
        public static final Type<ReadingAt> TYPE = new Type<>(Folios.rl("guide_reading_at"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReadingAt> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ReadingAt::open,
                ResourceLocation.STREAM_CODEC, ReadingAt::book,
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

    /**
     * A client sending what its player has done with a book, to be kept: with the player ({@code hand} -1), or on the
     * book in that hand, for a book that keeps its own.
     */
    public record Progress(ResourceLocation book, int hand, FoliosRecord record) implements CustomPacketPayload {
        public static final Type<Progress> TYPE = new Type<>(Folios.rl("guide_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Progress::book,
                ByteBufCodecs.VAR_INT, Progress::hand,
                FoliosRecord.STREAM_CODEC, Progress::record,
                Progress::new);

        @Override
        public Type<Progress> type() {
            return TYPE;
        }
    }

    /** A source's rows (FoliosSources), from the server, for the pages observing it, and the ids data packs took out. */
    public record Source(ResourceLocation source, List<net.ixdarklord.folios.api.FoliosSources.Row> rows, List<String> removed) implements CustomPacketPayload {
        public static final Type<Source> TYPE = new Type<>(Folios.rl("source"));
        private static final StreamCodec<RegistryFriendlyByteBuf, net.ixdarklord.folios.api.FoliosSources.Row> ROW = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, net.ixdarklord.folios.api.FoliosSources.Row::id,
                ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, java.util.Map<String, String>>map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8,
                        ByteBufCodecs.STRING_UTF8, 64), net.ixdarklord.folios.api.FoliosSources.Row::fields,
                net.ixdarklord.folios.api.FoliosSources.Row::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, Source> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Source::source,
                ROW.apply(ByteBufCodecs.list(4096)), Source::rows,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)), Source::removed,
                Source::new);

        @Override
        public Type<Source> type() {
            return TYPE;
        }
    }

    /** The server's quests (data packs' {@code folios/quests}), each as its file is written, sent to a player. */
    public record Quests(java.util.Map<ResourceLocation, String> quests) implements CustomPacketPayload {
        public static final Type<Quests> TYPE = new Type<>(Folios.rl("quests"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Quests> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceLocation, String, java.util.Map<ResourceLocation, String>>map(java.util.HashMap::new, ResourceLocation.STREAM_CODEC,
                        ByteBufCodecs.STRING_UTF8, 4096), Quests::quests,
                Quests::new);

        @Override
        public Type<Quests> type() {
            return TYPE;
        }
    }

    /** A client claiming the reward of a server quest its player has done. */
    public record Claim(ResourceLocation quest) implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(Folios.rl("claim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Claim> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Claim::quest,
                Claim::new);

        @Override
        public Type<Claim> type() {
            return TYPE;
        }
    }

    /**
     * A client saying which entry of a book is on its player's screen, as that changes; {@link #NO_BOOK} once none is.
     * The server counts an entry read once it has been open long enough (QuestReading).
     */
    public record Viewing(ResourceLocation book, String entry) implements CustomPacketPayload {
        public static final Type<Viewing> TYPE = new Type<>(Folios.rl("viewing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Viewing> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Viewing::book,
                ByteBufCodecs.STRING_UTF8, Viewing::entry,
                Viewing::new);

        @Override
        public Type<Viewing> type() {
            return TYPE;
        }
    }

    /** A client asking for structures' blocks. */
    public record StructureRequest(List<ResourceLocation> structures) implements CustomPacketPayload {
        public static final Type<StructureRequest> TYPE = new Type<>(Folios.rl("guide_structure_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StructureRequest> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(64)), StructureRequest::structures,
                StructureRequest::new);

        @Override
        public Type<StructureRequest> type() {
            return TYPE;
        }
    }

    /** No more blocks than this of a structure are sent; a larger one is said to be too large. */
    public static final int MOST_STRUCTURE_BLOCKS = 32768;

    /**
     * A structure's blocks: its {@code palette} of states, and each block as four numbers in {@code blocks}, its x, y and
     * z and its state's place in the palette. {@code status} is {@link #FOUND}, {@link #MISSING} or {@link #TOO_LARGE}.
     */
    public record Structure(ResourceLocation id, int status, List<BlockState> palette, List<Integer> blocks) implements CustomPacketPayload {
        public static final int FOUND = 0;
        public static final int MISSING = 1;
        public static final int TOO_LARGE = 2;
        public static final Type<Structure> TYPE = new Type<>(Folios.rl("guide_structure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Structure> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Structure::id,
                ByteBufCodecs.VAR_INT, Structure::status,
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).apply(ByteBufCodecs.list(MOST_STRUCTURE_BLOCKS)), Structure::palette,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST_STRUCTURE_BLOCKS * 4)), Structure::blocks,
                Structure::new);

        @Override
        public Type<Structure> type() {
            return TYPE;
        }
    }

    /** The advancements a player has completed: all of them ({@code reset}), or ones newly completed or revoked. */
    public record Advancements(boolean reset, List<ResourceLocation> done, List<ResourceLocation> undone) implements CustomPacketPayload {
        public static final Type<Advancements> TYPE = new Type<>(Folios.rl("guide_advancements"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Advancements> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Advancements::reset,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Advancements::done,
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), Advancements::undone,
                Advancements::new);

        @Override
        public Type<Advancements> type() {
            return TYPE;
        }
    }
}
