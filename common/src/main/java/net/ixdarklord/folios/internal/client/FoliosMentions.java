package net.ixdarklord.folios.internal.client;

import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FoliosMentions {
    public static final KeyMapping KEY = new KeyMapping("key.folios.mention", GLFW.GLFW_KEY_M, "key.categories.multiplayer");
    private static final Pattern TOKEN = Pattern.compile("\\[\\[([a-z0-9_.-]+:[a-z0-9_./-]+)\\|([a-z0-9_./-]*)\\|(\\d{1,4})]]");

    private FoliosMentions() {}

    public static String token(ResourceLocation book, FoliosBook.@Nullable Location at) {
        return "[[" + book + "|" + (at != null ? at.entry() : "") + "|" + (at != null ? at.page() : 0) + "]]";
    }

    public static void mention(ResourceLocation book, FoliosBook.@Nullable Location at) {
        Minecraft.getInstance().setScreen(new ChatScreen(token(book, at) + " "));
    }

    public static Component decorate(Component message) {
        if (!message.getString().contains("[[")) return message;
        return copy(message);
    }

    private static MutableComponent copy(Component component) {
        ComponentContents contents = component.getContents();
        MutableComponent out;
        if (contents instanceof LiteralContents literal && TOKEN.matcher(literal.text()).find()) {
            out = Component.empty();
            Matcher matcher = TOKEN.matcher(literal.text());
            int at = 0;
            while (matcher.find()) {
                if (matcher.start() > at) out.append(Component.literal(literal.text().substring(at, matcher.start())));
                ResourceLocation book = ResourceLocation.tryParse(matcher.group(1));
                out.append(book != null ? shown(book, matcher.group(2), Integer.parseInt(matcher.group(3))) : Component.literal(matcher.group()));
                at = matcher.end();
            }
            if (at < literal.text().length()) out.append(Component.literal(literal.text().substring(at)));
        } else if (contents instanceof TranslatableContents translatable) {
            Object[] arguments = translatable.getArgs().clone();
            for (int index = 0; index < arguments.length; index++) {
                if (arguments[index] instanceof Component argument) arguments[index] = copy(argument);
                else if (arguments[index] instanceof String argument && argument.contains("[[")) arguments[index] = copy(Component.literal(argument));
            }
            out = translatable.getFallback() != null ? Component.translatableWithFallback(translatable.getKey(), translatable.getFallback(), arguments)
                    : Component.translatable(translatable.getKey(), arguments);
        } else {
            out = MutableComponent.create(contents);
        }
        out.setStyle(component.getStyle());
        for (Component sibling : component.getSiblings()) out.append(copy(sibling));
        return out;
    }

    private static Component shown(ResourceLocation bookId, String entryId, int page) {
        FoliosBook book = FoliosLibrary.book(bookId);
        if (book == null) return scrambled(Component.translatableWithFallback("folios.mention.no_book", "A page of a book you don't have"));
        FoliosEntry entry = entryId.isEmpty() ? null : book.entry(entryId);
        if (!entryId.isEmpty() && (entry == null || !entry.isOpen() || page >= entry.pages().size() || !entry.pages().get(page).lock().isOpen())) {
            return scrambled(Component.translatableWithFallback("folios.mention.locked", "A page you haven't found yet"));
        }
        MutableComponent name = Component.empty().append(FoliosText.inline(book.name()));
        if (entry != null) {
            name.append(Component.literal(" › ")).append(entry.title());
            FoliosSession session = FoliosLibrary.session(bookId);
            if (session != null) {
                int laid = session.pageOf(new FoliosBook.Location(entryId, page));
                if (laid > 0) name.append(Component.translatableWithFallback("folios.mention.page", ", p. %s", laid + 1));
            }
        }
        Component tooltip = Component.translatableWithFallback("folios.mention.open", "Open it in the book");
        return Component.literal("[").append(name).append("]").withStyle(Style.EMPTY.withColor(book.accent()).withUnderlined(true)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/folios mention " + bookId + " " + (entryId.isEmpty() ? "-" : entryId) + " " + page)));
    }

    private static Component scrambled(Component why) {
        return Component.literal("[").append(Component.literal("Folios hidden page").withStyle(ChatFormatting.OBFUSCATED)).append("]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, why)));
    }

    public static boolean open(String written) {
        String[] parts = written.strip().split("\\s+");
        if (parts.length != 3) return false;
        ResourceLocation bookId = ResourceLocation.tryParse(parts[0]);
        FoliosBook book = bookId != null ? FoliosLibrary.book(bookId) : null;
        if (book == null) return false;
        if (parts[1].equals("-")) return FoliosClient.open(bookId, (FoliosBook.Location) null);
        FoliosEntry entry = book.entry(parts[1]);
        int page;
        try {
            page = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (entry == null || !entry.isOpen() || page < 0 || page >= entry.pages().size() || !entry.pages().get(page).lock().isOpen()) return false;
        return FoliosClient.open(bookId, new FoliosBook.Location(parts[1], page));
    }
}
