package net.ixdarklord.folios.internal.client.page;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class PreviewEntities {
    private static final Set<Entity> SHOWN = Collections.newSetFromMap(new WeakHashMap<>());

    private PreviewEntities() {}

    public static synchronized void track(Entity entity) {
        still(entity);
        SHOWN.add(entity);
    }

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

    public static synchronized void tick() {
        for (Entity entity : SHOWN) {
            entity.tickCount++;
            still(entity);
        }
    }
}
