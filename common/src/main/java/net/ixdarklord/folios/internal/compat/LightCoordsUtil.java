package net.ixdarklord.folios.internal.compat;

import net.minecraft.client.renderer.LightTexture;

// Minecraft 26.1's packed light helpers, on 1.21.1's LightTexture.
public final class LightCoordsUtil {
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private LightCoordsUtil() {}

    public static int pack(int block, int sky) {
        return LightTexture.pack(block, sky);
    }

    /** The brighter of two packed lights, block and sky each. */
    public static int max(int a, int b) {
        return pack(Math.max(LightTexture.block(a), LightTexture.block(b)), Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }

    public static int lightCoordsWithEmission(int lightCoords, int emission) {
        return pack(Math.max(LightTexture.block(lightCoords), emission), LightTexture.sky(lightCoords));
    }
}
