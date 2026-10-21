package net.ixdarklord.folios.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ixdarklord.folios.api.client.GuideBook;
import net.ixdarklord.folios.api.client.page.GuidePalette;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

// The name and subtitle of a book, in gilt, on its item model's cover, so a book held or lying about shows what it is.
// Placed in the model's own units (0 to 16): "x" the middle of the lines, "titleY" and "subtitleY" each line's middle,
// "z" the face they lie on (facing +z), "width" the room across, which longer lines shrink to fit. With "markers", a
// model whose elements mark the lettering (CoverMarkers), the lines go where it marks them instead; a book's own
// "cover_text" wins over both.
public record GuideCoverText(float x, float titleY, float subtitleY, float z, float width, @Nullable ResourceLocation markers) {
    /** On Folios's own book model, where its markers put it. */
    public static final GuideCoverText DEFAULT = new GuideCoverText(8.0F, 11.5F, 4.25F, 10.21F, 8.5F, Folios.rl("item/guide_book_3d"));

    public void submit(@Nullable ResourceLocation id, PoseStack pose, SubmitNodeCollector collector, int light) {
        GuideBook book = id != null ? GuideLibrary.book(id) : null;
        if (book == null) return;
        Font font = Minecraft.getInstance().font;
        int cover = 0xFF000000 | book.cover();
        int gilt = GuidePalette.mix(0xFFE8C66A, cover, 0.15F);
        // Where the book says, or where the model marks them, or where this renderer puts them.
        CoverMarkers.Markers marked = markers != null ? CoverMarkers.of(markers) : CoverMarkers.Markers.NONE;
        for (CoverMarkers.Lettering lettering : CoverMarkers.layout(book, marked, new GuideBook.CoverText.Line(x, titleY, GuideBook.CoverText.TITLE.size(), width),
                new GuideBook.CoverText.Line(x, subtitleY, GuideBook.CoverText.SUBTITLE.size(), width), z)) {
            if (lettering.kind() == CoverMarkers.Kind.RULE) this.rule(pose, collector, lettering, gilt, cover, light);
            else this.lines(pose, collector, font, lettering, CoverMarkers.color(lettering.kind(), gilt, cover), light);
        }
    }

    // A soft rule fading to its ends, a diamond stud at its middle, in gilt.
    private void rule(PoseStack pose, SubmitNodeCollector collector, CoverMarkers.Lettering lettering, int gilt, int cover, int light) {
        // Faded toward the plate's own dark leather, solid: a see-through face over the item showed what was behind it.
        int plate = GuidePalette.mix(cover, 0xFF000000, 0.55F);
        GuideBook.CoverText.Line line = lettering.line();
        float unit = 1 / 16.0F;
        float[][] runs = CoverMarkers.rule(line);
        float thick = line.size() / 2;
        float stud = runs[runs.length - 1][0];
        pose.pushPose();
        pose.translate(line.x() * unit, line.y() * unit, (lettering.z() + 0.005F) * unit);
        pose.scale(unit, unit, unit);
        collector.submitCustomGeometry(pose, net.ixdarklord.folios.internal.compat.RenderTypes.entityCutout(net.ixdarklord.folios.internal.client.paint.WorldPainter.WHITE), (last, consumer) -> {
            for (int index = 0; index < runs.length - 1; index++) {
                float[] run = runs[index];
                if (run[2] < 0.05F) continue;
                int color = GuidePalette.mix(plate, gilt, run[2]);
                quad(consumer, last, run[0], -thick, run[1], -thick, run[1], thick, run[0], thick, color, light);
            }
            quad(consumer, last, 0, -stud, stud, 0, 0, stud, -stud, 0, gilt, light);
        });
        pose.popPose();
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack.Pose last, float ax, float ay, float bx, float by, float cx, float cy,
                             float dx, float dy, int color, int light) {
        for (float[] p : new float[][]{{ax, ay}, {bx, by}, {cx, cy}, {dx, dy}}) {
            consumer.vertex(last.pose(), p[0], p[1], 0).color(color).uv(0.5F, 0.5F).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(light).normal(last.normal(), 0, 0, 1).endVertex();
        }
    }

    // A lettering's lines, centred on its place, shrunk to the room across.
    private void lines(PoseStack pose, SubmitNodeCollector collector, Font font, CoverMarkers.Lettering lettering, int color, int light) {
        GuideBook.CoverText.Line line = lettering.line();
        float unit = 1 / 16.0F;
        CoverMarkers.Rows rows = CoverMarkers.rows(font, lettering);
        pose.pushPose();
        pose.translate(line.x() * unit, line.y() * unit, lettering.z() * unit);
        float scale = rows.scale() * unit;
        pose.scale(scale, -scale, scale);
        for (int index = 0; index < rows.lines().size(); index++) {
            FormattedCharSequence row = rows.lines().get(index);
            collector.submitText(pose, -font.width(row) / 2.0F, rows.top() + index * 9, row, false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
        }
        pose.popPose();
    }

}
