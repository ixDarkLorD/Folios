package net.ixdarklord.folios.internal.client.page;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.FoliosEntry;
import net.ixdarklord.folios.api.client.page.FoliosLayout;
import net.ixdarklord.folios.api.client.page.FoliosPageElement;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.api.client.page.FoliosText;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

// Related entries, each a row with its icon that opens it. Entries the reader hasn't unlocked are left out.
public record LinksPageElement(List<Link> links, Optional<String> title, String text) implements FoliosPageElement {
    /** An entry to link to (its id, with an optional {@code #marker}), and the words shown for it; empty for its name. */
    public record Link(String entry, String label) {
        static final Codec<Link> CODEC = Codec.either(Codec.STRING, RecordCodecBuilder.<Link>create(instance -> instance.group(
                Codec.STRING.fieldOf("entry").forGetter(Link::entry),
                FoliosText.CODEC.optionalFieldOf("label", "").forGetter(Link::label)
        ).apply(instance, Link::new))).xmap(either -> either.map(id -> new Link(id, ""), link -> link), Either::right);
    }

    public static final MapCodec<LinksPageElement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Link.CODEC.listOf(1, 12).fieldOf("links").forGetter(LinksPageElement::links),
            FoliosText.CODEC.optionalFieldOf("title").forGetter(LinksPageElement::title),
            FoliosText.CODEC.optionalFieldOf("text", "").forGetter(LinksPageElement::text)
    ).apply(instance, LinksPageElement::new));

    private static final int ROW = 20;

    @Override
    public List<Laid> lay(FoliosLayout layout) {
        List<Link> shown = links.stream().filter(link -> {
            FoliosEntry entry = layout.book().entry(id(link));
            return entry != null && entry.isVisible();
        }).toList();
        // Without a title of its own it is "See also", in the reader's language.
        return layout.flow(title.orElse("folios.links"), shown.size() * ROW, text, (painter, y) -> {
            FoliosPalette palette = painter.palette();
            int top = y;
            for (Link link : shown) {
                FoliosEntry entry = layout.book().entry(id(link));
                if (entry == null) continue;
                boolean open = entry.isOpen();
                boolean over = open && painter.link(FoliosLayout.CONTENT_LEFT, top, painter.contentRight() - FoliosLayout.CONTENT_LEFT, ROW - 2, "entry:" + link.entry);
                painter.fill(FoliosLayout.CONTENT_LEFT, top, painter.contentRight(), top + ROW - 2,
                        over ? FoliosPalette.alpha(palette.accent(), 0x30) : FoliosPalette.alpha(palette.line(), 0x40));
                painter.fill(FoliosLayout.CONTENT_LEFT, top, FoliosLayout.CONTENT_LEFT + 2, top + ROW - 2, over ? palette.accent() : palette.line());
                entry.icon().draw(painter, FoliosLayout.CONTENT_LEFT + 4, top + 1);
                Component label = link.label.isEmpty() ? entry.title() : FoliosText.inline(link.label);
                if (!open) label = Component.literal("???");
                painter.text(label, FoliosLayout.CONTENT_LEFT + 24, top + 5, over ? palette.heading() : open ? palette.text() : palette.muted());
                top += ROW;
            }
        });
    }

    private static String id(Link link) {
        int hash = link.entry.indexOf('#');
        return hash < 0 ? link.entry : link.entry.substring(0, hash);
    }

    @Override
    public String searchText() {
        return FoliosText.resolve(title.orElse("")) + " " + FoliosText.resolve(text);
    }
}
