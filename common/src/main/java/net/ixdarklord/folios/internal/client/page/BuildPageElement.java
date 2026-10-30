package net.ixdarklord.folios.internal.client.page;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.ixdarklord.folios.internal.client.FoliosLibrary;
import net.ixdarklord.folios.internal.client.build.BlockChoice;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record BuildPageElement(List<Showcase.Shown> builds, Optional<String> title, String text, int height, Showcase.Selector selector) implements FoliosPageElement {
    public static final MapCodec<BuildPageElement> CODEC = RecordCodecBuilder.<BuildPageElement>mapCodec(instance -> instance.group(
            Showcase.Shown.CODEC.fieldOf("build").forGetter(BuildPageElement::builds),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(BuildPageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(BuildPageElement::text),
            Codec.intRange(40, 130).optionalFieldOf("height", 78).forGetter(BuildPageElement::height),
            Showcase.Selector.CODEC.optionalFieldOf("selector", Showcase.Selector.ARROWS).forGetter(BuildPageElement::selector)
    ).apply(instance, BuildPageElement::new));

    private static final int MOST_BLOCKS = 65536;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        return Showcase.lay(layout, title, text, height, selector, this.items(layout));
    }

    Body body(FoliosLayout layout) {
        return Showcase.body(layout, height, selector, this.items(layout));
    }

    private List<Showcase.Item> items(FoliosLayout layout) {
        ResourceLocation book = layout.book().id();
        List<Showcase.Item> items = new ArrayList<>();
        for (Showcase.Shown shown : builds) {
            ResourceLocation id = shown.id().contains(":") ? ResourceLocation.tryParse(shown.id()) : book.withPath(book.getPath() + "/builds/" + shown.id());
            JsonObject json = id == null ? null : FoliosLibrary.build(id);
            Component name = shown.label().map(FoliosText::inline)
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
                boolean clears = !block.isTag() && block.options().isEmpty();
                BlockPos from = pos(placed.has("pos") ? placed.get("pos") : placed.get("from"));
                BlockPos to = placed.has("to") ? pos(placed.get("to")) : from;
                BlockPos low = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
                BlockPos high = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
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
        builds.forEach(shown -> shown.label().ifPresent(name -> words.append(' ').append(FoliosText.resolve(name))));
        return words.toString();
    }
}
