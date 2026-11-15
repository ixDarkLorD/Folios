package net.ixdarklord.folios.internal.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record ItemRow(List<ItemStack> stacks) implements ComponentContents {
    public static final int COLUMNS = 8;
    public static final int CELL = 17;

    public static MutableComponent of(List<ItemStack> stacks) {
        return MutableComponent.create(new ItemRow(List.copyOf(stacks)));
    }

    public static @Nullable List<ItemStack> in(Component line) {
        return line.getContents() instanceof ItemRow row ? row.stacks : null;
    }

    public static int width(List<ItemStack> stacks) {
        return Math.min(COLUMNS, stacks.size()) * CELL - 1;
    }

    public static int height(List<ItemStack> stacks) {
        return (stacks.size() + COLUMNS - 1) / COLUMNS * CELL + 1;
    }

    private String names() {
        return stacks.stream().map(stack -> stack.getHoverName().getString()).collect(Collectors.joining(", "));
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) {
        return consumer.accept(style, this.names());
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
        return consumer.accept(this.names());
    }
}
