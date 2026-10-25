package net.ixdarklord.folios.internal.mixin.client;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The picture a picture-in-picture drawing made, for a book's scaled items drawn with a colour.
@Mixin(PictureInPictureRenderer.class)
public interface PictureInPictureRendererAccessor {
    @Accessor("textureView")
    GpuTextureView folios$view();
}
