package net.ixdarklord.folios.internal.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosCategory;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.FoliosObjective;
import net.ixdarklord.folios.api.client.page.FoliosIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.FireworkExplosion;

import java.util.ArrayList;
import java.util.List;

// Celebrates the player finishing part of a book, once each ever (kept with what else was read of it): an objective of
// a quest done, an entry read, every entry of a category read, a quest done, and mastery, every objective of every
// quest done. A toast in the book's look, a sound and sparkles round the player; fireworks in the book's colours for
// mastery alone. Each part plays only as the book's "celebrations" allow, none unless it says so; what is finished is
// marked all the same, so turning them on later plays nothing old. Checked once a second while they play.
public final class Celebrations {
    private Celebrations() {}

    public static void check() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        for (FoliosBook book : FoliosLibrary.books().values()) {
            FoliosMemory memory = FoliosMemory.of(book.id());
            CelebrationSettings settings = FoliosLibrary.celebrations(book.id());
            boolean changed = false;
            List<FoliosObjective> all = new ArrayList<>();
            for (FoliosCategory category : book.visibleCategories()) {
                if (!category.isOpen()) continue;
                int read = 0;
                int total = 0;
                for (FoliosEntry entry : book.entries(category.id())) {
                    all.addAll(entry.objectives());
                    if (!entry.isOpen()) continue;
                    total++;
                    if (!memory.isRead(entry.id())) continue;
                    read++;
                    if (memory.markCelebrated("entry:" + entry.id())) {
                        changed = true;
                        play(minecraft, book, settings, "entry", Component.translatableWithFallback("folios.toast.entry", "Entry read"), entry.title(), entry.icon());
                    }
                }
                if (total > 0 && read == total && memory.markCelebrated("category:" + category.id())) {
                    changed = true;
                    play(minecraft, book, settings, "category", Component.translatableWithFallback("folios.toast.category", "Category complete"),
                            category.title(), category.icon());
                }
            }
            for (FoliosEntry entry : book.entries().values()) {
                if (!entry.isQuest() || !entry.isOpen()) continue;
                List<FoliosObjective> objectives = entry.objectives();
                boolean done = true;
                for (int index = 0; index < objectives.size(); index++) {
                    FoliosObjective objective = objectives.get(index);
                    if (!objective.isDone(book)) {
                        done = false;
                        continue;
                    }
                    if (memory.markCelebrated("objective:" + entry.id() + "#" + index)) {
                        changed = true;
                        play(minecraft, book, settings, "objective", Component.translatableWithFallback("folios.toast.objective", "Objective complete"),
                                objective.label(book), entry.icon());
                    }
                }
                if (done && memory.markQuestDone(entry.id())) {
                    changed = true;
                    play(minecraft, book, settings, "quest", Component.translatableWithFallback("folios.toast.quest", "Quest complete"), entry.title(), entry.icon());
                }
            }
            if (!all.isEmpty() && FoliosObjective.progress(book, all) >= 1 && memory.markCelebrated("mastery")) {
                changed = true;
                play(minecraft, book, settings, "mastery", Component.translatableWithFallback("folios.toast.mastery", "Book mastered"), book.title(), null);
            }
            if (changed) memory.save();
        }
    }

    // One event's celebration, each part as the book allows: the toast (the trophy for mastery, for which there is no
    // icon), a sound, sparkles round the player, and for mastery fireworks.
    private static void play(Minecraft minecraft, FoliosBook book, CelebrationSettings settings, String event, Component heading, Component name,
                             @org.jspecify.annotations.Nullable FoliosIcon icon) {
        boolean mastery = event.equals("mastery");
        if (settings.on(event, "toast")) FoliosToast.show(book, heading, name, icon, mastery);
        if (settings.on(event, "sound")) {
            SoundEvent sound = mastery ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP;
            float pitch = switch (event) {
                case "objective" -> 1.8F;
                case "entry" -> 2.0F;
                case "quest" -> 1.5F;
                case "category" -> 1.2F;
                default -> 1.0F;
            };
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, mastery ? 1.0F : 0.5F));
        }
        LocalPlayer player = minecraft.player;
        if (mastery && settings.on(event, "fireworks")) {
            IntList colours = IntList.of(book.accent(), book.cover(), 0xE8C66A);
            IntList fade = IntList.of(0xFFFFFF);
            FireworkExplosion.Shape[] shapes = {FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};
            for (int burst = 0; burst < 3; burst++) {
                double angle = burst / 3.0 * Math.PI * 2;
                minecraft.level.createFireworks(player.getX() + Math.cos(angle) * 2.5, player.getY() + 3.5 + burst * 0.8, player.getZ() + Math.sin(angle) * 2.5,
                        0, 0, 0, List.of(new FireworkExplosion(shapes[burst], colours, fade, true, burst == 1)));
            }
        }
        if (!settings.on(event, "particles")) return;
        if (mastery) {
            for (int index = 0; index < 30; index++) {
                double angle = index / 30.0 * Math.PI * 2;
                minecraft.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, player.getX() + Math.cos(angle) * 0.6, player.getY() + 1, player.getZ() + Math.sin(angle) * 0.6,
                        Math.cos(angle) * 0.3, 0.35, Math.sin(angle) * 0.3);
            }
            return;
        }
        // Sparkles rising round the player: a full ring for a category or quest, a few for an objective or entry.
        int count = event.equals("category") || event.equals("quest") ? 40 : 14;
        for (int index = 0; index < count; index++) {
            double angle = index / (double) count * Math.PI * 2;
            double radius = 1.3 + (index % 3) * 0.2;
            minecraft.level.addParticle(index % 2 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD,
                    player.getX() + Math.cos(angle) * radius, player.getY() + 0.1 + (index % 5) * 0.2, player.getZ() + Math.sin(angle) * radius,
                    0, 0.05 + (index % 4) * 0.012, 0);
        }
    }
}
