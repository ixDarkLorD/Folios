package net.ixdarklord.folios.internal.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.GuideCategory;
import net.ixdarklord.folios.api.client.GuideEntry;
import net.ixdarklord.folios.api.client.GuideObjective;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.FireworkExplosion;

import java.util.ArrayList;
import java.util.List;

// Celebrates the player finishing part of a book, once each ever (kept with what else was read of it): every entry of
// a category read, and mastery, every objective of every quest done. A toast, a sound, and a show round the player:
// sparkles for a category, fireworks in the book's colours for mastery. Checked once a second while they play.
public final class Celebrations {
    private Celebrations() {}

    public static void check() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        for (GuideBook book : GuideLibrary.books().values()) {
            GuideMemory memory = GuideMemory.of(book.id());
            boolean changed = false;
            List<GuideObjective> all = new ArrayList<>();
            for (GuideCategory category : book.visibleCategories()) {
                if (!category.isOpen()) continue;
                int read = 0;
                int total = 0;
                for (GuideEntry entry : book.entries(category.id())) {
                    all.addAll(entry.objectives());
                    if (!entry.isOpen()) continue;
                    total++;
                    if (memory.isRead(entry.id())) read++;
                }
                if (total > 0 && read == total && memory.markCelebrated("category:" + category.id())) {
                    changed = true;
                    category(minecraft, book, category);
                }
            }
            if (!all.isEmpty() && GuideObjective.progress(book, all) >= 1 && memory.markCelebrated("mastery")) {
                changed = true;
                mastery(minecraft, book);
            }
            if (changed) memory.save();
        }
    }

    // A category read through: a toast, a chime, sparkles rising round the player.
    private static void category(Minecraft minecraft, GuideBook book, GuideCategory category) {
        SystemToast.add(minecraft.gui.toastManager(), new SystemToast.SystemToastId(),
                Component.translatableWithFallback("folios.celebrate.category", "Category complete: %s", category.title()), book.title());
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.6F));
        LocalPlayer player = minecraft.player;
        for (int index = 0; index < 40; index++) {
            double angle = index / 40.0 * Math.PI * 2;
            double radius = 1.3 + (index % 3) * 0.2;
            minecraft.level.addParticle(index % 2 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD,
                    player.getX() + Math.cos(angle) * radius, player.getY() + 0.1 + (index % 5) * 0.2, player.getZ() + Math.sin(angle) * radius,
                    0, 0.05 + (index % 4) * 0.012, 0);
        }
    }

    // The whole book mastered: a toast, the challenge fanfare, and fireworks in the book's colours over the player.
    private static void mastery(Minecraft minecraft, GuideBook book) {
        SystemToast.add(minecraft.gui.toastManager(), new SystemToast.SystemToastId(),
                Component.translatableWithFallback("folios.celebrate.mastery", "Mastered: %s", book.title()),
                Component.translatableWithFallback("folios.celebrate.mastery.detail", "Every quest in the book is done"));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F));
        LocalPlayer player = minecraft.player;
        IntList colours = IntList.of(book.accent(), book.cover(), 0xE8C66A);
        IntList fade = IntList.of(0xFFFFFF);
        FireworkExplosion.Shape[] shapes = {FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};
        for (int burst = 0; burst < 3; burst++) {
            double angle = burst / 3.0 * Math.PI * 2;
            minecraft.level.createFireworks(player.getX() + Math.cos(angle) * 2.5, player.getY() + 3.5 + burst * 0.8, player.getZ() + Math.sin(angle) * 2.5,
                    0, 0, 0, List.of(new FireworkExplosion(shapes[burst], colours, fade, true, burst == 1)), true);
        }
        for (int index = 0; index < 30; index++) {
            double angle = index / 30.0 * Math.PI * 2;
            minecraft.level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, player.getX() + Math.cos(angle) * 0.6, player.getY() + 1, player.getZ() + Math.sin(angle) * 0.6,
                    Math.cos(angle) * 0.3, 0.35, Math.sin(angle) * 0.3);
        }
    }
}
