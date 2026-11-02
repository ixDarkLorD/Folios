package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.client.handheld.SharedBooks;
import net.ixdarklord.folios.internal.client.screen.BookCache;
import net.minecraft.client.Minecraft;

public final class FoliosClientNetwork {
    private FoliosClientNetwork() {}

    public static void open(FoliosPayloads.Open payload) {
        Minecraft.getInstance().execute(() -> FoliosClient.open(payload.book(), payload.entry().orElse(null), null));
    }

    public static void recipe(FoliosPayloads.Recipe payload) {
        Minecraft.getInstance().execute(() -> {
            FoliosRecipes.receive(payload);
            BookCache.changed();
        });
    }

    public static void structure(FoliosPayloads.Structure payload) {
        Minecraft.getInstance().execute(() -> {
            FoliosStructures.receive(payload);
            BookCache.changed();
        });
    }

    public static void sharedReading(FoliosPayloads.SharedReading payload) {
        Minecraft.getInstance().execute(() -> {
            SharedBooks.receive(payload);
            BookCache.changed();
        });
    }

    public static void advancements(FoliosPayloads.Advancements payload) {
        Minecraft.getInstance().execute(() -> {
            FoliosProgress.receive(payload);
            BookCache.changed();
        });
    }
}
