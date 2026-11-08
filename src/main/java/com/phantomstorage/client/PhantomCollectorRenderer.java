package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomCollectorEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A small orb of the void: a sphere of the End's sky, the sky drifting slowly across it, with the
 * shared phantom eyes on its front and no wings. Full-bright like the anchor's panes, so it reads the
 * same by day and glows faintly at night; items it takes fly into it. Bobs while it follows, hangs
 * still once anchored. Opacity (eyes included) comes from {@link PhantomClientConfig}.
 */
public class PhantomCollectorRenderer extends EntityRenderer<PhantomCollectorEntity> {
    private static final ResourceLocation END_SKY = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
    private static final float PX = GhostQuads.PX;
    /** A 9 px orb inside the 0.625 entity box. */
    private static final float RADIUS = 4.5F * PX;
    private static final float CENTER_Y = 5.0F * PX;
    private static final int RINGS = 8;
    private static final int SEGMENTS = 14;
    /** One sky texel per model pixel round the equator, like a block. */
    private static final float SKY_U = 2.0F * Mth.PI * RADIUS / PX / 128.0F;
    private static final float SKY_V = Mth.PI * RADIUS / PX / 128.0F;
    private static final float SKY_DRIFT = 0.04F / 128.0F;
    /** Brighter than the anchor's panes: the whole orb is void, with nothing round it. */
    private static final int VOID_TINT = 0x9888C0;
    /** Eyes a little under the phantom's own 7x3 px, on the front of the orb. */
    private static final float EYES_HALF_W = 3.0F * PX;
    private static final float EYES_H = 2.5F * PX;
    private static final float BOB_BASE = 0.08F;

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
        int color = (PhantomClientConfig.white(alpha, 1.0F) & 0xFF000000) | VOID_TINT;
        PoseStack.Pose pose = poseStack.last();
        sphere(pose, buffer.getBuffer(PhantomRenderTypes.ghost(END_SKY)), color, age * SKY_DRIFT);
        GhostQuads.eyes(pose, buffer, RADIUS + 0.004F, CENTER_Y + 2.0F * PX, alpha, EYES_HALF_W, EYES_H);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** A UV sphere, quads wound counter-clockwise from outside so culling keeps the outer shell. */
    private static void sphere(PoseStack.Pose pose, VertexConsumer c, int color, float drift) {
        int light = LightTexture.FULL_BRIGHT;
        for (int ring = 0; ring < RINGS; ring++) {
            float lat0 = Mth.PI * ring / RINGS - Mth.HALF_PI;       // from the bottom pole up
            float lat1 = Mth.PI * (ring + 1) / RINGS - Mth.HALF_PI;
            float v0 = SKY_V * (1.0F - (float) ring / RINGS) + drift * 0.5F;
            float v1 = SKY_V * (1.0F - (float) (ring + 1) / RINGS) + drift * 0.5F;
            for (int seg = 0; seg < SEGMENTS; seg++) {
                float lon0 = Mth.TWO_PI * seg / SEGMENTS;
                float lon1 = Mth.TWO_PI * (seg + 1) / SEGMENTS;
                float u0 = SKY_U * seg / SEGMENTS + drift;
                float u1 = SKY_U * (seg + 1) / SEGMENTS + drift;
                // Corners: bottom-left, top-left, top-right, bottom-right seen from outside.
                vertex(pose, c, color, light, lat0, lon0, u0, v0);
                vertex(pose, c, color, light, lat1, lon0, u0, v1);
                vertex(pose, c, color, light, lat1, lon1, u1, v1);
                vertex(pose, c, color, light, lat0, lon1, u1, v0);
            }
        }
    }

    /** A point on the sphere: longitude runs from +z (the front) towards -x, so faces wind outward. */
    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int color, int light, float lat, float lon, float u, float v) {
        float ring = Mth.cos(lat);
        float nx = -Mth.sin(lon) * ring;
        float ny = Mth.sin(lat);
        float nz = Mth.cos(lon) * ring;
        GhostQuads.vertex(pose, c, color, light, nx, ny, nz, nx * RADIUS, CENTER_Y + ny * RADIUS, nz * RADIUS, u, v);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomCollectorEntity entity) {
        return END_SKY;
    }
}
