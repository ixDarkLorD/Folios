package net.ixdarklord.folios.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ixdarklord.folios.api.FoliosBooks;
import net.ixdarklord.folios.api.client.FoliosBook;
import net.ixdarklord.folios.api.client.page.FoliosPalette;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

// The "folios:guide_cover_text" special item model: the name and subtitle of the stack's book, in gilt, on a
// model's cover, so a book held or lying about shows what it is. Placed in the model's own units (0 to 16): "x" the
// middle of the lines, "title_y" and "subtitle_y" each line's middle, "z" the face they lie on (facing +z), "width" the
// room across, which longer lines shrink to fit. Used beside the book's model in a composite item model. With "markers",
// a model whose elements mark the lettering (CoverMarkers), the lines go where it marks them instead; a book's own
// "cover_text" wins over both.
public record FoliosCoverText(float x, float titleY, float subtitleY, float z, float width, @Nullable Identifier markers) implements SpecialModelRenderer<Identifier> {
    public static final Identifier TYPE = Folios.rl("guide_cover_text");

    @Override
    public void submit(@Nullable Identifier id, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        FoliosBook book = id != null ? FoliosLibrary.book(id) : null;
        if (book == null) return;
        Font font = Minecraft.getInstance().font;
        int cover = 0xFF000000 | book.cover();
        int gilt = FoliosPalette.mix(0xFFE8C66A, cover, 0.15F);
        // Where the book says, or where the model marks them, or where this renderer puts them.
        CoverMarkers.Markers marked = markers != null ? CoverMarkers.of(markers) : CoverMarkers.Markers.NONE;
        for (CoverMarkers.Lettering lettering : CoverMarkers.layout(book, marked, new FoliosBook.CoverText.Line(x, titleY, FoliosBook.CoverText.TITLE.size(), width),
                new FoliosBook.CoverText.Line(x, subtitleY, FoliosBook.CoverText.SUBTITLE.size(), width), z)) {
            if (lettering.kind() == CoverMarkers.Kind.RULE) this.rule(pose, collector, lettering, gilt, cover, light);
            else this.lines(pose, collector, font, lettering, CoverMarkers.color(lettering.kind(), gilt, cover), light);
        }
    }

    // A soft rule fading to its ends, a diamond stud at its middle, in gilt.
    private void rule(PoseStack pose, SubmitNodeCollector collector, CoverMarkers.Lettering lettering, int gilt, int cover, int light) {
        // Faded toward the plate's own dark leather, solid: a see-through face over the item showed what was behind it.
        int plate = FoliosPalette.mix(cover, 0xFF000000, 0.55F);
        FoliosBook.CoverText.Line line = lettering.line();
        float unit = 1 / 16.0F;
        float[][] runs = CoverMarkers.rule(line);
        float thick = line.size() / 2;
        float stud = runs[runs.length - 1][0];
        pose.pushPose();
        pose.translate(line.x() * unit, line.y() * unit, (lettering.z() + 0.005F) * unit);
        pose.scale(unit, unit, unit);
        collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(net.ixdarklord.folios.internal.client.paint.WorldPainter.WHITE), (last, consumer) -> {
            for (int index = 0; index < runs.length - 1; index++) {
                float[] run = runs[index];
                if (run[2] < 0.05F) continue;
                int color = FoliosPalette.mix(plate, gilt, run[2]);
                quad(consumer, last, run[0], -thick, run[1], -thick, run[1], thick, run[0], thick, color, light);
            }
            quad(consumer, last, 0, -stud, stud, 0, 0, stud, -stud, 0, gilt, light);
        });
        pose.popPose();
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer consumer, PoseStack.Pose last, float ax, float ay, float bx, float by, float cx, float cy,
                             float dx, float dy, int color, int light) {
        for (float[] p : new float[][]{{ax, ay}, {bx, by}, {cx, cy}, {dx, dy}}) {
            consumer.addVertex(last, p[0], p[1], 0).setColor(color).setUv(0.5F, 0.5F).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(last, 0, 0, 1);
        }
    }

    // A lettering's lines, centred on its place, shrunk to the room across.
    private void lines(PoseStack pose, SubmitNodeCollector collector, Font font, CoverMarkers.Lettering lettering, int color, int light) {
        FoliosBook.CoverText.Line line = lettering.line();
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

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float unit = 1 / 16.0F;
        output.accept(new Vector3f((x - width / 2) * unit, subtitleY * unit, z * unit));
        output.accept(new Vector3f((x + width / 2) * unit, titleY * unit, z * unit));
    }

    @Override
    public @Nullable Identifier extractArgument(ItemStack stack) {
        return FoliosBooks.bookOf(stack);
    }

    public record Unbaked(float x, float titleY, float subtitleY, float z, float width, java.util.Optional<Identifier> markers) implements SpecialModelRenderer.Unbaked<Identifier> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("x", 8.0F).forGetter(Unbaked::x),
                Codec.FLOAT.optionalFieldOf("title_y", 11.5F).forGetter(Unbaked::titleY),
                Codec.FLOAT.optionalFieldOf("subtitle_y", 4.25F).forGetter(Unbaked::subtitleY),
                Codec.FLOAT.optionalFieldOf("z", 10.21F).forGetter(Unbaked::z),
                Codec.FLOAT.optionalFieldOf("width", 8.5F).forGetter(Unbaked::width),
                Identifier.CODEC.optionalFieldOf("markers").forGetter(Unbaked::markers)
        ).apply(instance, Unbaked::new));

        @Override
        public SpecialModelRenderer<Identifier> bake(SpecialModelRenderer.BakingContext context) {
            return new FoliosCoverText(x, titleY, subtitleY, z, width, markers.orElse(null));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
