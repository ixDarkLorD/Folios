package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.internal.FoliosPayloads;
import net.minecraft.client.Minecraft;

public final class FoliosClientNetwork {
    private FoliosClientNetwork() {}

    public static void open(FoliosPayloads.Open payload) {
        Minecraft.getInstance().execute(() -> FoliosClient.open(payload.book(), payload.entry().orElse(null), null));
    }

    public static void structure(FoliosPayloads.Structure payload) {
        Minecraft.getInstance().execute(() -> FoliosStructures.receive(payload));
    }

    public static void sharedReading(FoliosPayloads.SharedReading payload) {
        Minecraft.getInstance().execute(() -> net.ixdarklord.folios.internal.client.handheld.SharedBooks.receive(payload));
    }

    public static void advancements(FoliosPayloads.Advancements payload) {
        Minecraft.getInstance().execute(() -> FoliosProgress.receive(payload));
    }
}
