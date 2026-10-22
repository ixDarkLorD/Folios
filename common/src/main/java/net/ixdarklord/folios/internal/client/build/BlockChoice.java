package net.ixdarklord.folios.internal.client.build;

import com.mojang.datafixers.util.Either;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// What may stand at a place of a build: one block state ("minecraft:oak_stairs[facing=east]"), or any block of a tag
// ("#minecraft:logs[axis=y]", the properties set on each block that has them). A tag is shown a block of it at a time,
// a new one each second, and any of its blocks will do. It is read when first needed, once the world's tags are known.
public final class BlockChoice {
    private final String written;
    private @Nullable BlockState state;
    private @Nullable TagKey<Block> tag;
    private Map<String, String> properties = Map.of();
    private @Nullable List<BlockState> options;
    private boolean failed;

    private BlockChoice(String written, @Nullable BlockState state) {
        this.written = written;
        this.state = state;
        if (state != null) this.options = List.of(state);
    }

    /** A block or tag as a page writes it; read when first needed. */
    public static BlockChoice of(String written) {
        return new BlockChoice(written.strip(), null);
    }

    /** Just this block state. */
    public static BlockChoice of(BlockState state) {
        return new BlockChoice(state.toString(), state);
    }

    public boolean isTag() {
        return written.startsWith("#");
    }

    /** The blocks that will do here, in a steady order; empty for none (air, or what can't be read). */
    public List<BlockState> options() {
        if (options != null) return options;
        if (failed) return List.of();
        try {
            Either<BlockStateParser.BlockResult, BlockStateParser.TagResult> parsed = BlockStateParser.parseForTesting(BuiltInRegistries.BLOCK.asLookup(), written, false);
            List<BlockState> found = new ArrayList<>();
            parsed.ifLeft(block -> {
                state = block.blockState();
                found.add(block.blockState());
            });
            parsed.ifRight(result -> {
                properties = result.vagueProperties();
                if (result.tag() instanceof net.minecraft.core.HolderSet.Named<Block> named) tag = named.key();
                for (Holder<Block> holder : result.tag()) found.add(withProperties(holder.value().defaultBlockState(), properties));
                found.sort(Comparator.comparing(each -> BuiltInRegistries.BLOCK.getKey(each.getBlock()).toString()));
            });
            found.removeIf(BlockState::isAir);
            // A tag read before the world's tags are known is empty: asked again later.
            if (found.isEmpty() && isTag()) return List.of();
            options = List.copyOf(found);
        } catch (Exception e) {
            failed = true;
            Folios.LOGGER.warn("A build names a block it can't read, {}: {}", written, e.getMessage());
            return List.of();
        }
        return options;
    }

    // A block's default state with the named properties it has set to the values given.
    private static BlockState withProperties(BlockState state, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property != null) state = with(state, property, entry.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    /** The block shown at a time (seconds): a tag's blocks in turn, a second each. */
    public @Nullable BlockState shown(float time) {
        List<BlockState> all = this.options();
        if (all.isEmpty()) return null;
        return all.get(Math.floorMod((int) time, all.size()));
    }

    /** Whether more than one block will do, so what is shown changes. */
    public boolean cycles() {
        return this.options().size() > 1;
    }

    /** Whether a block standing in the world is one that will do. */
    public boolean matches(BlockState there) {
        for (BlockState option : this.options()) {
            if (option.getBlock() == there.getBlock()) return true;
        }
        return false;
    }

    /** What choices are counted together as: the block for one, the tag (and its properties) for a tag. */
    public Object key() {
        this.options();
        if (state != null) return state.getBlock();
        return isTag() ? written.replaceAll("\\[.*", "") : written;
    }

    /** How many items placing it takes: none for the half of a door or bed that comes with the other, two for a double slab. */
    public int items() {
        List<BlockState> all = this.options();
        return all.isEmpty() ? 1 : items(all.getFirst());
    }

    public static int items(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return 0;
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return 0;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        return 1;
    }

    /** A stack of what is shown at a time, {@code count} of it; empty for a block with no item. */
    public ItemStack stack(float time, int count) {
        BlockState shown = this.shown(time);
        return shown == null ? ItemStack.EMPTY : new ItemStack(shown.getBlock().asItem(), Math.max(1, count));
    }

    /** Its name: the block's, or for a tag "Any …", by the tag's translation if it has one. */
    public Component name(float time) {
        this.options();
        if (tag != null) {
            String id = tag.location().getNamespace() + "." + tag.location().getPath().replace('/', '.');
            // Without a translation, the tag's last word or words in title case: "wooden_fences" is "Wooden Fences".
            String path = tag.location().getPath();
            StringBuilder words = new StringBuilder();
            for (String word : path.substring(path.lastIndexOf('/') + 1).split("_")) {
                if (word.isEmpty()) continue;
                if (!words.isEmpty()) words.append(' ');
                words.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            Component named = Component.translatableWithFallback("tag.block." + id, words.toString());
            return Component.translatableWithFallback("folios.build.any", "Any %s", named);
        }
        BlockState shown = this.shown(time);
        return shown != null ? shown.getBlock().getName() : Component.literal(written);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockChoice choice && written.equals(choice.written) && Objects.equals(state, choice.state);
    }

    @Override
    public int hashCode() {
        return written.hashCode();
    }

    @Override
    public String toString() {
        return written;
    }
}
