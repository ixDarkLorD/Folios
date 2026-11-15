package net.ixdarklord.folios.internal.client.paint;

import com.mojang.blaze3d.platform.NativeImage;
import net.ixdarklord.folios.internal.core.Folios;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Node;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

public final class GifStrips {
    private static final int MOST_FRAMES = 128;
    private static final int MOST_HEIGHT = 4096;
    private static final Map<ResourceLocation, Optional<Strip>> STRIPS = new HashMap<>();

    public record Strip(ResourceLocation texture, int width, int height, int[] delays, int total) {
        public int frames() {
            return delays.length;
        }

        public int frameAt(float seconds) {
            int at = (int) (seconds * 1000) % Math.max(1, total);
            for (int frame = 0; frame < delays.length; frame++) {
                at -= delays[frame];
                if (at < 0) return frame;
            }
            return delays.length - 1;
        }
    }

    private GifStrips() {}

    public static boolean isGif(ResourceLocation id) {
        return id.getPath().toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    public static @Nullable Strip get(ResourceLocation id) {
        return STRIPS.computeIfAbsent(id, key -> Optional.ofNullable(load(key))).orElse(null);
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Optional<Strip> strip : STRIPS.values()) strip.ifPresent(made -> minecraft.getTextureManager().release(made.texture()));
        STRIPS.clear();
    }

    private static @Nullable Strip load(ResourceLocation id) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open(); ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false);
                return strip(id, reader);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            Folios.LOGGER.warn("Couldn't read the GIF {}: {}", id, e.getMessage());
            return null;
        }
    }

    private static @Nullable Strip strip(ResourceLocation id, ImageReader reader) throws Exception {
        int count = Math.min(MOST_FRAMES, reader.getNumImages(true));
        if (count <= 0) return null;
        int width = 0;
        int height = 0;
        IIOMetadata streamData = reader.getStreamMetadata();
        if (streamData != null) {
            Node screen = child((IIOMetadataNode) streamData.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
            if (screen != null) {
                width = number(screen, "logicalScreenWidth", 0);
                height = number(screen, "logicalScreenHeight", 0);
            }
        }
        if (width <= 0 || height <= 0) {
            width = reader.getWidth(0);
            height = reader.getHeight(0);
        }
        count = Math.max(1, Math.min(count, MOST_HEIGHT / Math.max(1, height)));
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        List<int[]> frames = new ArrayList<>();
        int[] delays = new int[count];
        int total = 0;
        for (int index = 0; index < count; index++) {
            BufferedImage frame = reader.read(index);
            IIOMetadataNode tree = (IIOMetadataNode) reader.getImageMetadata(index).getAsTree("javax_imageio_gif_image_1.0");
            Node descriptor = child(tree, "ImageDescriptor");
            Node control = child(tree, "GraphicControlExtension");
            int left = descriptor == null ? 0 : number(descriptor, "imageLeftPosition", 0);
            int top = descriptor == null ? 0 : number(descriptor, "imageTopPosition", 0);
            String disposal = control == null ? "none" : control.getAttributes().getNamedItem("disposalMethod").getNodeValue();
            delays[index] = Math.max(20, (control == null ? 10 : number(control, "delayTime", 10)) * 10);
            total += delays[index];
            BufferedImage before = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
            Graphics2D graphics = canvas.createGraphics();
            graphics.drawImage(frame, left, top, null);
            graphics.dispose();
            frames.add(canvas.getRGB(0, 0, width, height, null, 0, width));
            if (disposal.equals("restoreToBackgroundColor")) {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(AlphaComposite.Clear);
                clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
                clear.dispose();
            } else if (before != null) {
                canvas = before;
            }
        }
        NativeImage image = new NativeImage(width, height * frames.size(), true);
        for (int index = 0; index < frames.size(); index++) {
            int[] pixels = frames.get(index);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) image.setPixelRGBA(x, index * height + y, abgr(pixels[y * width + x]));
            }
        }
        ResourceLocation texture = Folios.rl("gif/" + Integer.toHexString(id.hashCode()));
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(image));
        return new Strip(texture, width, height, delays, total);
    }

    private static int abgr(int argb) {
        return argb & 0xFF00FF00 | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static @Nullable Node child(IIOMetadataNode root, String name) {
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equals(name)) return node;
        }
        return null;
    }

    private static int number(Node node, String attribute, int fallback) {
        Node value = node.getAttributes().getNamedItem(attribute);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.getNodeValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
