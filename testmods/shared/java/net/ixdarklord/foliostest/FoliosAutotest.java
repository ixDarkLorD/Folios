package net.ixdarklord.foliostest;

import net.ixdarklord.coolcatcore.api.event.v2.client.ClientTickEvents;
import net.ixdarklord.folios.internal.client.GuideClient;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.ixdarklord.folios.internal.client.build.BuildGuide;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// A run through the demo books on its own, for versions the MCP bridge doesn't reach: started with
// -Dfolios.autotest=true (through JAVA_TOOL_OPTIONS). It makes a flat creative world, holds and opens the books, and
// leaves a screenshot of each step in run/screenshots (folios-autotest-*.png), then quits.
public final class FoliosAutotest {
    private static final ResourceLocation JOURNAL = new ResourceLocation("foliostest", "field_journal");
    private static final ResourceLocation HANDBOOK = new ResourceLocation("foliostest", "handbook");

    private record Step(int ticks, Consumer<Minecraft> action) {}

    // Whether the pages were dark before the test made them so, to put them back.
    private static boolean wasDark;

    private static final List<Step> STEPS = List.of(
            new Step(60, minecraft -> {
                command(minecraft, "time set noon");
                command(minecraft, "gamerule doDaylightCycle false");
                command(minecraft, "weather clear");
                command(minecraft, "item replace entity @s weapon.mainhand with folios:guide_book{folios_book:\"foliostest:field_journal\"}");
            }),
            new Step(40, minecraft -> shot(minecraft, "01_held")),
            new Step(1, minecraft -> minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND)),
            new Step(50, minecraft -> shot(minecraft, "02_raised_shut")),
            new Step(1, minecraft -> {
                if (minecraft.screen != null) minecraft.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            }),
            new Step(70, minecraft -> shot(minecraft, "03_open")),
            new Step(1, minecraft -> GuideClient.open(JOURNAL, "crafting/tools", InteractionHand.MAIN_HAND)),
            new Step(70, minecraft -> shot(minecraft, "04_recipes")),
            // The same spread looked at from another way: the pages, tabs and bar must keep their light.
            new Step(1, minecraft -> {
                minecraft.player.setYRot(135.0F);
                minecraft.player.setXRot(-15.0F);
            }),
            new Step(20, minecraft -> shot(minecraft, "04b_recipes_turned")),
            new Step(1, minecraft -> {
                minecraft.player.setYRot(0.0F);
                minecraft.player.setXRot(0.0F);
            }),
            new Step(1, minecraft -> GuideClient.open(JOURNAL, "world/builds", InteractionHand.MAIN_HAND)),
            new Step(90, minecraft -> shot(minecraft, "05_multiblock")),
            new Step(1, minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT)),
            new Step(30, minecraft -> shot(minecraft, "06_third_person")),
            // Started from the open book, as its eye clasp does, so the panel takes the book's look; then put away.
            new Step(1, minecraft -> {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                Map<BlockPos, BlockChoice> blocks = new LinkedHashMap<>();
                for (int x = 0; x < 3; x++) {
                    for (int z = 0; z < 3; z++) blocks.put(new BlockPos(x, 0, z), BlockChoice.of(Blocks.STONE_BRICKS.defaultBlockState()));
                }
                blocks.put(new BlockPos(1, 1, 1), BlockChoice.of(Blocks.CHEST.defaultBlockState()));
                BuildGuide.start(Component.literal("Autotest build"), blocks);
                if (minecraft.screen != null) minecraft.screen.onClose();
            }),
            new Step(40, minecraft -> minecraft.player.setXRot(50.0F)),
            new Step(30, minecraft -> shot(minecraft, "07_ghosts")),
            new Step(1, minecraft -> {
                BuildGuide.stop();
                minecraft.player.setXRot(0.0F);
                command(minecraft, "item replace entity @s weapon.mainhand with folios:guide_book{folios_book:\"foliostest:handbook\"}");
            }),
            // Used from the hand, a screen book is raised and opened there before its screen comes up.
            new Step(20, minecraft -> minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND)),
            new Step(6, minecraft -> shot(minecraft, "08a_screen_book_raising")),
            new Step(8, minecraft -> shot(minecraft, "08b_screen_book_opening")),
            new Step(60, minecraft -> shot(minecraft, "08_screen_book")),
            new Step(1, minecraft -> minecraft.setScreen(new InventoryScreen(minecraft.player))),
            new Step(20, minecraft -> shot(minecraft, "09_inventory")),
            // Others holding books: an armor stand, a husk with the reading mark and an allay, facing the player.
            new Step(1, minecraft -> {
                minecraft.setScreen(null);
                minecraft.player.setYRot(0.0F);
                minecraft.player.setXRot(10.0F);
                command(minecraft, "difficulty easy");
                command(minecraft, "summon armor_stand ~-1.5 ~ ~3 {ShowArms:1b,NoBasePlate:1b,Rotation:[180f,0f],HandItems:[{id:\"folios:guide_book\",Count:1b,tag:{folios_book:\"foliostest:field_journal\"}},{}]}");
                command(minecraft, "summon husk ~ ~ ~3.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],HandItems:[{id:\"folios:guide_book\",Count:1b,tag:{folios_book:\"foliostest:handbook\",folios_reading:1b}},{}]}");
                command(minecraft, "summon allay ~1.5 ~0.5 ~3 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],HandItems:[{id:\"folios:guide_book\",Count:1b,tag:{folios_book:\"foliostest:field_journal\"}},{}]}");
            }),
            new Step(40, minecraft -> shot(minecraft, "10_actors")),
            new Step(1, minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK)),
            new Step(30, minecraft -> shot(minecraft, "11_actors_behind")),
            // The pages added with tags, structures, builds and elements, on the screen book.
            new Step(1, minecraft -> GuideClient.open(HANDBOOK, "world/builds#hut", null)),
            new Step(90, minecraft -> shot(minecraft, "10_hut_tags")),
            new Step(25, minecraft -> shot(minecraft, "10b_hut_tags_later")),
            new Step(1, minecraft -> GuideClient.open(HANDBOOK, "world/builds#structures", null)),
            new Step(120, minecraft -> shot(minecraft, "11_structures_builds")),
            new Step(1, minecraft -> GuideClient.open(HANDBOOK, "world/builds#elements", null)),
            new Step(100, minecraft -> shot(minecraft, "12_elements")),
            new Step(1, minecraft -> {
                if (minecraft.screen != null) minecraft.screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            }),
            new Step(100, minecraft -> shot(minecraft, "13_elements_next")),
            // Pressing them: the hut's blocks list, the second structure, the next build.
            new Step(1, minecraft -> GuideClient.open(HANDBOOK, "world/builds#hut", null)),
            new Step(90, minecraft -> click(minecraft, 334, 144)),
            new Step(10, minecraft -> shot(minecraft, "15_blocks_list")),
            new Step(1, minecraft -> GuideClient.open(HANDBOOK, "world/builds#structures", null)),
            new Step(100, minecraft -> click(minecraft, 142, 161)),
            new Step(40, minecraft -> click(minecraft, 337, 161)),
            new Step(20, minecraft -> shot(minecraft, "16_number_and_arrow")),
            // The requirement list of a build with more kinds than it shows at once, opened and scrolled.
            new Step(1, minecraft -> GuideClient.open(HANDBOOK, "world/sampler", null)),
            new Step(80, minecraft -> click(minecraft, 192, 144)),
            new Step(10, minecraft -> shot(minecraft, "17_requirement_list")),
            new Step(1, minecraft -> {
                if (minecraft.screen != null) minecraft.screen.mouseScrolled(130, 120, -2);
            }),
            new Step(10, minecraft -> shot(minecraft, "18_requirement_list_scrolled")),
            // The handheld book in dark mode, then back to how it was.
            new Step(1, minecraft -> {
                if (minecraft.screen != null) minecraft.screen.onClose();
                command(minecraft, "item replace entity @s weapon.mainhand with folios:guide_book{folios_book:\"foliostest:field_journal\"}");
            }),
            new Step(20, minecraft -> GuideClient.open(JOURNAL, null, InteractionHand.MAIN_HAND)),
            new Step(50, minecraft -> {
                if (minecraft.screen != null) minecraft.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            }),
            new Step(70, minecraft -> {
                wasDark = net.ixdarklord.coolcatcanvas.internal.client.gui.style.ConfigStyle.mode() == net.ixdarklord.coolcatcore.api.config.ConfigTheme.Mode.DARK;
                if (!wasDark) net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings.toggleThemeMode();
            }),
            new Step(20, minecraft -> shot(minecraft, "14_handheld_dark")),
            new Step(1, minecraft -> {
                if (!wasDark) net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings.toggleThemeMode();
            }),
            // Finishing the handbook's quest: each objective, the quest and mastery celebrated (the test book turns them on).
            new Step(20, minecraft -> {
                if (minecraft.screen != null) minecraft.screen.onClose();
                minecraft.player.setXRot(-30.0F);
            }),
            new Step(5, minecraft -> GuideClient.open(HANDBOOK, "basics/first_steps", null)),
            new Step(60, minecraft -> GuideClient.open(HANDBOOK, "crafting/tools", null)),
            new Step(60, minecraft -> GuideClient.open(HANDBOOK, "crafting/torches", null)),
            new Step(60, minecraft -> {
                if (minecraft.screen != null) minecraft.screen.onClose();
                command(minecraft, "give @s minecraft:oak_log 8");
            }),
            new Step(80, minecraft -> command(minecraft, "advancement grant @s only minecraft:story/mine_stone")),
            new Step(30, minecraft -> shot(minecraft, "19_toasts")),
            new Step(1, minecraft -> GuideClient.open(HANDBOOK, null, null)),
            new Step(60, minecraft -> shot(minecraft, "20_mastered_front")),
            new Step(40, minecraft -> Runtime.getRuntime().halt(0))
    );

    private static int step = -1;
    private static int waited;
    private static boolean worldMade;

    private FoliosAutotest() {}

    public static void init() {
        if (!Boolean.getBoolean("folios.autotest")) return;
        org.slf4j.LoggerFactory.getLogger("FoliosAutotest").info("Autotest on");
        ClientTickEvents.END.register(FoliosAutotest::tick);
    }

    private static void tick(Minecraft minecraft) {
        // The window is rarely focused while it runs: losing focus mustn't pause the game behind a closed screen.
        minecraft.options.pauseOnLostFocus = false;
        if (step < 0) {
            // Forge's screen of loading warnings: kept as a screenshot, then passed by.
            if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("LoadingErrorScreen") && minecraft.getOverlay() == null && ++waited > 40) {
                shot(minecraft, "00_loading_warnings");
                minecraft.setScreen(new TitleScreen());
                waited = 0;
                return;
            }
            if (!worldMade && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null && ++waited > 20) {
                worldMade = true;
                makeWorld(minecraft);
            }
            if (minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
                step = 0;
                waited = 0;
            }
            return;
        }
        if (step >= STEPS.size()) return;
        Step current = STEPS.get(step);
        if (++waited < current.ticks()) return;
        waited = 0;
        step++;
        current.action().accept(minecraft);
    }

    private static void makeWorld(Minecraft minecraft) {
        GameRules rules = new GameRules();
        LevelSettings settings = new LevelSettings("Folios Autotest " + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    // A left click at a place on the screen, in its scaled coordinates, pressed and let go.
    private static void click(Minecraft minecraft, double x, double y) {
        if (minecraft.screen == null) return;
        minecraft.screen.mouseClicked(x, y, 0);
        minecraft.screen.mouseReleased(x, y, 0);
    }

    private static void command(Minecraft minecraft, String command) {
        minecraft.player.connection.sendCommand(command);
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, "folios-autotest-" + name + ".png", minecraft.getMainRenderTarget(), message -> {});
    }
}
