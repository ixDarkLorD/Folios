package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.FoliosMentions;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component folios$mentions(Component message) {
        return FoliosMentions.decorate(message);
    }
}
