package net.ixdarklord.folios.internal.core;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

// Folios: guide books for mods, built on CoolCatLib.
public final class Folios {
    public static final String MOD_ID = "folios";
    public static final Logger LOGGER = LoggerFactory.getLogger("Folios");

    private Folios() {}

    public static ResourceLocation rl(String name) {
        return new ResourceLocation(MOD_ID, name.toLowerCase(Locale.ROOT));
    }
}
