package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

// Minecraft 26.1's render type factories, which 1.21.1 keeps on RenderType itself.
public final class RenderTypes {
    private RenderTypes() {}

    // 26.1's entity cutout draws both sides of a face (its pipeline has no culling); this version's culls the back, which
    // hid faces wound the other way, as the book's mirrored leaf edges are.
    public static RenderType entityCutout(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    public static RenderType entityTranslucent(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
