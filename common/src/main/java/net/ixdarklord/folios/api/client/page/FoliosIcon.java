package net.ixdarklord.folios.api.client.page;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.ixdarklord.folios.internal.compat.GuiGraphicsExtractor;
import net.ixdarklord.folios.internal.compat.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A book's, category's or entry's 16-pixel icon: items ({@link FoliosStack}, e.g. {@code "minecraft:compass"}), or a
 * texture, named by its path ({@code "mymod:textures/gui/guide/compass.png"} or {@code {"texture": "..."}}).
 */
public final class FoliosIcon {
    public static final FoliosIcon NONE = new FoliosIcon(null, null);

    private final @Nullable FoliosStack stack;
    private final @Nullable ResourceLocation texture;

    private FoliosIcon(@Nullable FoliosStack stack, @Nullable ResourceLocation texture) {
        this.stack = stack;
        this.texture = texture;
    }

    public static FoliosIcon of(FoliosStack stack) {
        return new FoliosIcon(stack, null);
    }

    public static FoliosIcon texture(ResourceLocation texture) {
        return new FoliosIcon(null, texture);
    }

    /** Reads an icon from JSON; {@link #NONE} for null. */
    public static FoliosIcon read(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) return NONE;
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("texture")) return texture(new ResourceLocation(object.get("texture").getAsString()));
            if (object.has("item")) return of(FoliosStack.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, object.get("item")).getOrThrow(false, error -> {}));
        }
        if (json.isJsonPrimitive()) {
            String text = json.getAsString();
            if (text.endsWith(".png")) return texture(new ResourceLocation(text));
        }
        return of(FoliosStack.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow(false, error -> {}));
    }

    public boolean isEmpty() {
        return stack == null && texture == null;
    }

    public @Nullable FoliosStack stack() {
        return stack;
    }

    public @Nullable ResourceLocation texture() {
        return texture;
    }

    /** Draws it on a page, 16 pixels square at (x, y). */
    public void draw(FoliosPainter painter, int x, int y) {
        if (texture != null) painter.texture(texture, x, y, 16, 16, 0, 0, 1, 1, 0xFFFFFFFF);
        else if (stack != null) painter.item(stack.at(painter.time()), x, y);
    }

    /** Draws it on a screen, {@code size} pixels square at (x, y), with no tooltip. */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int size, float seconds) {
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, size, size, size, size);
        } else if (stack != null) {
            if (size == 16) {
                graphics.fakeItem(stack.at(seconds), x, y);
            } else {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y);
                graphics.pose().scale(size / 16.0F, size / 16.0F);
                graphics.fakeItem(stack.at(seconds), 0, 0);
                graphics.pose().popMatrix();
            }
        }
    }
}
