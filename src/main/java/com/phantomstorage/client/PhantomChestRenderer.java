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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders the vanilla chest model with the Phantom Chest texture, translucent and gently bobbing.
 * Opacity and the light-level-7 floor (a redstone torch) come from {@link PhantomClientConfig}.
 * Real surrounding light comes only from LambDynamicLights, via assets/phantomstorage/dynamiclights/entity.
 */
public class PhantomChestRenderer extends EntityRenderer<PhantomChestEntity> {
    private static final ResourceLocation TEXTURE = PhantomStorage.id("textures/entity/phantom_chest.png");
    private static final int MIN_BLOCK_LIGHT = 7;
    /** Lid width relative to the body: just enough to win the shared planes. */
    private static final float LID_SCALE = 1.004F;

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

        int blockLight = PhantomClientConfig.chestGlows()
                ? Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT) : LightTexture.block(packedLight);
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        int ghost = PhantomClientConfig.white(PhantomClientConfig.chestAlpha(), 1.0F);
        // Depth first, then colour: only the outermost surface shows, never the faces behind it.
        this.renderModel(poseStack, buffer.getBuffer(PhantomRenderTypes.depthOnly(TEXTURE)), light, 0xFFFFFFFF);
        this.renderModel(poseStack, buffer.getBuffer(PhantomRenderTypes.ghost(TEXTURE)), light, ghost);
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

    @Override
    public ResourceLocation getTextureLocation(PhantomChestEntity entity) {
        return TEXTURE;
    }
}
