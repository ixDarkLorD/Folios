package net.ixdarklord.folios.internal.client.page;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.client.GuideEntry;
import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.api.client.page.GuidePage;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.api.client.page.GuideText;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

// Related entries, each a row with its icon that opens it. Entries the reader hasn't unlocked are left out.
public record LinksPage(List<Link> links, Optional<String> title, String text) implements GuidePage {
    /** An entry to link to (its id, with an optional {@code #anchor}), and the words shown for it; empty for its name. */
    public record Link(String entry, String label) {
        static final Codec<Link> CODEC = Codec.either(Codec.STRING, RecordCodecBuilder.<Link>create(instance -> instance.group(
                Codec.STRING.fieldOf("entry").forGetter(Link::entry),
                Codec.STRING.optionalFieldOf("label", "").forGetter(Link::label)
        ).apply(instance, Link::new))).xmap(either -> either.map(id -> new Link(id, ""), link -> link), Either::right);
    }

    public static final MapCodec<LinksPage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Link.CODEC.listOf(1, 12).fieldOf("links").forGetter(LinksPage::links),
            Codec.STRING.optionalFieldOf("title").forGetter(LinksPage::title),
            Codec.STRING.optionalFieldOf("text", "").forGetter(LinksPage::text)
    ).apply(instance, LinksPage::new));

    private static final int ROW = 20;

    @Override
    public List<Laid> lay(GuideLayout layout) {
        List<Link> shown = links.stream().filter(link -> {
            GuideEntry entry = layout.book().entry(id(link));
            return entry != null && entry.isVisible();
        }).toList();
        // Without a title of its own it is "See also", in the reader's language.
        return layout.flow(title.orElse("folios.links"), shown.size() * ROW, text, (painter, y) -> {
            GuidePalette palette = painter.palette();
            int top = y;
            for (Link link : shown) {
                GuideEntry entry = layout.book().entry(id(link));
                if (entry == null) continue;
                boolean open = entry.isOpen();
                boolean over = open && painter.link(GuideLayout.CONTENT_LEFT, top, GuideLayout.CONTENT_WIDTH, ROW - 2, "entry:" + link.entry);
                painter.fill(GuideLayout.CONTENT_LEFT, top, GuideLayout.CONTENT_RIGHT, top + ROW - 2,
                        over ? GuidePalette.alpha(palette.accent(), 0x30) : GuidePalette.alpha(palette.line(), 0x40));
                painter.fill(GuideLayout.CONTENT_LEFT, top, GuideLayout.CONTENT_LEFT + 2, top + ROW - 2, over ? palette.accent() : palette.line());
                entry.icon().draw(painter, GuideLayout.CONTENT_LEFT + 4, top + 1);
                Component label = link.label.isEmpty() ? entry.title() : GuideText.inline(link.label);
                if (!open) label = Component.literal("???");
                painter.text(label, GuideLayout.CONTENT_LEFT + 24, top + 5, over ? palette.heading() : open ? palette.text() : palette.muted());
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
        return GuideText.resolve(title.orElse("")) + " " + GuideText.resolve(text);
    }
}
