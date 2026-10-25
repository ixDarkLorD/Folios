package net.ixdarklord.folios.internal.client.page;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The entities books show in scenes (entity pages, entity popups), made on the client and in no world, so nothing else
 * ticks them. Each grows a tick older every client tick, which is what their models' moving parts follow (a blaze's
 * rods, wings, fins, an idle sway), drawn between ticks by the frame's partial tick. Only their age: a whole tick would
 * move them, think and make sounds and particles in the world. Held weakly, so one a book no longer shows goes.
 */
public final class PreviewEntities {
    private static final Set<Entity> SHOWN = Collections.newSetFromMap(new WeakHashMap<>());

    private PreviewEntities() {}

    /** An entity a scene shows: it ages with the game from now, facing straight ahead. */
    public static synchronized void track(Entity entity) {
        still(entity);
        SHOWN.add(entity);
    }

    // Faces ahead, its head and body with it, the same as it was the tick before. A new mob is turned some random way
    // with the tick before's turn left at nothing, and its head is drawn between the two by the frame's partial tick,
    // which made it jitter.
    private static void still(Entity entity) {
        entity.setYRot(0);
        entity.setXRot(0);
        entity.yRotO = 0;
        entity.xRotO = 0;
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = 0;
            living.yHeadRotO = 0;
            living.yBodyRot = 0;
            living.yBodyRotO = 0;
        }
    }

    /** At the end of every client tick. */
    public static synchronized void tick() {
        for (Entity entity : SHOWN) {
            entity.tickCount++;
            still(entity);
        }
    }
}
