package net.ixdarklord.folios.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;

/**
 * What a reader has done with one book: the entries read, the spreads shown (for reveals that play only on unread
 * pages), the quests done and what has been celebrated, the bookmarks, and where they left off. Kept on the server: with
 * the player ({@code FoliosRegistry.READING}), or on the book's own stack for a book that keeps its own
 * ({@code FoliosRegistry.BOOK_READING}). Books are client resources, so the client works it out and sends it; the server
 * only keeps it. Immutable: a change is a new record.
 *
 * @param read        the entries read, by id
 * @param seen        the spreads shown in full, as {@code "<chapter>|<page>"} (a chapter: {@code home},
 *                    {@code entry:<id>}, {@code category:<id>}...)
 * @param questsDone  the quests whose completion has been told
 * @param celebrated  what has been celebrated: {@code category:<id>}, {@code mastery}
 * @param obtained    the items the player has had that the book's "obtained" locks and objectives ask for, by id
 * @param bookmarks   the places bookmarked, oldest first
 * @param last        the entry page last open
 * @param leftOpen    whether a handheld book was put away lying open
 * @param lastChapter the spread last open when it wasn't an entry's (its chapter key), with {@code lastPage}
 */
public record FoliosRecord(List<String> read, List<String> seen, List<String> questsDone, List<String> celebrated, List<String> obtained, List<Place> bookmarks,
                          Optional<Place> last, boolean leftOpen, Optional<String> lastChapter, int lastPage) {
    public static final FoliosRecord EMPTY = new FoliosRecord(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), false, Optional.empty(), 0);
    /** How many of each list a record keeps; more than any book has, less than a client could send to fill a save. */
    public static final int MAX_EACH = 4096;
    public static final int MAX_BOOKMARKS = 8;

    /** A page of an entry. */
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

    /** The record within the limits: as a client sent it, cut short where it is longer. */
    public FoliosRecord capped() {
        return new FoliosRecord(head(read, MAX_EACH), head(seen, MAX_EACH), head(questsDone, MAX_EACH), head(celebrated, MAX_EACH), head(obtained, MAX_EACH),
                head(bookmarks, MAX_BOOKMARKS), last, leftOpen, lastChapter, Math.max(0, lastPage));
    }

    /** What a book's own stack keeps: its reading, without the player's quests, celebrations and items had. */
    public FoliosRecord forStack() {
        return new FoliosRecord(read, seen, List.of(), List.of(), List.of(), bookmarks, last, leftOpen, lastChapter, lastPage);
    }

    private static <T> List<T> head(List<T> list, int most) {
        return list.size() <= most ? list : list.subList(0, most);
    }
}
