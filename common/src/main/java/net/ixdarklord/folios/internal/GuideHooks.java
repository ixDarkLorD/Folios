package net.ixdarklord.folios.internal;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

// What common code (the book item) asks of the client: set by the client's constructor. On a dedicated server
// nothing is set, and a book's name falls back to its translation key.
public final class GuideHooks {
    public static BiConsumer<Identifier, InteractionHand> opener = (book, hand) -> {};
    public static Function<Identifier, Component> names = GuideHooks::defaultName;

    private GuideHooks() {}

    public static void openFromItem(Identifier book, InteractionHand hand) {
        opener.accept(book, hand);
    }

    public static Component bookName(Identifier book) {
        return names.apply(book);
    }

    /** {@code guide.<namespace>.<path>.name}, with the path in title case as the fallback. */
    public static Component defaultName(Identifier book) {
        String path = book.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        StringBuilder title = new StringBuilder();
        for (String word : last.split(" ")) {
            if (word.isEmpty()) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return Component.translatableWithFallback("guide." + book.getNamespace() + "." + path.replace('/', '.') + ".name", title.toString());
    }
}
