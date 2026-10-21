package net.ixdarklord.folios.internal.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Looking about in a handheld book: the mouse counts as grabbed, so its motion turns the view as in play.
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("mouseGrabbed")
    void folios$setMouseGrabbed(boolean grabbed);
}
