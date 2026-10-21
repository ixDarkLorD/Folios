package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

// Minecraft 26.1's render type factories, which 1.21.1 keeps on RenderType itself.
public final class RenderTypes {
    private RenderTypes() {}

    public static RenderType entityCutout(ResourceLocation texture) {
        return RenderType.entityCutout(texture);
    }

    public static RenderType entityTranslucent(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
