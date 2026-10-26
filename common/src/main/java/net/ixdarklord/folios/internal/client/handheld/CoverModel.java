package net.ixdarklord.folios.internal.client.handheld;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.ixdarklord.folios.internal.compat.SubmitNodeCollector;
import net.ixdarklord.folios.internal.compat.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// What sits on a handheld book's boards: corner caps, clasps, bands. A model in the format of block and item models
// ("elements" of boxes, each face with its texture and uv, a box turned about an axis if it says so), so it can be
// made in any editor for those, read from assets/<namespace>/models/<path>.json by its id. It is laid on each board's
// outside, mirrored on the left one: x from 0 at the spine to 16 at the board's outer edge, y from 0 at its foot to
// 16 at its head, z from 0 on the board outwards, in the same units as x. A face with "tintindex" 0 takes the cover's
// colour, 1 the book's accent. A box with "board": "front" sits only on the front board (the cover with the book's name), "back" only on
// the back one. A box whose name has "#shut" in it holds the book shut (a strap or clasp): as the book opens it
// unlatches first, swinging out about its board's fore-edge before the cover moves, and is carried off behind the
// boards as the cover opens, gone once the book lies open. One with "#open" shows only once it lies open. One with "#icon"
// is the gem the book's cover icon is set in (CoverEmblem), drawn as it is only for a book without one. A box with "#spine" in its
// name is wrapped round the spine instead, bending with it as the book opens (a band across it): only its height
// (y), how far it stands out (its "to" z) and its "west" face's texture are used. Models are read once and kept
// until resources reload.
public final class CoverModel {
    private static final Map<ResourceLocation, Optional<CoverModel>> LOADED = new HashMap<>();

    // `tint`: -1 for none, 0 the cover's colour, 1 the book's accent.
    private record Face(ResourceLocation texture, Vector3f[] corners, float[] uvs, int tint, Vector3f normal, int board, @Nullable Turn turn, int state, boolean gem) {}

    /** The "#icon" gem: its middle across and up, its front face's z, its width, and which boards it is on. */
    private record Gem(float x, float y, float z, float side, int board) {}

    /** When a box shows: always, only shut, only open. */
    private static final int ALWAYS = 0;
    private static final int SHUT = 1;
    private static final int OPEN = 2;

    // A box's turn about its origin, as its "rotation" says. It is made in the board's own proportions, not in the
    // model's square units (the board is taller than it is wide), so a square turned on it stays square.
    private record Turn(float[] origin, String axis, float angle) {
        Matrix4f on(float sx, float sy, float sz) {
            Matrix4f matrix = new Matrix4f().translate(origin[0], origin[1], origin[2]).scale(1 / sx, 1 / sy, 1 / sz);
            switch (axis) {
                case "x" -> matrix.rotateX(angle);
                case "y" -> matrix.rotateY(angle);
                default -> matrix.rotateZ(angle);
            }
            return matrix.scale(sx, sy, sz).translate(-origin[0], -origin[1], -origin[2]);
        }
    }

    /** How much of a book's opening its latch takes, unlatching before its cover swings. */
    public static final float LATCH = 0.3F;
    /** How far a "#shut" box swings out about its board's fore-edge once unlatched. */
    private static final float LATCH_SWING = (float) Math.toRadians(100);

    /** How far the cover has swung open, 0 to 1, when the book is {@code open} that far (the latch goes first). */
    public static float swing(float open) {
        return Math.max(0, Math.min(1, (open - LATCH) / (1 - LATCH)));
    }

    /** Which boards a box sits on. */
    private static final int BOTH = 0;
    private static final int FRONT = 1;
    private static final int BACK = 2;

    /**
     * A band round the spine: from {@code y0} to {@code y1} up it (0 at the foot, 16 at the head), standing {@code out}
     * off it (in x's units), its face from that part of {@code texture} (0 to 1), tinted as a face's "tintindex" says.
     */
    public record Band(ResourceLocation texture, float y0, float y1, float out, float u0, float v0, float u1, float v1, int tint) {}

    private final Map<ResourceLocation, List<Face>> byTexture;
    private final List<Band> bands;
    private final @Nullable Gem gem;

    private CoverModel(Map<ResourceLocation, List<Face>> byTexture, List<Band> bands, @Nullable Gem gem) {
        this.byTexture = byTexture;
        this.bands = bands;
        this.gem = gem;
    }

    /** The bands round the spine. */
    public List<Band> bands() {
        return bands;
    }

    public static void clear() {
        LOADED.clear();
    }

    /** The model with that id, read the first time it is asked for; null when there is none or it can't be read. */
    public static @Nullable CoverModel get(ResourceLocation id) {
        return LOADED.computeIfAbsent(id, CoverModel::read).orElse(null);
    }

    private static Optional<CoverModel> read(ResourceLocation id) {
        ResourceLocation file = id.withPath(path -> "models/" + path + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            Folios.LOGGER.warn("There is no guide cover model {}", file);
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(parse(JsonParser.parseReader(reader).getAsJsonObject()));
        } catch (Exception e) {
            Folios.LOGGER.error("Couldn't read the guide cover model {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static CoverModel parse(JsonObject json) {
        Map<String, String> textures = new HashMap<>();
        if (json.has("textures")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) textures.put(entry.getKey(), entry.getValue().getAsString());
        }
        Map<ResourceLocation, List<Face>> faces = new LinkedHashMap<>();
        List<Band> bands = new ArrayList<>();
        Gem gem = null;
        if (!json.has("elements")) return new CoverModel(faces, bands, null);
        for (JsonElement element : json.getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            float[] from = floats(box, "from", 3);
            float[] to = floats(box, "to", 3);
            Turn turn = turn(box);
            int board = !box.has("board") ? BOTH : box.get("board").getAsString().equals("front") ? FRONT : BACK;
            String name = box.has("name") ? box.get("name").getAsString() : "";
            int state = name.contains("#shut") ? SHUT : name.contains("#open") ? OPEN : ALWAYS;
            JsonObject sides = box.getAsJsonObject("faces");
            boolean isGem = name.contains("#icon");
            if (isGem) gem = new Gem((from[0] + to[0]) / 2, (from[1] + to[1]) / 2, Math.max(from[2], to[2]), Math.abs(to[0] - from[0]), board);
            if (name.contains("#spine")) {
                JsonObject face = sides.has("west") ? sides.getAsJsonObject("west") : sides.has("south") ? sides.getAsJsonObject("south") : null;
                ResourceLocation texture = face != null ? texture(face.get("texture").getAsString(), textures) : null;
                if (texture == null) continue;
                float[] uv = face.has("uv") ? floats(face, "uv", 4) : new float[]{0, 0, 16, 16};
                bands.add(new Band(texture, Math.min(from[1], to[1]), Math.max(from[1], to[1]), Math.max(0, to[2]),
                        uv[0] / 16, uv[1] / 16, uv[2] / 16, uv[3] / 16, face.has("tintindex") ? face.get("tintindex").getAsInt() : -1));
                continue;
            }
            for (Map.Entry<String, JsonElement> side : sides.entrySet()) {
                JsonObject face = side.getValue().getAsJsonObject();
                ResourceLocation texture = texture(face.get("texture").getAsString(), textures);
                if (texture == null) continue;
                Face built = face(side.getKey(), from, to, face, texture, turn, board, state, isGem);
                if (built != null) faces.computeIfAbsent(texture, key -> new ArrayList<>()).add(built);
            }
        }
        return new CoverModel(faces, bands, gem);
    }

    // A texture reference followed through "#name"s to an id, then to its file.
    private static @Nullable ResourceLocation texture(String reference, Map<String, String> textures) {
        String name = reference;
        for (int step = 0; step < 8 && name.startsWith("#"); step++) name = textures.get(name.substring(1));
        if (name == null || name.startsWith("#")) return null;
        ResourceLocation id = ResourceLocation.parse(name);
        return id.withPath(path -> "textures/" + path + ".png");
    }

    private static float[] floats(JsonObject json, String key, int count) {
        float[] values = new float[count];
        var array = json.getAsJsonArray(key);
        for (int index = 0; index < count; index++) values[index] = array.get(index).getAsFloat();
        return values;
    }

    // A box's turn, as its "rotation" says; null when it says nothing.
    private static @Nullable Turn turn(JsonObject box) {
        if (!box.has("rotation")) return null;
        JsonObject rotation = box.getAsJsonObject("rotation");
        return new Turn(floats(rotation, "origin", 3), rotation.get("axis").getAsString(), (float) Math.toRadians(rotation.get("angle").getAsFloat()));
    }

    // One face of a box: its corners in the order block models give them, and their uv, from the face's own "uv" or
    // the box's place as block models work it out.
    private static @Nullable Face face(String side, float[] from, float[] to, JsonObject json, ResourceLocation texture, @Nullable Turn turn, int board, int state, boolean gem) {
        float x0 = from[0], y0 = from[1], z0 = from[2], x1 = to[0], y1 = to[1], z1 = to[2];
        float[][] corners;
        float[] uv;
        Vector3f normal;
        switch (side) {
            case "down" -> {
                corners = new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                uv = new float[]{x0, 16 - z1, x1, 16 - z0};
                normal = new Vector3f(0, -1, 0);
            }
            case "up" -> {
                corners = new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                uv = new float[]{x0, z0, x1, z1};
                normal = new Vector3f(0, 1, 0);
            }
            case "north" -> {
                corners = new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                uv = new float[]{16 - x1, 16 - y1, 16 - x0, 16 - y0};
                normal = new Vector3f(0, 0, -1);
            }
            case "south" -> {
                corners = new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                uv = new float[]{x0, 16 - y1, x1, 16 - y0};
                normal = new Vector3f(0, 0, 1);
            }
            case "west" -> {
                corners = new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                uv = new float[]{z0, 16 - y1, z1, 16 - y0};
                normal = new Vector3f(-1, 0, 0);
            }
            case "east" -> {
                corners = new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
                uv = new float[]{16 - z1, 16 - y1, 16 - z0, 16 - y0};
                normal = new Vector3f(1, 0, 0);
            }
            default -> {
                return null;
            }
        }
        if (json.has("uv")) uv = floats(json, "uv", 4);
        // Each corner's uv, turned as the face's "rotation" says.
        float[][] uvs = {{uv[0], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}};
        int steps = json.has("rotation") ? Math.floorMod(json.get("rotation").getAsInt() / 90, 4) : 0;
        float[] flat = new float[8];
        Vector3f[] points = new Vector3f[4];
        for (int corner = 0; corner < 4; corner++) {
            float[] at = uvs[(corner + steps) % 4];
            flat[corner * 2] = at[0] / 16.0F;
            flat[corner * 2 + 1] = at[1] / 16.0F;
            points[corner] = new Vector3f(corners[corner][0], corners[corner][1], corners[corner][2]);
        }
        return new Face(texture, points, flat, json.has("tintindex") ? json.get("tintindex").getAsInt() : -1, normal, board, turn, state, gem);
    }

    /**
     * Draws it on a board's outside. The pose is the book's (page pixels: x right from the spine, y down, z towards
     * the reader); {@code outside} is the z of the board's outer face, the board {@code width} across and
     * {@code top} to {@code bottom} tall; {@code left} mirrors it for the left board. {@code open} is how far the book
     * is open, 0 shut to 1, for the boxes shown only shut or only open.
     */
    public void draw(PoseStack pose, SubmitNodeCollector collector, int light, boolean left, float width, float top, float bottom, float outside, int cover, int accent, float open, org.joml.@Nullable Matrix3f relight) {
        this.draw(pose, collector, light, left, width, top, bottom, outside, cover, accent, open, relight, net.ixdarklord.folios.api.client.page.FoliosIcon.NONE, 1);
    }

    /** As the draw above, with {@code icon} set in its "#icon" gem (the gem itself left out), {@code iconSize} times its usual size. */
    public void draw(PoseStack pose, SubmitNodeCollector collector, int light, boolean left, float width, float top, float bottom, float outside, int cover, int accent, float open,
                     org.joml.@Nullable Matrix3f relight, net.ixdarklord.folios.api.client.page.FoliosIcon icon, float iconSize) {
        boolean inset = gem != null && !icon.isEmpty();
        boolean opened = open >= 0.999F;
        // The latch: "#shut" boxes swing out about the board's fore-edge (x 16, on its face) as the book starts to open.
        float unlatch = Math.max(0, Math.min(1, open / LATCH));
        float eased = 1 - (1 - unlatch) * (1 - unlatch);
        Matrix4f latch = new Matrix4f().translate(16, 0, 0).rotateY(eased * LATCH_SWING).translate(-16, 0, 0);
        Matrix4f place = new Matrix4f(pose.last().pose());
        float sign = left ? -1 : 1;
        // Model units to page pixels: x along the board, y up it (page y runs down), z away from the reader.
        place.translate(0, bottom, outside);
        place.scale(sign * width / 16.0F, -(bottom - top) / 16.0F, -width / 16.0F);
        // Mirrored or not, the faces' corners must turn the same way as seen from outside.
        boolean flipped = place.determinant() < 0;
        // A model unit in page pixels, each way: what a box's turn is made in.
        float sx = width / 16.0F;
        float sy = (bottom - top) / 16.0F;
        for (Map.Entry<ResourceLocation, List<Face>> group : byTexture.entrySet()) {
            List<Face> faces = group.getValue();
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(group.getKey()), (last, consumer) -> {
                for (Face face : faces) {
                    // The left board is the front one: shut, it is the cover.
                    if (face.board == FRONT && !left || face.board == BACK && left) continue;
                    if (face.state == SHUT && opened || face.state == OPEN && !opened) continue;
                    if (face.gem && inset) continue;
                    Matrix4f at = face.state == SHUT && unlatch > 0 ? new Matrix4f(place).mul(latch) : place;
                    if (face.turn != null) at = new Matrix4f(at).mul(face.turn.on(sx, sy, sx));
                    // The face's normal carried as its corners are (a mirror turns it with them), for the light.
                    Vector3f normal = at.transformDirection(face.normal, new Vector3f()).normalize();
                    if (relight != null) relight.transform(normal).normalize();
                    int color = face.tint == 0 ? cover : face.tint == 1 ? accent : 0xFFFFFFFF;
                    for (int index = 0; index < 4; index++) {
                        int corner = flipped ? 3 - index : index;
                        Vector3f point = at.transformPosition(face.corners[corner], new Vector3f());
                        emit(consumer, point, face.uvs[corner * 2], face.uvs[corner * 2 + 1], color, light, normal);
                    }
                }
            });
        }
        if (inset && !(gem.board == FRONT && !left || gem.board == BACK && left)) {
            pose.pushPose();
            pose.translate(0, bottom, outside);
            pose.scale(sign * width / 16.0F, -(bottom - top) / 16.0F, -width / 16.0F);
            pose.translate(gem.x, gem.y, gem.z + 0.01F);
            // Mirrored, as the left board is, the icon would read backwards: turned back the right way round.
            if (flipped) pose.scale(-1, 1, 1);
            net.ixdarklord.folios.internal.client.CoverEmblem.draw(pose, collector, light, icon, gem.side, iconSize, accent);
            pose.popPose();
        }
    }

    private static void emit(VertexConsumer consumer, Vector3f at, float u, float v, int color, int light, Vector3f normal) {
        consumer.addVertex(at.x, at.y, at.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x, normal.y, normal.z);
    }
}
