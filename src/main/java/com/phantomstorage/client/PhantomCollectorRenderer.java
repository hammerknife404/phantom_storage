package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomCollectorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A small orb of the void: a sphere of the End portal's starfield ({@link PhantomVoid}), like the
 * anchor's panes, with the shared phantom eyes on its front and no wings; items it takes fly into it.
 * Bobs while it follows, hangs still once anchored. Orb and eyes fade with the opacity setting.
 */
public class PhantomCollectorRenderer extends EntityRenderer<PhantomCollectorEntity> {
    private static final float PX = GhostQuads.PX;
    /** A 9 px orb inside the 0.625 entity box. */
    private static final float RADIUS = 4.5F * PX;
    private static final float CENTER_Y = 5.0F * PX;
    private static final int RINGS = 8;
    private static final int SEGMENTS = 14;
    /** Eyes a little under the phantom's own 7x3 px, on the front of the orb. */
    private static final float EYES_HALF_W = 3.0F * PX;
    private static final float EYES_H = 2.5F * PX;
    private static final float BOB_BASE = 0.08F;
    private static final PhantomVoid ORB = sphere();

    public PhantomCollectorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.2F;
        this.shadowStrength = 0.35F;
    }

    @Override
    public void render(PhantomCollectorEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        boolean anchored = entity.isInSittingPose();
        float age = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0, anchored ? BOB_BASE : BOB_BASE + Mth.sin(age * 0.08F) * 0.06F, 0.0);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        float alpha = PhantomClientConfig.collectorAlpha();
        PoseStack.Pose pose = poseStack.last();
        ORB.draw(pose, buffer, alpha);
        GhostQuads.eyes(pose, buffer, RADIUS + 0.004F, CENTER_Y + 2.0F * PX, alpha, EYES_HALF_W, EYES_H);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** The orb's quads, wound counter-clockwise from outside, normals pointing out. */
    private static PhantomVoid sphere() {
        PhantomVoid.Builder b = PhantomVoid.builder();
        for (int ring = 0; ring < RINGS; ring++) {
            float lat0 = Mth.PI * ring / RINGS - Mth.HALF_PI;       // from the bottom pole up
            float lat1 = Mth.PI * (ring + 1) / RINGS - Mth.HALF_PI;
            for (int seg = 0; seg < SEGMENTS; seg++) {
                float lon0 = Mth.TWO_PI * seg / SEGMENTS;
                float lon1 = Mth.TWO_PI * (seg + 1) / SEGMENTS;
                // Corners: bottom-left, top-left, top-right, bottom-right seen from outside.
                vertex(b, lat0, lon0);
                vertex(b, lat1, lon0);
                vertex(b, lat1, lon1);
                vertex(b, lat0, lon1);
            }
        }
        return b.build();
    }

    /** A point on the sphere: longitude runs from +z (the front) towards -x, so faces wind outward. */
    private static void vertex(PhantomVoid.Builder b, float lat, float lon) {
        float ring = Mth.cos(lat);
        float nx = -Mth.sin(lon) * ring;
        float ny = Mth.sin(lat);
        float nz = Mth.cos(lon) * ring;
        b.vertex(nx * RADIUS, CENTER_Y + ny * RADIUS, nz * RADIUS, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomCollectorEntity entity) {
        return PhantomVoid.END_SKY;
    }
}
