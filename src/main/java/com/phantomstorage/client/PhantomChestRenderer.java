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
 * Renders the vanilla chest model with the Phantom Chest texture, translucent and gently bobbing.
 * The body never renders darker than light level 7 (a redstone torch); the glow layer is full-bright.
 * Real surrounding light comes only from LambDynamicLights, via assets/phantomstorage/dynamiclights/entity.
 */
public class PhantomChestRenderer extends EntityRenderer<PhantomChestEntity> {
    private static final ResourceLocation TEXTURE = PhantomStorage.id("textures/entity/phantom_chest.png");
    /**
     * Emissive mask (web strands, runes, eye). Drawn with the vanilla "eyes" render type: additive and
     * full-bright, and treated as emissive by shader packs (Iris/Oculus) the same as spider/enderman eyes.
     */
    private static final ResourceLocation GLOW_TEXTURE = PhantomStorage.id("textures/entity/phantom_chest_glow.png");
    private static final float GLOW_BASE = 0.7F;
    private static final float GLOW_PULSE = 0.2F;
    /** ARGB: ~80% opacity, no tint (the texture carries the colour). */
    private static final int GHOST_COLOR = 0xCCFFFFFF;
    private static final int MIN_BLOCK_LIGHT = 7;

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
        poseStack.translate(0.0, 0.06 + Mth.sin(age * 0.08F) * 0.06F, 0.0);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(-0.5, 0.0, -0.5);

        float open = 1.0F - entity.getLidOpenness(partialTick);
        open = 1.0F - open * open * open;
        this.lid.xRot = -(open * ((float) Math.PI / 2.0F));
        this.lock.xRot = this.lid.xRot;

        int light = LightTexture.pack(
                Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT), LightTexture.sky(packedLight));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentCull(TEXTURE));
        this.lid.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, GHOST_COLOR);
        this.lock.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, GHOST_COLOR);
        this.bottom.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, GHOST_COLOR);

        // Glowing details: a slow pulse, independent of world light.
        int glow = (int) (255 * Mth.clamp(GLOW_BASE + GLOW_PULSE * Mth.sin(age * 0.05F), 0.0F, 1.0F));
        int glowColor = 0xFF000000 | glow << 16 | glow << 8 | glow;
        VertexConsumer emissive = buffer.getBuffer(RenderType.eyes(GLOW_TEXTURE));
        this.lid.render(poseStack, emissive, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowColor);
        this.lock.render(poseStack, emissive, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowColor);
        this.bottom.render(poseStack, emissive, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowColor);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomChestEntity entity) {
        return TEXTURE;
    }
}
