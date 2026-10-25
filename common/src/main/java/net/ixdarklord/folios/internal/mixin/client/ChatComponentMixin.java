package net.ixdarklord.folios.internal.mixin.client;

import net.ixdarklord.folios.internal.client.FoliosMentions;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Every message the chat shows: the pages it mentions shown as their names, or scrambled (FoliosMentions).
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    // The overload every message ends up in (the one taking only the message hands it on).
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component folios$mentions(Component message) {
        return FoliosMentions.decorate(message);
    }
}
