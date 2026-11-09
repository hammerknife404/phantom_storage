package com.phantomstorage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * A surface of the End portal's starfield (the anchor's windows, the collector's orb, the gateway's
 * doorway), drawn the way the End portal shader draws it but with ordinary entity passes: the end sky
 * as a near-black backing in the mod's {@link PhantomRenderTypes#ghost ghost} pass, then the shader's
 * star layers added on top in the eyes' additive pass, each turned, scaled and drifting as in
 * {@link VoidLayers}. Every vertex takes its texture spot from where it lands on screen, as the shader
 * does per pixel, so the stars stay put on screen while the pet moves: a window onto the void.
 *
 * <p>Not {@code RenderType.endPortal()}: that shader is opaque, so the void couldn't fade with the
 * opacity setting, and shader packs have no program for it (Iris swaps the End portal block's own
 * render for a plain texture), so on an entity it drew nothing or leftovers. The ghost and eyes passes
 * take the pet's alpha, and every shader pack handles them.
 *
 * <p>The geometry never changes, so it is built once ({@link Builder}) and only drawn each frame.
 */
final class PhantomVoid {
    static final ResourceLocation END_SKY = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    static final ResourceLocation END_PORTAL = ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
    /** Floor for a vertex's clip w, so one at or behind the eye can't divide by zero. */
    private static final float MIN_W = 0.01F;
    private static final int STRIDE = 6;

    /** x, y, z, nx, ny, nz per vertex, four vertices per quad. */
    private final float[] vertices;
    /** Scratch: each vertex's screen position (0..1, y up) this frame. Render thread only. */
    private final float[] screen;
    private final float[] layer = new float[6];

    private PhantomVoid(float[] vertices) {
        this.vertices = vertices;
        this.screen = new float[vertices.length / STRIDE * 2];
    }

    static Builder builder() {
        return new Builder();
    }

    /**
     * Draws the surface at {@code pose} (inside an entity renderer, where the level's projection and
     * view are current) at opacity {@code alpha} (0..1). The backing blends by alpha; the stars add
     * their light, so fading them means dimming them.
     */
    void draw(PoseStack.Pose pose, MultiBufferSource buffer, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        Matrix4f toClip = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix())
                .mul(pose.pose());
        Vector4f clip = new Vector4f();
        int count = this.vertices.length / STRIDE;
        for (int i = 0; i < count; i++) {
            int at = i * STRIDE;
            toClip.transform(this.vertices[at], this.vertices[at + 1], this.vertices[at + 2], 1.0F, clip);
            float w = Math.max(clip.w, MIN_W);
            this.screen[i * 2] = clip.x / w * 0.5F + 0.5F;
            this.screen[i * 2 + 1] = clip.y / w * 0.5F + 0.5F;
        }
        int light = LightTexture.FULL_BRIGHT;

        // Backing: the end sky at screen scale, tinted by the shader's first colour.
        int backing = VoidLayers.color(0, 1.0F) & 0xFFFFFF | Math.round(alpha * 255.0F) << 24;
        VertexConsumer back = buffer.getBuffer(PhantomRenderTypes.ghost(END_SKY));
        for (int i = 0; i < count; i++) {
            this.emit(pose, back, i, backing, light, this.screen[i * 2], this.screen[i * 2 + 1]);
        }

        VertexConsumer stars = buffer.getBuffer(RenderType.eyes(END_PORTAL));
        float time = RenderSystem.getShaderGameTime();
        for (int n = 1; n <= VoidLayers.LAYERS; n++) {
            VoidLayers.layer(n, time, this.layer);
            int color = VoidLayers.color(n - 1, alpha);
            float[] t = this.layer;
            for (int i = 0; i < count; i++) {
                float x = this.screen[i * 2];
                float y = this.screen[i * 2 + 1];
                this.emit(pose, stars, i, color, light, t[0] * x + t[1] * y + t[2], t[3] * x + t[4] * y + t[5]);
            }
        }
    }

    private void emit(PoseStack.Pose pose, VertexConsumer c, int i, int color, int light, float u, float v) {
        int at = i * STRIDE;
        float[] d = this.vertices;
        GhostQuads.vertex(pose, c, color, light, d[at + 3], d[at + 4], d[at + 5], d[at], d[at + 1], d[at + 2], u, v);
    }

    static final class Builder {
        private final FloatArrayList data = new FloatArrayList();

        private Builder() {}

        /** One vertex; give them four to a quad, counter-clockwise seen from outside (both passes cull). */
        Builder vertex(float x, float y, float z, float nx, float ny, float nz) {
            this.data.add(x);
            this.data.add(y);
            this.data.add(z);
            this.data.add(nx);
            this.data.add(ny);
            this.data.add(nz);
            return this;
        }

        /**
         * The flat quad a, a + up, a + up + across, a + across (in that winding), facing {@code normal},
         * cut into {@code cutsUp} x {@code cutsAcross} cells. Each vertex's texture spot is exact and
         * the GPU blends between them, so smaller cells keep a big pane's stars truer to the screen.
         */
        Builder grid(float ax, float ay, float az, float upX, float upY, float upZ,
                     float acrossX, float acrossY, float acrossZ, int cutsUp, int cutsAcross,
                     float nx, float ny, float nz) {
            for (int i = 0; i < cutsUp; i++) {
                for (int j = 0; j < cutsAcross; j++) {
                    float u0 = (float) i / cutsUp;
                    float u1 = (float) (i + 1) / cutsUp;
                    float a0 = (float) j / cutsAcross;
                    float a1 = (float) (j + 1) / cutsAcross;
                    this.corner(ax, ay, az, upX, upY, upZ, acrossX, acrossY, acrossZ, u0, a0, nx, ny, nz);
                    this.corner(ax, ay, az, upX, upY, upZ, acrossX, acrossY, acrossZ, u1, a0, nx, ny, nz);
                    this.corner(ax, ay, az, upX, upY, upZ, acrossX, acrossY, acrossZ, u1, a1, nx, ny, nz);
                    this.corner(ax, ay, az, upX, upY, upZ, acrossX, acrossY, acrossZ, u0, a1, nx, ny, nz);
                }
            }
            return this;
        }

        private void corner(float ax, float ay, float az, float upX, float upY, float upZ,
                            float acrossX, float acrossY, float acrossZ, float up, float across,
                            float nx, float ny, float nz) {
            this.vertex(ax + upX * up + acrossX * across, ay + upY * up + acrossY * across,
                    az + upZ * up + acrossZ * across, nx, ny, nz);
        }

        PhantomVoid build() {
            return new PhantomVoid(this.data.toFloatArray());
        }
    }
}
