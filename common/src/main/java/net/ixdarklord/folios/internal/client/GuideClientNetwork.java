package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.internal.GuidePayloads;
import net.minecraft.client.Minecraft;

// The client's side of the guide's packets, run on the main thread.
public final class GuideClientNetwork {
    private GuideClientNetwork() {}

    public static void open(GuidePayloads.Open payload) {
        Minecraft.getInstance().execute(() -> GuideClient.open(payload.book(), payload.entry().orElse(null), null));
    }


    public static void sharedReading(GuidePayloads.SharedReading payload) {
        Minecraft.getInstance().execute(() -> net.ixdarklord.folios.internal.client.handheld.SharedBooks.receive(payload));
    }

    public static void advancements(GuidePayloads.Advancements payload) {
        Minecraft.getInstance().execute(() -> GuideProgress.receive(payload));
    }
}
