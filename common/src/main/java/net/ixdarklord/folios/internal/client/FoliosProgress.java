package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.internal.FoliosPayloads;
import net.ixdarklord.folios.internal.mixin.client.ClientAdvancementsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class FoliosProgress {
    private static final Set<Identifier> DONE = new HashSet<>();
    private static final Map<String, BooleanSupplier> FLAGS = new ConcurrentHashMap<>();
    private static boolean heard;
    private static int version;

    private FoliosProgress() {}

    public static int version() {
        return version;
    }

    public static void changed() {
        version++;
    }

    public static synchronized void receive(FoliosPayloads.Advancements payload) {
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

    public static synchronized boolean serverHasGuides() {
        return heard;
    }

    public static synchronized boolean isDone(Identifier advancement) {
        if (heard) return DONE.contains(advancement);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return false;
        ClientAdvancements advancements = minecraft.getConnection().getAdvancements();
        AdvancementNode node = advancements.tree().get(advancement);
        if (node == null) return false;
        AdvancementProgress progress = ((ClientAdvancementsAccessor) advancements).folios$progress().get(node.holder());
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
