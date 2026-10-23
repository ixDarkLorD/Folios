package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.GuideLibrary;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Builds designed in the book's own files, turning in a window: "build" names one, "builds" several, picked between
// under the window. A bare name is a file of this book's (<book>/builds/<name>.json); "namespace:path" any book's file
// under folios/ ("mymod:my_guide/builds/tower"). A build file has a "name" to show it by, and its blocks: a "pattern"
// and "mapping" as a multiblock page's, then "blocks", each {"block": ..., "pos": [x, y, z]} or a box of them
// {"block": ..., "from": [x, y, z], "to": [x, y, z], "hollow": true}, in order, each over what came before (air clears).
// A block can be a tag, as on a multiblock page.
public record BuildPageElement(List<Showcase.Shown> builds, Optional<String> title, String text, int height, Showcase.Selector selector) implements FoliosPageElement {
    public static final MapCodec<BuildPageElement> CODEC = RecordCodecBuilder.<BuildPageElement>mapCodec(instance -> instance.group(
            Showcase.Shown.CODEC.listOf().optionalFieldOf("builds", List.of()).forGetter(BuildPageElement::builds),
            Showcase.Shown.CODEC.optionalFieldOf("build").forGetter(page -> Optional.empty()),
            Codec.STRING.optionalFieldOf("title").forGetter(BuildPageElement::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(BuildPageElement::text),
            Codec.intRange(40, 130).optionalFieldOf("height", 78).forGetter(BuildPageElement::height),
            Showcase.Selector.CODEC.optionalFieldOf("selector", Showcase.Selector.ARROWS).forGetter(BuildPageElement::selector)
    ).apply(instance, (many, one, title, text, height, selector) -> {
        List<Showcase.Shown> all = new ArrayList<>();
        one.ifPresent(all::add);
        all.addAll(many);
        return new BuildPageElement(List.copyOf(all), title, text, height, selector);
    })).validate(page -> page.builds.isEmpty() ? DataResult.error(() -> "a build page needs \"build\" or \"builds\"") : DataResult.success(page));

    /** The most blocks a box in a build file may fill, so a slip of a number can't fill the world. */
    private static final int MOST_BLOCKS = 65536;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        return Showcase.lay(layout, title, text, height, selector, this.items(layout));
    }

    /** What it shows between its title and its text. */
    Body body(FoliosLayout layout) {
        return Showcase.body(layout, height, selector, this.items(layout));
    }

    private List<Showcase.Item> items(FoliosLayout layout) {
        Identifier book = layout.book().id();
        List<Showcase.Item> items = new ArrayList<>();
        for (Showcase.Shown shown : builds) {
            Identifier id = shown.id().contains(":") ? Identifier.tryParse(shown.id()) : book.withPath(book.getPath() + "/builds/" + shown.id());
            JsonObject json = id == null ? null : GuideLibrary.build(id);
            Component name = shown.name().map(FoliosText::inline)
                    .orElse(json != null && json.has("name") ? FoliosText.inline(json.get("name").getAsString()) : null);
            Showcase.State state;
            if (json == null) {
                state = Showcase.State.saying(Component.translatableWithFallback("folios.build.missing", "No build %s", shown.id()));
            } else {
                try {
                    state = Showcase.State.of(read(json));
                } catch (Exception e) {
                    Folios.LOGGER.error("Couldn't read the build {} of the guide {}: {}", id, book, e.getMessage());
                    state = Showcase.State.saying(Component.translatableWithFallback("folios.build.unreadable", "The build %s couldn't be read", shown.id()));
                }
            }
            Showcase.State known = state;
            items.add(new Showcase.Item(name, () -> known));
        }
        return items;
    }

    /** A build file's blocks, by place, its lowest corner at 0. */
    static Map<BlockPos, BlockChoice> read(JsonObject json) {
        Map<BlockPos, BlockChoice> blocks = new LinkedHashMap<>();
        if (json.has("pattern")) {
            List<List<String>> pattern = new ArrayList<>();
            for (JsonElement layer : json.getAsJsonArray("pattern")) {
                List<String> rows = new ArrayList<>();
                for (JsonElement row : layer.getAsJsonArray()) rows.add(row.getAsString());
                pattern.add(rows);
            }
            Map<String, String> mapping = new LinkedHashMap<>();
            if (json.has("mapping")) json.getAsJsonObject("mapping").entrySet().forEach(entry -> mapping.put(entry.getKey(), entry.getValue().getAsString()));
            blocks.putAll(MultiblockPageElement.blocks(pattern, mapping));
        }
        if (json.has("blocks")) {
            for (JsonElement element : json.getAsJsonArray("blocks")) {
                JsonObject placed = element.getAsJsonObject();
                BlockChoice block = BlockChoice.of(placed.get("block").getAsString());
                // Air, or a block that can't be read, clears what it covers.
                boolean clears = !block.isTag() && block.options().isEmpty();
                BlockPos from = pos(placed.has("pos") ? placed.get("pos") : placed.get("from"));
                BlockPos to = placed.has("to") ? pos(placed.get("to")) : from;
                BlockPos low = BlockPos.min(from, to);
                BlockPos high = BlockPos.max(from, to);
                long volume = (long) (high.getX() - low.getX() + 1) * (high.getY() - low.getY() + 1) * (high.getZ() - low.getZ() + 1);
                if (volume > MOST_BLOCKS) throw new IllegalArgumentException("a box of " + volume + " blocks is more than " + MOST_BLOCKS);
                boolean hollow = placed.has("hollow") && placed.get("hollow").getAsBoolean();
                for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
                    boolean shell = pos.getX() == low.getX() || pos.getX() == high.getX() || pos.getY() == low.getY() || pos.getY() == high.getY()
                            || pos.getZ() == low.getZ() || pos.getZ() == high.getZ();
                    if (hollow && !shell) continue;
                    BlockPos at = pos.immutable();
                    blocks.remove(at);
                    if (!clears) blocks.put(at, block);
                }
            }
        }
        if (blocks.isEmpty()) return blocks;
        // Moved so its lowest corner is at 0, as a pattern's is: wherever its file put it.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos pos : blocks.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        if (minX == 0 && minY == 0 && minZ == 0) return blocks;
        Map<BlockPos, BlockChoice> moved = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockChoice> block : blocks.entrySet()) moved.put(block.getKey().offset(-minX, -minY, -minZ), block.getValue());
        return moved;
    }

    private static BlockPos pos(JsonElement json) {
        JsonArray xyz = json.getAsJsonArray();
        if (xyz.size() != 3) throw new IllegalArgumentException("a position is three numbers, [x, y, z]");
        return new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt());
    }

    @Override
    public String searchText() {
        StringBuilder words = new StringBuilder(FoliosText.resolve(title.orElse(""))).append(' ').append(FoliosText.resolve(text));
        builds.forEach(shown -> shown.name().ifPresent(name -> words.append(' ').append(FoliosText.resolve(name))));
        return words.toString();
    }
}
