package net.ixdarklord.folios.api.client;

import net.minecraft.resources.ResourceLocation;

/**
 * How a page's content shows once the page is turned to: everything drawn on it (text, pictures, items, scenes,
 * whatever a page type draws) asks the effect how it looks at this point of the reveal, by where it is on the page.
 * Register one with {@link GuideReveals#register}; a book chooses it in book.json and an entry may choose its own:
 * <pre>{@code
 * "reveal": "mymod:shimmer"                                  // an effect, for its default length
 * "reveal": {"effect": "folios:rise", "seconds": 0.6}    // and how long it takes
 * "reveal": {"effect": "folios:ink", "once": true}       // only the first time each spread is seen this game
 * "reveal": {"effect": "folios:ink", "unread": true}     // only on spreads the reader hasn't been shown before
 * "reveal": "none"                                           // shown at once
 * }</pre>
 * An effect is asked once for each thing drawn each frame while it plays, so it should be cheap: arithmetic, no
 * allocation beyond the {@link Look} it returns (or one of the constants).
 * <pre>{@code
 * // Each line slides in from the left, the higher ones first.
 * GuideReveals.register(MyMod.id("slide"), (progress, x, y) -> {
 *     float local = GuideReveal.stagger(progress, y / GuideLayout.PAGE_HEIGHT, 0.5F);
 *     return new GuideReveal.Look(local, -(1 - GuideReveal.easeOut(local)) * 12, 0);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface GuideReveal {
    /**
     * How something drawn at (x, y) looks at this point of the reveal.
     *
     * @param progress how far the reveal has gone, 0 as the page lands to 1 when it is done (and 1 from then on)
     * @param x        where it is drawn across the open spread, in page pixels: 0 at the left page's outer edge,
     *                 {@code GuideLayout.PAGE_WIDTH} at the fold, twice that at the right page's outer edge; so an
     *                 effect runs on over the fold
     * @param y        where it is drawn down the page, in page pixels (0 to {@code GuideLayout.PAGE_HEIGHT})
     */
    Look look(float progress, float x, float y);

    /**
     * Draws over a page while the reveal plays (progress under 1), after its content: fire along a burning edge,
     * smoke, sparks, floating symbols. In the page's own pixels, through its painter; {@code pageX} is where the page
     * starts across the spread (0 or {@code GuideLayout.PAGE_WIDTH}), for drawing what runs on over the fold. Nothing
     * by default. Each thing drawn here is drawn every frame, so a few hundred fills at most.
     *
     * @param seconds a clock for what flickers or drifts (the book's)
     */
    default void overlay(net.ixdarklord.folios.api.client.page.GuidePainter painter, float progress, float seconds, float pageX) {
    }

    /**
     * Draws about one thing on the page just after it is drawn while the reveal plays: flames on a letter catching
     * fire, a glow round an item. {@code x}, {@code y}, {@code width} and {@code height} are where it is in the
     * painter's own pixels, for drawing; {@code spreadX} and {@code spreadY} where its middle is across the spread, as
     * {@link #look} is given it. Called for letters, items and pictures (not for plain fills), so keep it to a few
     * fills, and only where it shows. Nothing by default.
     */
    default void around(net.ixdarklord.folios.api.client.page.GuidePainter painter, float progress, float seconds,
                        float x, float y, float width, float height, float spreadX, float spreadY) {
    }

    /**
     * How a thing looks: how much it shows (0 not at all, 1 fully; items and scenes, which can't fade, show once past
     * a half), how far it is moved from its place in page pixels, a colour it is tinted towards ({@code tint}'s alpha
     * is how far: 0 not at all), and what share of its letters, if it is text, show as runes (0 none, 1 all).
     */
    record Look(float opacity, float dx, float dy, int tint, float runes) {
        public static final Look SHOWN = new Look(1, 0, 0);
        public static final Look HIDDEN = new Look(0, 0, 0);

        public Look(float opacity, float dx, float dy) {
            this(opacity, dx, dy, 0, 0);
        }
    }

    /**
     * A reveal: an effect, how long it takes in seconds, whether it plays only the first time each spread is seen this
     * game ({@code once}; pages of entries read in an earlier game count as seen), and whether it plays only on spreads
     * the reader has never been shown in full ({@code unread}; kept with the player, or with the copy of a book that
     * keeps its own, so it doesn't play again after a restart or on another computer).
     */
    record Use(ResourceLocation effect, float seconds, boolean once, boolean unread) {
        public static final Use NONE = new Use(GuideReveals.NONE, 0, false, false);

        public Use(ResourceLocation effect, float seconds) {
            this(effect, seconds, false, false);
        }

        public Use(ResourceLocation effect, float seconds, boolean once) {
            this(effect, seconds, once, false);
        }
    }

    // ---- Helpers for effects ----

    /**
     * A part's own progress when parts start one after another: {@code place} (0 to 1) is where it is in the order,
     * and {@code spread} how much of the reveal the starts are spread over (0 all together, nearly 1 one at a time).
     */
    static float stagger(float progress, float place, float spread) {
        float start = Math.max(0, Math.min(1, place)) * spread;
        float length = 1 - spread;
        return length <= 0 ? (progress >= start ? 1 : 0) : Math.max(0, Math.min(1, (progress - start) / length));
    }

    /** Eases 0 to 1 quickly at first and gently into its end. */
    static float easeOut(float t) {
        float u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    /** A steady pseudo-random number from 0 to 1 for a seed: for where sparks start, which letters flicker. */
    static float noise(int seed) {
        int hash = seed * 0x27D4EB2D;
        hash ^= hash >>> 15;
        hash *= 0x2C1B3C6D;
        hash ^= hash >>> 12;
        return (hash & 0xFFFF) / 65535.0F;
    }

    /** Eases 0 to 1 gently at both ends. */
    static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
