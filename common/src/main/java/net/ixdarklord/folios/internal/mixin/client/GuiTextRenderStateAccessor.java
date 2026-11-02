package net.ixdarklord.folios.internal.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccessor {
    @Accessor("font")
    Font folios$font();

    @Accessor("text")
    FormattedCharSequence folios$text();

    @Accessor("x")
    int folios$x();

    @Accessor("y")
    int folios$y();

    @Accessor("color")
    int folios$color();

    @Accessor("backgroundColor")
    int folios$backgroundColor();

    @Accessor("dropShadow")
    boolean folios$dropShadow();
}
