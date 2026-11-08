package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Hand-built geometry shared by the pets drawn from vanilla textures (tank, anchor): raw quads, and
 * the phantom wings and glowing eyes they wear. Units are blocks; the caller's pose is the pet's
 * base centre, facing +z.
 */
final class GhostQuads {
    static final float PX = 1.0F / 16.0F;
    static final ResourceLocation PHANTOM = ResourceLocation.withDefaultNamespace("textures/entity/phantom.png");
    private static final ResourceLocation PHANTOM_EYES = ResourceLocation.withDefaultNamespace("textures/entity/phantom_eyes.png");
    /** Phantom model UVs (64x64): the wing tip's top face (13x9 at (25,24)) and the head front (7x3 at (5,5)). */
    private static final float[] WING = px(25, 24, 38, 33);
    private static final float[] EYES = px(5, 5, 12, 8);
    /** Wings at half the phantom's size: 3 px inner half, 6.5 px outer half, 4.5 px deep at the root. */
    private static final float WING_BASE = 3.0F * PX;
    private static final float WING_TIP = 6.5F * PX;
    private static final float WING_DEPTH = 4.5F * PX;
    /** Fraction of the root depth the outer edge loses at the front: the tip tapers and sweeps back. */
    private static final float WING_TAPER = 0.4F;
    private static final float FLAP_SPEED = 0.18F;
    private static final float FLAP_DEGREES = 18.0F;
    /** Eyes at 1.5x the phantom's 7x3 px. */
    private static final float EYES_HALF_W = 5.25F * PX;
    private static final float EYES_H = 4.5F * PX;

    private GhostQuads() {}

    /** Wing angle in degrees at {@code age} ticks, for {@link #wing}. */
    static float flap(float age) {
        return Mth.sin(age * FLAP_SPEED) * FLAP_DEGREES;
    }

    /**
     * One phantom wing on side {@code side} (+1 = +x, -1 = -x), rooted at ({@code side * rootX}, {@code rootY}):
     * a single thin membrane, no box edges. The inner half swings {@code flap} degrees and the outer
     * half twice that, like the phantom's, but both halves share the joint vertices and one continuous
     * stretch of the wing texture, so the wing bends there instead of looking bolted together. Drawn
     * once, into a consumer for {@link #PHANTOM} whose render type doesn't cull, so it shows from above
     * and below.
     */
    static void wing(PoseStack.Pose pose, VertexConsumer c, int side, float flap, float rootX, float rootY, int color, int light) {
        float a = flap * Mth.DEG_TO_RAD;
        float jointX = rootX + Mth.cos(a) * WING_BASE;
        float jointY = rootY + Mth.sin(a) * WING_BASE;
        float tipX = jointX + Mth.cos(2.0F * a) * WING_TIP;
        float tipY = jointY + Mth.sin(2.0F * a) * WING_TIP;
        float z0 = -WING_DEPTH / 2.0F;
        float z1 = WING_DEPTH / 2.0F;
        float tipZ0 = z0 + WING_DEPTH * WING_TAPER;

        float u0 = WING[0];
        float uJoint = Mth.lerp(WING_BASE / (WING_BASE + WING_TIP), WING[0], WING[2]);
        float u1 = WING[2];
        float v0 = WING[1];
        float v1 = WING[3];
        float vTip0 = Mth.lerp(WING_TAPER, v0, v1);
        // Inner half: root -> joint.
        vertex(pose, c, color, light, 0, 1, 0, side * rootX, rootY, z0, u0, v0);
        vertex(pose, c, color, light, 0, 1, 0, side * rootX, rootY, z1, u0, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z1, uJoint, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z0, uJoint, v0);
        // Outer half: joint -> tapered tip.
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z0, uJoint, v0);
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z1, uJoint, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * tipX, tipY, z1, u1, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * tipX, tipY, tipZ0, u1, vTip0);
    }

    /**
     * Full-bright phantom eyes on a front face at {@code z}, centred, their top edge at {@code top}, faded
     * with the pet's {@code alpha}. The eyes render type adds its colour rather than blending by alpha,
     * so fading means dimming the colour.
     */
    static void eyes(PoseStack.Pose pose, MultiBufferSource buffer, float z, float top, float alpha) {
        eyes(pose, buffer, z, top, alpha, EYES_HALF_W, EYES_H);
    }

    /** {@link #eyes(PoseStack.Pose, MultiBufferSource, float, float, float)} at a given size (half width, height). */
    static void eyes(PoseStack.Pose pose, MultiBufferSource buffer, float z, float top, float alpha, float halfW, float h) {
        VertexConsumer eyes = buffer.getBuffer(RenderType.eyes(PHANTOM_EYES));
        int level = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int color = 0xFF000000 | level << 16 | level << 8 | level;
        // The eyes render type culls back faces, so wind counter-clockwise as seen from the front.
        int bright = LightTexture.FULL_BRIGHT;
        vertex(pose, eyes, color, bright, 0, 0, 1, -halfW, top - h, z, EYES[0], EYES[3]);
        vertex(pose, eyes, color, bright, 0, 0, 1, halfW, top - h, z, EYES[2], EYES[3]);
        vertex(pose, eyes, color, bright, 0, 0, 1, halfW, top, z, EYES[2], EYES[1]);
        vertex(pose, eyes, color, bright, 0, 0, 1, -halfW, top, z, EYES[0], EYES[1]);
    }

    /** Pixel rectangle on a 64x64 texture as u0, v0, u1, v1 fractions. */
    static float[] px(int u0, int v0, int u1, int v1) {
        return new float[] {u0 / 64.0F, v0 / 64.0F, u1 / 64.0F, v1 / 64.0F};
    }

    static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    /**
     * Corners in order bottom-left, top-left, top-right, bottom-right of the face's texture; that order
     * winds counter-clockwise seen from the side the normal points to, so culling render types keep it.
     */
    static void quad(PoseStack.Pose pose, VertexConsumer c, int color, int light, float nx, float ny, float nz,
                     float u0, float vTop, float u1, float vBottom,
                     float ax, float ay, float az, float bx, float by, float bz,
                     float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, c, color, light, nx, ny, nz, ax, ay, az, u0, vBottom);
        vertex(pose, c, color, light, nx, ny, nz, bx, by, bz, u0, vTop);
        vertex(pose, c, color, light, nx, ny, nz, cx, cy, cz, u1, vTop);
        vertex(pose, c, color, light, nx, ny, nz, dx, dy, dz, u1, vBottom);
    }

    static void vertex(PoseStack.Pose pose, VertexConsumer c, int color, int light, float nx, float ny, float nz,
                       float x, float y, float z, float u, float v) {
        c.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
