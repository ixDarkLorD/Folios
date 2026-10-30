package net.ixdarklord.folios.internal.client;

import java.util.HashMap;
import java.util.Map;

public final class TabMotion {
    private static final float STIFFNESS = 320;
    private static final float DAMPING = 2 * (float) Math.sqrt(STIFFNESS);
    private static final float STEP = 1 / 240.0F;
    private static final float KNOCK = 11;
    private static final float PRESS_SECONDS = 0.3F;

    private final Map<String, float[]> tabs = new HashMap<>();
    private long lastFrame = net.ixdarklord.folios.internal.client.FoliosClock.millis();
    private float frame;

    public void frame() {
        long now = net.ixdarklord.folios.internal.client.FoliosClock.millis();
        frame = Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
    }

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
