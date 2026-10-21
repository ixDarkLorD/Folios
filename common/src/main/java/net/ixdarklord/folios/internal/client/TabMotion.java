package net.ixdarklord.folios.internal.client;

import net.minecraft.Util;

import java.util.HashMap;
import java.util.Map;

// How a book's ribbon tabs move: each is on a spring, so it swings out under the pointer, further while its part of
// the book is open; pressed, it is knocked in and springs out again. Stepped in small
// fixed steps, so it moves the same at any frame rate.
public final class TabMotion {
    // A quick spring, damped critically: it swings straight out to its place and settles there without rocking back
    // and forth (which, a pixel at a time, read as a flicker).
    private static final float STIFFNESS = 320;
    private static final float DAMPING = 2 * (float) Math.sqrt(STIFFNESS);
    private static final float STEP = 1 / 240.0F;
    /** How hard a press knocks a tab in, and how long its darkening lasts. */
    private static final float KNOCK = 11;
    private static final float PRESS_SECONDS = 0.3F;

    // Per tab: how far out, how fast it moves, how pressed, and the time it has yet to step.
    private final Map<String, float[]> tabs = new HashMap<>();
    private long lastFrame = Util.getMillis();
    private float frame;

    /** Starts a frame: how much time has passed is read once for every tab. */
    public void frame() {
        long now = Util.getMillis();
        frame = Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
    }

    /**
     * How far a tab is out, swinging towards where it should be: 0 at rest, 0.55 under the pointer, 1 while its part
     * of the book is open; below 0 for a moment when knocked in.
     */
    public float out(String tab, boolean hovered, boolean here) {
        float[] state = tabs.computeIfAbsent(tab, key -> new float[]{here ? 1 : 0, 0, 0, 0});
        float target = here ? 1 : hovered ? 0.55F : 0;
        state[3] += frame;
        while (state[3] >= STEP) {
            state[3] -= STEP;
            float pull = STIFFNESS * (target - state[0]) - DAMPING * state[1];
            state[1] += pull * STEP;
            state[0] += state[1] * STEP;
        }
        state[2] = Math.max(0, state[2] - frame / PRESS_SECONDS);
        return Math.max(-0.6F, Math.min(1.4F, state[0]));
    }

    /** How pressed in a tab is, 1 just pressed to 0, eased. */
    public float pressed(String tab) {
        float[] state = tabs.get(tab);
        if (state == null) return 0;
        float t = state[2];
        return t * t * (3 - 2 * t);
    }

    public void press(String tab) {
        float[] state = tabs.computeIfAbsent(tab, key -> new float[]{0, 0, 0, 0});
        state[1] -= KNOCK;
        state[2] = 1;
    }
}
