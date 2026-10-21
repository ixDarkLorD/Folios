package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.internal.GuidePayloads;
import net.ixdarklord.folios.internal.mixin.client.ClientAdvancementsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

// What has unlocked the player's books: the advancements the server says they have, and flags code sets. A server
// without CoolCatLib sends nothing; then the advancements the client was shown are what counts.
public final class GuideProgress {
    private static final Set<ResourceLocation> DONE = new HashSet<>();
    private static final Map<String, BooleanSupplier> FLAGS = new ConcurrentHashMap<>();
    private static boolean heard;
    // Goes up whenever anything here changes, so what was laid out with locks in mind is laid out again.
    private static int version;

    private GuideProgress() {}

    public static int version() {
        return version;
    }

    public static void changed() {
        version++;
    }

    public static synchronized void receive(GuidePayloads.Advancements payload) {
        if (payload.reset()) DONE.clear();
        DONE.addAll(payload.done());
        payload.undone().forEach(DONE::remove);
        heard = true;
        version++;
    }

    public static synchronized void reset() {
        DONE.clear();
        heard = false;
        version++;
    }

    /** Whether the server has CoolCatLib: it has said which advancements the player has. */
    public static synchronized boolean serverHasGuides() {
        return heard;
    }

    public static synchronized boolean isDone(ResourceLocation advancement) {
        if (heard) return DONE.contains(advancement);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return false;
        ClientAdvancements advancements = minecraft.getConnection().getAdvancements();
        Advancement shown = advancements.getAdvancements().get(advancement);
        if (shown == null) return false;
        AdvancementProgress progress = ((ClientAdvancementsAccessor) advancements).folios$progress().get(shown);
        return progress != null && progress.isDone();
    }

    public static void setFlag(String flag, BooleanSupplier value) {
        FLAGS.put(flag, value);
        version++;
    }

    public static boolean flag(String flag) {
        boolean not = flag.startsWith("!");
        BooleanSupplier value = FLAGS.get(not ? flag.substring(1) : flag);
        boolean on = value != null && value.getAsBoolean();
        return not != on;
    }
}
