package net.ixdarklord.folios.internal.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The selection's other end, which vanilla's text field keeps private (CompatEditBox moves the cursor keeping it).
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("highlightPos")
    int folios$highlightPos();
}
