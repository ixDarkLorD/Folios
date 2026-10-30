package net.ixdarklord.folios.internal.core;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class Folios {
    public static final String MOD_ID = "folios";
    public static final Logger LOGGER = LoggerFactory.getLogger("Folios");

    private Folios() {}

    public static Identifier rl(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name.toLowerCase(Locale.ROOT));
    }
}
