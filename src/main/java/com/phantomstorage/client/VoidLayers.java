package com.phantomstorage.client;

/**
 * The math of the End portal shader's starfield ({@code rendertype_end_portal.fsh}), game-free so it
 * can be unit-tested: its colours, and for each star layer the affine map from a point on the screen
 * to the spot of the star texture it shows. {@link PhantomVoid} draws with it.
 */
public final class VoidLayers {
    /** Star layers, as the End portal block draws them (the end gateway draws 16). */
    public static final int LAYERS = 15;

    /** The shader's COLORS: entry 0 tints the end-sky backing, entry i the star layer i + 1. */
    private static final float[][] COLORS = {
            {0.022087F, 0.098399F, 0.110818F},
            {0.011892F, 0.095924F, 0.089485F},
            {0.027636F, 0.101689F, 0.100326F},
            {0.046564F, 0.109883F, 0.114838F},
            {0.064901F, 0.117696F, 0.097189F},
            {0.063761F, 0.086895F, 0.123646F},
            {0.084817F, 0.111994F, 0.166380F},
            {0.097489F, 0.154120F, 0.091064F},
            {0.106152F, 0.131144F, 0.195191F},
            {0.097721F, 0.110188F, 0.187229F},
            {0.133516F, 0.138278F, 0.148582F},
            {0.070006F, 0.243332F, 0.235792F},
            {0.196766F, 0.142899F, 0.214696F},
            {0.047281F, 0.315338F, 0.321970F},
            {0.204675F, 0.390010F, 0.302066F},
            {0.080955F, 0.314821F, 0.661491F},
    };

    private VoidLayers() {}

    /** The shader colour {@code index} (0 = the backing) scaled by {@code factor}, as opaque ARGB. */
    public static int color(int index, float factor) {
        float[] c = COLORS[index];
        return 0xFF000000 | channel(c[0] * factor) << 16 | channel(c[1] * factor) << 8 | channel(c[2] * factor);
    }

    /**
     * Fills {@code out} with (a, b, c, d, e, f) such that screen point (x, y) (0..1 across the window,
     * y up, as the shader's {@code texProj0} after its divide) shows the star texture of layer
     * {@code layer} (1..{@link #LAYERS}) at u = a x + b y + c, v = d x + e y + f, at shader game time
     * {@code gameTime}. That is the shader's {@code texProj0 * end_portal_layer(layer)}: a row vector
     * times the matrices, so it turns and scales first, then drifts, then halves and shifts by a
     * quarter.
     */
    public static void layer(int layer, float gameTime, float[] out) {
        double angle = Math.toRadians((layer * layer * 4321.0 + layer * 9.0) * 2.0);
        float scale = (4.5F - layer / 4.0F) * 2.0F;
        float cos = (float) Math.cos(angle) * scale * 0.5F;
        float sin = (float) Math.sin(angle) * scale * 0.5F;
        float drift = (2.0F + layer / 1.5F) * (gameTime * 1.5F);
        out[0] = cos;
        out[1] = -sin;
        out[2] = 0.5F * (17.0F / layer) + 0.25F;
        out[3] = sin;
        out[4] = cos;
        out[5] = 0.5F * drift + 0.25F;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}
