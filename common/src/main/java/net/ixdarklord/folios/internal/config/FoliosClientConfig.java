package net.ixdarklord.folios.internal.config;

import net.ixdarklord.coolcatcore.api.config.Config;
import net.ixdarklord.coolcatcore.api.config.ConfigBuilder;
import net.ixdarklord.coolcatcore.api.config.ConfigScope;
import net.ixdarklord.coolcatcore.api.config.ConfigValue;
import net.ixdarklord.folios.internal.core.Folios;

public final class FoliosClientConfig {
    private static final ConfigBuilder BUILDER = Config.builder(Folios.MOD_ID, ConfigScope.CLIENT)
            .comment("Folios client settings.");

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
        BUILDER.push("reading", "How books read.");
    }
    public static final ConfigValue<Double> TEXT_SCALE = BUILDER.doubleValue("textScale", 1.0)
            .range(1.0, 2.0).comment("How much larger to draw what is on a book's pages. Pages hold less as it grows, and a book",
                    "never grows past what fits on the screen.").build();
    public static final ConfigValue<Boolean> NARRATE_PAGES = BUILDER.bool("narratePages", true)
            .comment("With the game's narrator on, read each page aloud as it is turned to.").build();
    public static final ConfigValue<Boolean> LOOKUP_ANY_BOOK = BUILDER.bool("lookupAnyBook", true)
            .comment("Holding the look-up key over an item opens its page in any installed book, not only books you carry.",
                    "Books you carry are always tried first, and a book can ask to be looked up only when carried.").build();
    public static final ConfigValue<Boolean> RECIPES_THROUGH_VIEWER = BUILDER.bool("recipesThroughViewer", false)
            .comment("Draw books' recipes with the installed recipe viewer (JEI, REI or EMI) instead of the book's own drawing,",
                    "laid out as the viewer lays them out. Viewer recipe pages always use the viewer.").build();
    static {
        BUILDER.pop();
    }

    public static final Config CONFIG = BUILDER.build();

    private FoliosClientConfig() {}
}
