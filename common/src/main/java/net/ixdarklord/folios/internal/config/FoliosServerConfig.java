package net.ixdarklord.folios.internal.config;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.folios.internal.core.Folios;

public final class FoliosServerConfig {
    private static final ConfigBuilder BUILDER = Config.builder(Folios.MOD_ID, ConfigScope.SERVER)
            .comment("Folios server settings.");

    static {
        BUILDER.push("quests", "Quests from data packs (data/<namespace>/folios/quests), which the server follows and rewards.");
    }
    public static final ConfigValue<Boolean> STRICT = BUILDER.bool("strict", false)
            .comment("Rewarded quests may not ask for entries to be read: reading happens on the player's screen, so it is the one",
                    "thing the server can't see for itself. Such quests are skipped, with a warning in the log.").build();
    public static final ConfigValue<Double> READ_SECONDS = BUILDER.doubleValue("readSeconds", 2.0)
            .range(0.0, 60.0).comment("How long an entry must be open before the server counts it read, in seconds.").build();
    public static final ConfigValue<Integer> READS_PER_MINUTE = BUILDER.intValue("readsPerMinute", 20)
            .range(1, 600).comment("The most entries a player can be counted reading in a minute; more are ignored and logged.").build();
    public static final ConfigValue<Boolean> NEEDS_BOOK = BUILDER.bool("needsBook", true)
            .comment("Reading counts only in a book the player has with them, or one the server opened for them.").build();
    static {
        BUILDER.pop();
    }

    public static final Config CONFIG = BUILDER.build();

    private FoliosServerConfig() {}

    public static void init() {
    }
}
