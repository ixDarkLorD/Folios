package net.ixdarklord.folios.api.client;

import net.ixdarklord.folios.api.client.page.GuideLayout;
import net.ixdarklord.folios.internal.core.Folios;
import net.ixdarklord.folios.api.client.page.GuidePainter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reveal effects books can use ({@link GuideReveal}), by id. CoolCatLib's own:
 * <ul>
 *     <li>{@code folios:rise}, the default: each part fades in as it rises a few pixels into place, from the top
 *     of the page down</li>
 *     <li>{@code folios:fade}: everything fades in together</li>
 *     <li>{@code folios:ink}: the page fills in from top to bottom like ink soaking down it, with a soft edge</li>
 *     <li>{@code folios:sweep}: the page fills in from its spine edge outwards</li>
 *     <li>{@code folios:scatter}: parts drift in from a little way off, each from its own side, settling as
 *     they fade in</li>
 *     <li>{@code folios:burn}: each thing on the page is burnt into it in turn, down the page and along each line:
 *     it flares white-hot, burns orange with flames licking up off it, chars and settles into its own colour, with
 *     sparks and smoke rising off what burns</li>
 *     <li>{@code folios:runes}: each part first shows as glowing runes that flicker and settle into its own words,
 *     while runes drift up the page and a shimmer of light crosses it</li>
 *     <li>{@code none}: shown at once</li>
 * </ul>
 */
public final class GuideReveals {
    public static final ResourceLocation NONE = Folios.rl("none");
    public static final ResourceLocation RISE = Folios.rl("rise");
    public static final ResourceLocation FADE = Folios.rl("fade");
    public static final ResourceLocation INK = Folios.rl("ink");
    public static final ResourceLocation SWEEP = Folios.rl("sweep");
    public static final ResourceLocation SCATTER = Folios.rl("scatter");
    public static final ResourceLocation BURN = Folios.rl("burn");
    public static final ResourceLocation RUNES = Folios.rl("runes");
    /** The reveal a book has when book.json doesn't say. */
    public static final GuideReveal.Use DEFAULT = new GuideReveal.Use(RISE, 0.45F);
    /** How long an effect takes when book.json names it without a length. */
    public static final float DEFAULT_SECONDS = 0.45F;

    private static final Map<ResourceLocation, GuideReveal> EFFECTS = new ConcurrentHashMap<>();

    static {
        register(NONE, (progress, x, y) -> GuideReveal.Look.SHOWN);
        register(FADE, (progress, x, y) -> new GuideReveal.Look(GuideReveal.smooth(progress), 0, 0));
        register(RISE, (progress, x, y) -> {
            float local = GuideReveal.stagger(progress, y / GuideLayout.PAGE_HEIGHT, 0.55F);
            return new GuideReveal.Look(GuideReveal.smooth(local), 0, (1 - GuideReveal.easeOut(local)) * 6);
        });
        register(INK, (progress, x, y) -> {
            // A soft edge a fifth of the page tall sweeping down past the foot.
            float edge = progress * 1.2F - 0.2F;
            return new GuideReveal.Look(GuideReveal.smooth((edge - y / GuideLayout.PAGE_HEIGHT) / 0.2F + 1), 0, 0);
        });
        register(SWEEP, (progress, x, y) -> {
            // Out from the fold to both outer edges.
            float local = GuideReveal.stagger(progress, Math.abs(x - GuideLayout.PAGE_WIDTH) / GuideLayout.PAGE_WIDTH, 0.6F);
            return new GuideReveal.Look(GuideReveal.smooth(local), 0, 0);
        });
        register(SCATTER, (progress, x, y) -> {
            // Each part's own direction from where it is: a cheap hash of its place.
            int hash = (Math.round(x) * 73856093) ^ (Math.round(y) * 19349663);
            float angle = (hash & 1023) / 1023.0F * (float) (Math.PI * 2);
            float local = GuideReveal.stagger(progress, ((hash >>> 10) & 255) / 255.0F, 0.45F);
            float away = (1 - GuideReveal.easeOut(local)) * 10;
            return new GuideReveal.Look(GuideReveal.smooth(local), (float) Math.cos(angle) * away, (float) Math.sin(angle) * away);
        });
        register(BURN, new Burn());
        register(RUNES, new Runes());
    }

    private GuideReveals() {}

    // ---- Burn ----

    // Each thing on the page is burnt into it in turn, down the page and along each line, left to right: it flares
    // white-hot, burns orange with flames licking up off it, chars dark and settles into its own colour. Sparks and a
    // thin smoke rise off what burns.
    private static final class Burn implements GuideReveal {
        private static final int HOT = 0xFFF2B8;
        private static final int EMBER = 0xFF7A1A;
        private static final int CHAR = 0x3A2410;
        // How much of the reveal the starts are spread over; the rest is each thing's own burning.
        private static final float SPREAD = 0.66F;
        // How long, of its own burning, a thing is aflame.
        private static final float AFLAME = 0.32F;

        // Where a place comes in the order: down the page, and along each line.
        private static float place(float x, float y) {
            float across = (x % GuideLayout.PAGE_WIDTH) / GuideLayout.PAGE_WIDTH;
            return Math.min(1, y / GuideLayout.PAGE_HEIGHT * 0.8F + across * 0.2F);
        }

        private static float own(float progress, float x, float y) {
            return GuideReveal.stagger(progress, place(x, y), SPREAD);
        }

        @Override
        public Look look(float progress, float x, float y) {
            float t = own(progress, x, y);
            if (t <= 0) return Look.HIDDEN;
            float shown = GuideReveal.smooth(t / 0.12F);
            // White-hot, then ember, then char, then its own colour.
            int colour = t < 0.1F ? mix(HOT, EMBER, t / 0.1F) : mix(EMBER, CHAR, GuideReveal.smooth((t - 0.1F) / 0.3F));
            float strength = 1 - GuideReveal.smooth((t - 0.45F) / 0.55F);
            return new Look(shown, 0, 0, (Math.round(strength * 0.92F * 255) << 24) | colour, 0);
        }

        @Override
        public void around(GuidePainter painter, float progress, float seconds, float x, float y, float width, float height, float spreadX, float spreadY) {
            float t = own(progress, spreadX, spreadY);
            if (t <= 0 || t >= AFLAME) return;
            float heat = 1 - t / AFLAME;
            int bottom = Math.round(y + height - 1);
            // Flames off its top, a tongue every two pixels, each its own height and flicker.
            for (int column = 0; column < width; column += 2) {
                float cx = x + column;
                float flicker = 0.55F + 0.45F * (float) Math.sin(seconds * 13 + spreadX * 0.7F + column * 1.9F);
                int tall = Math.round(heat * flicker * (2 + height * 0.45F));
                if (tall <= 0) continue;
                int top = Math.round(y) - tall;
                int x0 = Math.round(cx), x1 = Math.min(Math.round(x + width), x0 + 2);
                painter.fill(x0, top, x1, Math.round(y) + 1, (Math.round(heat * 230) << 24) | 0xE0521C);
                int core = tall * 3 / 5;
                if (core > 0) painter.fill(x0, Math.round(y) - core, x1, Math.round(y) + 1, (Math.round(heat * 240) << 24) | 0xFFA23A);
                if (core > 2) painter.fill(x0, Math.round(y) - core / 2, x0 + 1, Math.round(y) + 1, (Math.round(heat * 255) << 24) | 0xFFE58A);
            }
            // An ember glow along its foot.
            painter.fill(Math.round(x), bottom, Math.round(x + width), bottom + 1, (Math.round(heat * 160) << 24) | 0xFF8A1E);
        }

        private static int mix(int a, int b, float t) {
            t = Math.max(0, Math.min(1, t));
            int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
            int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
            int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
            return (r << 16) | (g << 8) | bl;
        }

        @Override
        public void overlay(GuidePainter painter, float progress, float seconds, float pageX) {
            int width = GuideLayout.PAGE_WIDTH;
            int height = GuideLayout.PAGE_HEIGHT;
            // Sparks and smoke rise from the rows burning now.
            float length = 1 - SPREAD;
            float hottest = (progress - length * AFLAME * 0.5F) / SPREAD / 0.8F * height;
            float spread = length * AFLAME / SPREAD / 0.8F * height + 10;
            for (int spark = 0; spark < 24; spark++) {
                float cycle = seconds * 1.4F + GuideReveal.noise(spark * 977);
                float age = cycle % 1;
                int round = (int) cycle;
                float from = hottest + (GuideReveal.noise(spark * 131 + round * 17) - 0.5F) * spread;
                if (from < 6 || from > height - 4) continue;
                float x0 = 10 + GuideReveal.noise(spark * 31 + round * 7) * (width - 20);
                int x = Math.round(x0 + (float) Math.sin(seconds * 3 + spark) * 3 * age);
                int y = Math.round(from - age * 24);
                if (y < 2) continue;
                if (spark % 4 == 0) {
                    int alpha = Math.round((float) Math.sin(age * Math.PI) * 50);
                    painter.fill(x, y, x + 2 + Math.round(age * 2), y + 2, (alpha << 24) | 0x8C8780);
                } else {
                    int colour = age < 0.3F ? 0xFFE58A : age < 0.65F ? 0xFFA23A : 0xE0521C;
                    painter.fill(x, y, x + 1, y + 1, (Math.round((1 - age) * 255) << 24) | colour);
                }
            }
        }
    }

    // ---- Runes ----

    // Each letter first shows as a glowing rune that flickers and settles into its own; a soft band of light crosses
    // the spread as they do, and runes drift up and fade.
    private static final class Runes implements GuideReveal {
        private static final int GLOW = 0x7FD8FF;
        private static final String SYMBOLS = "abcdefghijklmnopqrstuvwxyz";
        private static final ResourceLocation FONT = new ResourceLocation("alt");

        @Override
        public Look look(float progress, float x, float y) {
            // A wave across the spread from the fold out, each place a little apart from its neighbours.
            float place = Math.abs(x - GuideLayout.PAGE_WIDTH) / GuideLayout.PAGE_WIDTH * 0.6F + y / GuideLayout.PAGE_HEIGHT * 0.25F
                    + GuideReveal.noise(Math.round(x) * 73 + Math.round(y) * 1013) * 0.15F;
            float local = GuideReveal.stagger(progress, place, 0.5F);
            if (local <= 0) return Look.HIDDEN;
            float settled = GuideReveal.smooth(local);
            return new Look(GuideReveal.smooth(local * 3), 0, 0, (Math.round((1 - settled) * 0.8F * 255) << 24) | GLOW, 1 - settled);
        }

        @Override
        public void overlay(GuidePainter painter, float progress, float seconds, float pageX) {
            int width = GuideLayout.PAGE_WIDTH;
            int height = GuideLayout.PAGE_HEIGHT;
            float fade = 1 - GuideReveal.smooth((progress - 0.55F) / 0.45F);
            if (fade <= 0) return;
            // A soft band of light, leaning, crossing from the fold outwards on both pages.
            float reach = progress * 1.3F * width;
            for (int y = 0; y < height; y += 4) {
                float lean = (y + 2 - height / 2.0F) * 0.18F;
                float centre = pageX < width ? width - reach + lean - pageX : width + reach + lean - pageX;
                int x = Math.round(centre);
                if (x < -6 || x >= width + 6) continue;
                // Three nested strips, brightest in the middle.
                for (int band = 3; band >= 1; band--) {
                    int x0 = Math.max(0, x - band * 2), x1 = Math.min(width, x + band * 2);
                    if (x1 > x0) painter.fill(x0, y, x1, Math.min(height, y + 4), (Math.round(22 * fade) << 24) | (band == 1 ? 0xF0FCFF : GLOW));
                }
            }
            // Runes drifting up, swelling into light and out again.
            for (int rune = 0; rune < 12; rune++) {
                float age = (seconds * 0.45F + GuideReveal.noise(rune * 389)) % 1;
                float x0 = GuideReveal.noise(rune * 53) * (width * 2 - 20) + 6 - pageX;
                if (x0 < 2 || x0 > width - 8) continue;
                int x = Math.round(x0 + (float) Math.sin(seconds + rune) * 2);
                int y = Math.round(height * 0.9F - age * height * 0.75F);
                float glow = (float) Math.sin(age * Math.PI) * fade;
                if (glow < 0.1F) continue;
                char symbol = SYMBOLS.charAt((int) (GuideReveal.noise(rune * 7 + (int) (seconds * 2)) * SYMBOLS.length()) % SYMBOLS.length());
                painter.text(net.minecraft.network.chat.Component.literal(String.valueOf(symbol)).withStyle(style -> style.withFont(FONT)),
                        x, y, (Math.round(glow * 210) << 24) | GLOW);
            }
        }
    }

    /** Adds an effect, or replaces the one with that id. Safe to call while mods load. */
    public static void register(ResourceLocation id, GuideReveal effect) {
        EFFECTS.put(id, effect);
    }

    /** The effect with that id; null when there is none. */
    public static @Nullable GuideReveal get(ResourceLocation id) {
        return EFFECTS.get(id);
    }
}
