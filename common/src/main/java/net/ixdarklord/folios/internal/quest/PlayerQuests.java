package net.ixdarklord.folios.internal.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PlayerQuests(Map<Identifier, Progress> quests, List<Identifier> obtained, Map<Identifier, List<String>> read, List<String> flags) {
    public static final PlayerQuests EMPTY = new PlayerQuests(Map.of(), List.of(), Map.of(), List.of());

    public record Progress(List<Float> objectives, boolean unlocked, boolean done, boolean claimed) {
        public static final Progress NONE = new Progress(List.of(), false, false, false);
        public static final Codec<Progress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.listOf().optionalFieldOf("objectives", List.of()).forGetter(Progress::objectives),
                Codec.BOOL.optionalFieldOf("unlocked", false).forGetter(Progress::unlocked),
                Codec.BOOL.optionalFieldOf("done", false).forGetter(Progress::done),
                Codec.BOOL.optionalFieldOf("claimed", false).forGetter(Progress::claimed)
        ).apply(instance, Progress::new));

        public Progress {
            objectives = List.copyOf(objectives);
        }

        public boolean isOpen() {
            return unlocked || done;
        }
    }

    public static final Codec<PlayerQuests> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Progress.CODEC).optionalFieldOf("quests", Map.of()).forGetter(PlayerQuests::quests),
            Identifier.CODEC.listOf().optionalFieldOf("obtained", List.of()).forGetter(PlayerQuests::obtained),
            Codec.unboundedMap(Identifier.CODEC, Codec.STRING.listOf()).optionalFieldOf("read", Map.of()).forGetter(PlayerQuests::read),
            Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(PlayerQuests::flags)
    ).apply(instance, PlayerQuests::new));

    public PlayerQuests {
        quests = Map.copyOf(quests);
        obtained = List.copyOf(obtained);
        read = Map.copyOf(read);
        flags = List.copyOf(flags);
    }

    public Progress progress(Identifier quest) {
        return quests.getOrDefault(quest, Progress.NONE);
    }

    public PlayerQuests with(Identifier quest, Progress progress) {
        Map<Identifier, Progress> next = new HashMap<>(quests);
        if (progress.equals(Progress.NONE)) next.remove(quest);
        else next.put(quest, progress);
        return new PlayerQuests(next, obtained, read, flags);
    }

    public PlayerQuests withObtained(Set<Identifier> items) {
        Set<Identifier> all = new LinkedHashSet<>(obtained);
        return all.addAll(items) ? new PlayerQuests(quests, List.copyOf(all), read, flags) : this;
    }

    public boolean isRead(Identifier book, String entry) {
        return read.getOrDefault(book, List.of()).contains(entry);
    }

    public PlayerQuests withRead(Identifier book, String entry) {
        if (this.isRead(book, entry)) return this;
        Map<Identifier, List<String>> next = new HashMap<>(read);
        Set<String> entries = new LinkedHashSet<>(read.getOrDefault(book, List.of()));
        entries.add(entry);
        next.put(book, List.copyOf(entries));
        return new PlayerQuests(quests, obtained, next, flags);
    }

    public PlayerQuests withFlag(String flag, boolean on) {
        Set<String> next = new LinkedHashSet<>(flags);
        boolean changed = on ? next.add(flag) : next.remove(flag);
        return changed ? new PlayerQuests(quests, obtained, read, List.copyOf(next)) : this;
    }
}
