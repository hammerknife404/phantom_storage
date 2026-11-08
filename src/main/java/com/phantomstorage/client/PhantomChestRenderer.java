package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomChestEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders the vanilla chest model with the Phantom Chest texture, translucent and gently bobbing,
 * wearing a saddle while its owner rides it.
 * Opacity and the light-level-7 floor (a redstone torch) come from {@link PhantomClientConfig}.
 * Real surrounding light comes only from LambDynamicLights, via assets/phantomstorage/dynamiclights/entity.
 */
public class PhantomChestRenderer extends EntityRenderer<PhantomChestEntity> {
    private static final ResourceLocation TEXTURE = PhantomStorage.id("textures/entity/phantom_chest.png");
    private static final int MIN_BLOCK_LIGHT = 7;
    /** Lid width relative to the body: just enough to win the shared planes. */
    private static final float LID_SCALE = 1.004F;
    /** Worn while ridden: the pig's saddle, its seat on the lid and its straps down the sides. */
    private static final ResourceLocation SADDLE = ResourceLocation.withDefaultNamespace("textures/entity/pig/pig_saddle.png");
    private static final float PX = GhostQuads.PX;
    /** pig_saddle.png (64x32): the seat's oval and one strap ending in its buckle. */
    private static final float SEAT_U0 = 53 / 64.0F, SEAT_U1 = 64 / 64.0F, SEAT_V0 = 19 / 32.0F, SEAT_V1 = 30 / 32.0F;
    private static final float STRAP_U0 = 29 / 64.0F, STRAP_U1 = 36 / 64.0F, STRAP_V0 = 23 / 32.0F, STRAP_V1 = 26 / 32.0F;
    private static final float LID_TOP = 14.0F * PX;
    private static final float SEAT_HALF = 5.5F * PX;
    /** Just outside the lid's sides, which are 7 px out (a hair more, scaled). */
    private static final float SIDE = 7.15F * PX;
    private static final float STRAP_HALF = 1.5F * PX;
    private static final float STRAP_BOTTOM = 6.0F * PX;

    private final ModelPart bottom;
    private final ModelPart lid;
    private final ModelPart lock;

    public PhantomChestRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
        this.shadowRadius = 0.3F;
        this.shadowStrength = 0.4F;
    }

    @Override
    public void render(PhantomChestEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float age = entity.tickCount + partialTick;
        // Ridden, it sits still under the rider, its lid where they sit.
        poseStack.translate(0.0, entity.isVehicle() ? 0.0 : 0.06 + Mth.sin(age * 0.08F) * 0.06F, 0.0);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(-0.5, 0.0, -0.5);

        float open = 1.0F - entity.getLidOpenness(partialTick);
        open = 1.0F - open * open * open;
        this.lid.xRot = -(open * ((float) Math.PI / 2.0F));
        this.lock.xRot = this.lid.xRot;

        int blockLight = PhantomClientConfig.chestGlows()
                ? Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT) : LightTexture.block(packedLight);
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        int ghost = PhantomClientConfig.white(PhantomClientConfig.chestAlpha(), 1.0F);
        // Depth first, then colour: only the outermost surface shows, never the faces behind it.
        this.renderModel(poseStack, buffer.getBuffer(PhantomRenderTypes.depthOnly(TEXTURE)), light, 0xFFFFFFFF);
        this.renderModel(poseStack, buffer.getBuffer(PhantomRenderTypes.ghost(TEXTURE)), light, ghost);
        if (entity.isVehicle()) {
            poseStack.translate(0.5, 0.0, 0.5);
            saddle(poseStack.last(), buffer.getBuffer(RenderType.entityCutoutNoCull(SADDLE)), light);
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * The vanilla chest model's lid overlaps the body by a pixel, so their front, back and side faces
     * share planes there and z-fight into a striped band (vanilla hides it with matching texture rows).
     * The lid is drawn a hair wider so it always wins.
     */
    private void renderModel(PoseStack poseStack, VertexConsumer consumer, int light, int color) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.scale(LID_SCALE, 1.0F, LID_SCALE);
        poseStack.translate(-0.5, 0.0, -0.5);
        this.lid.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);
        this.lock.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);
        poseStack.popPose();
        this.bottom.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);
    }

    /**
     * The saddle, solid on the ghostly chest: the seat's oval just above the lid, and a strap on each
     * side running over the lid's edge and down, buckle at the bottom. Centred on the chest; the rider
     * faces +z, the lock.
     */
    private static void saddle(PoseStack.Pose pose, VertexConsumer c, int light) {
        int white = 0xFFFFFFFF;
        float seatY = LID_TOP + 0.3F * PX;
        GhostQuads.vertex(pose, c, white, light, 0, 1, 0, -SEAT_HALF, seatY, SEAT_HALF, SEAT_U0, SEAT_V1);
        GhostQuads.vertex(pose, c, white, light, 0, 1, 0, SEAT_HALF, seatY, SEAT_HALF, SEAT_U1, SEAT_V1);
        GhostQuads.vertex(pose, c, white, light, 0, 1, 0, SEAT_HALF, seatY, -SEAT_HALF, SEAT_U1, SEAT_V0);
        GhostQuads.vertex(pose, c, white, light, 0, 1, 0, -SEAT_HALF, seatY, -SEAT_HALF, SEAT_U0, SEAT_V0);
        float strapY = LID_TOP + 0.15F * PX;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * SIDE;
            float under = side * SEAT_HALF;
            // Over the lid's edge, from under the seat...
            GhostQuads.vertex(pose, c, white, light, 0, 1, 0, under, strapY, -STRAP_HALF, STRAP_U0, STRAP_V0);
            GhostQuads.vertex(pose, c, white, light, 0, 1, 0, under, strapY, STRAP_HALF, STRAP_U0, STRAP_V1);
            GhostQuads.vertex(pose, c, white, light, 0, 1, 0, x, strapY, STRAP_HALF, STRAP_U0 + 2 / 64.0F, STRAP_V1);
            GhostQuads.vertex(pose, c, white, light, 0, 1, 0, x, strapY, -STRAP_HALF, STRAP_U0 + 2 / 64.0F, STRAP_V0);
            // ...then down the side to the buckle.
            GhostQuads.vertex(pose, c, white, light, side, 0, 0, x, strapY, -STRAP_HALF, STRAP_U0 + 2 / 64.0F, STRAP_V0);
            GhostQuads.vertex(pose, c, white, light, side, 0, 0, x, strapY, STRAP_HALF, STRAP_U0 + 2 / 64.0F, STRAP_V1);
            GhostQuads.vertex(pose, c, white, light, side, 0, 0, x, STRAP_BOTTOM, STRAP_HALF, STRAP_U1, STRAP_V1);
            GhostQuads.vertex(pose, c, white, light, side, 0, 0, x, STRAP_BOTTOM, -STRAP_HALF, STRAP_U1, STRAP_V0);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomChestEntity entity) {
        return TEXTURE;
    }
}
