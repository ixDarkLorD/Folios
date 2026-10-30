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

    public static BlockChoice of(String written) {
        return new BlockChoice(written.strip(), null);
    }

    public static BlockChoice of(BlockState state) {
        return new BlockChoice(state.toString(), state);
    }

    public boolean isTag() {
        return written.startsWith("#");
    }

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
            if (found.isEmpty() && isTag()) return List.of();
            options = List.copyOf(found);
        } catch (Exception e) {
            failed = true;
            Folios.LOGGER.warn("A build names a block it can't read, {}: {}", written, e.getMessage());
            return List.of();
        }
        return options;
    }

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

    public @Nullable BlockState shown(float time) {
        List<BlockState> all = this.options();
        if (all.isEmpty()) return null;
        return all.get(Math.floorMod((int) time, all.size()));
    }

    public boolean cycles() {
        return this.options().size() > 1;
    }

    public boolean matches(BlockState there) {
        for (BlockState option : this.options()) {
            if (option.getBlock() == there.getBlock()) return true;
        }
        return false;
    }

    public Object key() {
        this.options();
        if (state != null) return state.getBlock();
        return isTag() ? written.replaceAll("\\[.*", "") : written;
    }

    public int items() {
        List<BlockState> all = this.options();
        return all.isEmpty() ? 1 : items(all.get(0));
    }

    public static int items(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return 0;
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return 0;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        return 1;
    }

    public ItemStack stack(float time, int count) {
        BlockState shown = this.shown(time);
        return shown == null ? ItemStack.EMPTY : new ItemStack(shown.getBlock().asItem(), Math.max(1, count));
    }

    public Component name(float time) {
        this.options();
        if (tag != null) {
            String id = tag.location().getNamespace() + "." + tag.location().getPath().replace('/', '.');
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
