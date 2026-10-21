package net.ixdarklord.folios.internal.mixin.client;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// The progress of the advancements a client was shown, for unlocking books on a server that doesn't send its own.
@Mixin(ClientAdvancements.class)
public interface ClientAdvancementsAccessor {
    @Accessor("progress")
    Map<AdvancementHolder, AdvancementProgress> folios$progress();
}
