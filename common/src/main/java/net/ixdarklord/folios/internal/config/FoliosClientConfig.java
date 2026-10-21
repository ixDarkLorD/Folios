package net.ixdarklord.folios.internal.config;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.folios.internal.core.Folios;

/** Each player's own preferences. */
public final class FoliosClientConfig {
    private static final ConfigBuilder BUILDER = Config.builder(Folios.MOD_ID, ConfigScope.CLIENT)
            .comment("Folios client settings.");

    /** A corner of the screen. */
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    static {
        BUILDER.push("buildPanel", "The panel showing a build's progress while it is shown in the world.");
    }
    public static final ConfigValue<Corner> BUILD_PANEL_CORNER = BUILDER.enumValue("corner", Corner.TOP_LEFT)
            .comment("Which corner of the screen the panel sits in.").build();
    public static final ConfigValue<Integer> BUILD_PANEL_X = BUILDER.intValue("x", 8)
            .range(0, 2000).comment("How far the panel's frame is from that corner's side, in GUI pixels.").build();
    public static final ConfigValue<Integer> BUILD_PANEL_Y = BUILDER.intValue("y", 8)
            .range(0, 2000).comment("How far the panel's frame is from that corner's top or bottom, in GUI pixels.").build();
    static {
        BUILDER.pop();
    }

    public static final Config CONFIG = BUILDER.build();

    private FoliosClientConfig() {}
}
