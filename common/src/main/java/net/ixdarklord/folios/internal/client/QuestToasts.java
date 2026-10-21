package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.GuideEntry;
import net.ixdarklord.folios.api.client.GuideObjective;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

// Tells the player when a quest in one of their books is done, once a second while they play: a toast naming the
// quest and its book. A quest is told of once ever; that it was is kept with what else was read of the book.
public final class QuestToasts {
    private QuestToasts() {}

    public static void check() {
        for (GuideBook book : GuideLibrary.books().values()) {
            GuideMemory memory = null;
            for (GuideEntry entry : book.entries().values()) {
                if (!entry.isQuest() || !entry.isOpen()) continue;
                boolean done = true;
                for (GuideObjective objective : entry.objectives()) {
                    if (!objective.isDone(book)) {
                        done = false;
                        break;
                    }
                }
                if (!done) continue;
                if (memory == null) memory = GuideMemory.of(book.id());
                if (!memory.markQuestDone(entry.id())) continue;
                memory.save();
                // Its own toast, so several done together are each shown.
                SystemToast.add(Minecraft.getInstance().gui.toastManager(), new SystemToast.SystemToastId(),
                        Component.translatableWithFallback("folios.quest.toast", "Quest complete: %s", entry.title()), book.title());
            }
        }
    }
}
