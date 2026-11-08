package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomAnchorEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * A small ghostly lodestone with the tank's phantom wings and glowing eyes. While it follows it bobs
 * and flaps like the other pets; anchored, it hangs still with its wings folded down. Drawn from
 * vanilla textures, so there is no model or texture to maintain. Opacity and the light-level-7 floor
 * come from {@link PhantomClientConfig}.
 */
public class PhantomAnchorRenderer extends EntityRenderer<PhantomAnchorEntity> {
    private static final ResourceLocation LODESTONE_TOP = ResourceLocation.withDefaultNamespace("block/lodestone_top");
    private static final ResourceLocation LODESTONE_SIDE = ResourceLocation.withDefaultNamespace("block/lodestone_side");
    private static final int MIN_BLOCK_LIGHT = 7;
    private static final float PX = GhostQuads.PX;
    /** A 10 px cube inside the 0.75 entity box. */
    private static final float HALF = 5.0F * PX;
    private static final float HEIGHT = 10.0F * PX;
    /** The wing roots sit just inside the top edges, so the wings grow out of the stone. */
    private static final float WING_TUCK = PX;
    /** Anchored: wings hang down the sides (the outer half at twice this). */
    private static final float FOLDED_DEGREES = -38.0F;
    private static final float BOB_BASE = 0.06F;

    public PhantomAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
        this.shadowStrength = 0.4F;
    }

    @Override
    public void render(PhantomAnchorEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        boolean anchored = entity.isInSittingPose();
        float age = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0, anchored ? BOB_BASE : BOB_BASE + Mth.sin(age * 0.08F) * 0.06F, 0.0);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        int ghost = PhantomClientConfig.white(PhantomClientConfig.anchorAlpha(), 1.0F);
        int blockLight = PhantomClientConfig.anchorGlows()
                ? Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT) : LightTexture.block(packedLight);
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        PoseStack.Pose pose = poseStack.last();

        // Back faces culled: a convex box then shows only its outer shell, never the far faces through it.
        cube(pose, buffer.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS)),
                GhostQuads.sprite(LODESTONE_TOP), GhostQuads.sprite(LODESTONE_SIDE), ghost, light);
        VertexConsumer wings = buffer.getBuffer(RenderType.entityTranslucent(GhostQuads.PHANTOM));
        float flap = anchored ? FOLDED_DEGREES : GhostQuads.flap(age);
        GhostQuads.wing(pose, wings, 1, flap, HALF - WING_TUCK, HEIGHT - 0.5F * PX, ghost, light);
        GhostQuads.wing(pose, wings, -1, flap, HALF - WING_TUCK, HEIGHT - 0.5F * PX, ghost, light);
        GhostQuads.eyes(pose, buffer, HALF + 0.002F, HEIGHT - 2.0F * PX);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** The lodestone: its top texture on the top and bottom, its side texture all round, each face whole. */
    private static void cube(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite end, TextureAtlasSprite side,
                             int color, int light) {
        float x0 = -HALF;
        float x1 = HALF;
        float z0 = -HALF;
        float z1 = HALF;
        float y0 = 0.0F;
        float y1 = HEIGHT;
        float eu0 = end.getU0();
        float eu1 = end.getU1();
        float ev0 = end.getV0();
        float ev1 = end.getV1();
        float su0 = side.getU0();
        float su1 = side.getU1();
        float sv0 = side.getV0();
        float sv1 = side.getV1();
        GhostQuads.quad(pose, c, color, light, 0, 1, 0, eu0, ev0, eu1, ev1, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        GhostQuads.quad(pose, c, color, light, 0, -1, 0, eu0, ev0, eu1, ev1, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        GhostQuads.quad(pose, c, color, light, 0, 0, -1, su0, sv0, su1, sv1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        GhostQuads.quad(pose, c, color, light, 0, 0, 1, su0, sv0, su1, sv1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        GhostQuads.quad(pose, c, color, light, -1, 0, 0, su0, sv0, su1, sv1, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        GhostQuads.quad(pose, c, color, light, 1, 0, 0, su0, sv0, su1, sv1, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomAnchorEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
