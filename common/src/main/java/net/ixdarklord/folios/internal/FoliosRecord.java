package net.ixdarklord.folios.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;

public record FoliosRecord(List<String> read, List<String> seen, List<String> questsDone, List<String> celebrated, List<String> obtained, List<Place> bookmarks,
                          Optional<Place> last, boolean leftOpen, Optional<String> lastChapter, int lastPage) {
    public static final FoliosRecord EMPTY = new FoliosRecord(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), false, Optional.empty(), 0);
    public static final int MAX_EACH = 4096;
    public static final int MAX_BOOKMARKS = 8;

    public record Place(String entry, int page) {
        public static final Codec<Place> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("entry").forGetter(Place::entry),
                Codec.INT.optionalFieldOf("page", 0).forGetter(Place::page)
        ).apply(instance, Place::new));
    }

    public static final Codec<FoliosRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("read", List.of()).forGetter(FoliosRecord::read),
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(FoliosRecord::seen),
            Codec.STRING.listOf().optionalFieldOf("quests_done", List.of()).forGetter(FoliosRecord::questsDone),
            Codec.STRING.listOf().optionalFieldOf("celebrated", List.of()).forGetter(FoliosRecord::celebrated),
            Codec.STRING.listOf().optionalFieldOf("obtained", List.of()).forGetter(FoliosRecord::obtained),
            Place.CODEC.listOf().optionalFieldOf("bookmarks", List.of()).forGetter(FoliosRecord::bookmarks),
            Place.CODEC.optionalFieldOf("last").forGetter(FoliosRecord::last),
            Codec.BOOL.optionalFieldOf("left_open", false).forGetter(FoliosRecord::leftOpen),
            Codec.STRING.optionalFieldOf("last_chapter").forGetter(FoliosRecord::lastChapter),
            Codec.INT.optionalFieldOf("last_page", 0).forGetter(FoliosRecord::lastPage)
    ).apply(instance, FoliosRecord::new));
    public static final StreamCodec<ByteBuf, FoliosRecord> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public FoliosRecord {
        read = List.copyOf(read);
        seen = List.copyOf(seen);
        questsDone = List.copyOf(questsDone);
        celebrated = List.copyOf(celebrated);
        obtained = List.copyOf(obtained);
        bookmarks = List.copyOf(bookmarks);
    }

    public FoliosRecord capped() {
        return new FoliosRecord(head(read, MAX_EACH), head(seen, MAX_EACH), head(questsDone, MAX_EACH), head(celebrated, MAX_EACH), head(obtained, MAX_EACH),
                head(bookmarks, MAX_BOOKMARKS), last, leftOpen, lastChapter, Math.max(0, lastPage));
    }

    public FoliosRecord forStack() {
        return new FoliosRecord(read, seen, List.of(), List.of(), List.of(), bookmarks, last, leftOpen, lastChapter, lastPage);
    }

    private static <T> List<T> head(List<T> list, int most) {
        return list.size() <= most ? list : list.subList(0, most);
    }
}
