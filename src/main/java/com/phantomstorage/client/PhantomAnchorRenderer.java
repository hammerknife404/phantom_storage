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
 * A small ghostly lodestone whose sides are windows onto the void: a lodestone frame round a pane of
 * the End portal's starfield ({@link PhantomVoid}), which drifts like the portal block's and shows by
 * day and night alike. It wears the tank's phantom wings and glowing eyes. While it follows it bobs
 * and flaps like the other pets; anchored, it hangs still with its wings folded down. Drawn from
 * vanilla textures, so there is no model or texture to maintain. Opacity (void and eyes included) and
 * the light-level-7 floor come from {@link PhantomClientConfig}.
 *
 * <p>Drawn with the mod's own {@link PhantomRenderTypes#ghost} like the chest, never with vanilla's
 * {@code entityTranslucentCull(blocks)}: that one is the translucent block-item sheet, which the game
 * draws from a fixed buffer in a pass of its own and render mods may batch apart.
 */
public class PhantomAnchorRenderer extends EntityRenderer<PhantomAnchorEntity> {
    private static final ResourceLocation LODESTONE_TOP = ResourceLocation.withDefaultNamespace("block/lodestone_top");
    private static final ResourceLocation LODESTONE_SIDE = ResourceLocation.withDefaultNamespace("block/lodestone_side");
    private static final int MIN_BLOCK_LIGHT = 7;
    private static final float PX = GhostQuads.PX;
    /** A 10 px cube inside the 0.75 entity box. */
    private static final float HALF = 5.0F * PX;
    private static final float HEIGHT = 10.0F * PX;
    /** The lodestone frame round each side's pane, in px. */
    private static final float FRAME = 2.0F;
    /** The wing roots sit just inside the top edges, so the wings grow out of the stone. */
    private static final float WING_TUCK = PX;
    /** Anchored: wings hang down the sides (the outer half at twice this). */
    private static final float FOLDED_DEGREES = -38.0F;
    private static final float BOB_BASE = 0.06F;
    private static final PhantomVoid PANES = voidPanes();

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

        float alpha = PhantomClientConfig.anchorAlpha();
        int ghost = PhantomClientConfig.white(alpha, 1.0F);
        int blockLight = PhantomClientConfig.anchorGlows()
                ? Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT) : LightTexture.block(packedLight);
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        PoseStack.Pose pose = poseStack.last();

        // The void first, as the gateway and collector draw it: shader packs batch entity passes in the order
        // renderers ask for them, and one order everywhere keeps the void's backing under its stars.
        PANES.draw(pose, buffer, alpha);
        VertexConsumer stone = buffer.getBuffer(PhantomRenderTypes.ghost(InventoryMenu.BLOCK_ATLAS));
        stoneEnds(pose, stone, GhostQuads.sprite(LODESTONE_TOP), ghost, light);
        stoneFrames(pose, stone, GhostQuads.sprite(LODESTONE_SIDE), ghost, light);

        VertexConsumer wings = buffer.getBuffer(RenderType.entityTranslucent(GhostQuads.PHANTOM));
        float flap = anchored ? FOLDED_DEGREES : GhostQuads.flap(age);
        GhostQuads.wing(pose, wings, 1, flap, HALF - WING_TUCK, HEIGHT - 0.5F * PX, ghost, light);
        GhostQuads.wing(pose, wings, -1, flap, HALF - WING_TUCK, HEIGHT - 0.5F * PX, ghost, light);
        GhostQuads.eyes(pose, buffer, HALF + 0.002F, HEIGHT - 2.0F * PX, alpha);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** Top and bottom faces, the lodestone's top texture. */
    private static void stoneEnds(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite top, int color, int light) {
        float u0 = top.getU0();
        float u1 = top.getU1();
        float v0 = top.getV0();
        float v1 = top.getV1();
        GhostQuads.quad(pose, c, color, light, 0, 1, 0, u0, v0, u1, v1, -HALF, HEIGHT, -HALF, -HALF, HEIGHT, HALF, HALF, HEIGHT, HALF, HALF, HEIGHT, -HALF);
        GhostQuads.quad(pose, c, color, light, 0, -1, 0, u0, v0, u1, v1, -HALF, 0, HALF, -HALF, 0, -HALF, HALF, 0, -HALF, HALF, 0, HALF);
    }

    /**
     * Each side as a lodestone frame {@link #FRAME} px wide round an open centre: four strips, each
     * showing the matching part of the side texture.
     */
    private static void stoneFrames(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite side, int color, int light) {
        float f = FRAME / (2.0F * HALF / PX); // the frame as a fraction of the face
        // Strips as (a0, b0, a1, b1) in face fractions: a along the face, b up from the base.
        float[][] strips = {{0, 0, 1, f}, {0, 1 - f, 1, 1}, {0, f, f, 1 - f}, {1 - f, f, 1, 1 - f}};
        for (int face = 0; face < 4; face++) {
            for (float[] s : strips) {
                sideRect(pose, c, face, s[0], s[1], s[2], s[3],
                        side.getU(s[0]), side.getV(1 - s[3]), side.getU(s[2]), side.getV(1 - s[1]), color, light);
            }
        }
    }

    /**
     * The open centre of each side: a window onto the End portal's starfield, cut 2 x 2 so its stars
     * hold to the screen. Corners wound like {@link #sideRect}: a, a + up, a + up + across, a + across.
     */
    private static PhantomVoid voidPanes() {
        float f = FRAME / (2.0F * HALF / PX);
        float y0 = f * HEIGHT;
        float h = (1.0F - 2.0F * f) * HEIGHT;
        float in = HALF - 2.0F * HALF * f; // the pane's half-width
        return PhantomVoid.builder()
                .grid(-in, y0, -HALF, 0, h, 0, 2 * in, 0, 0, 2, 2, 0, 0, -1)
                .grid(in, y0, HALF, 0, h, 0, -2 * in, 0, 0, 2, 2, 0, 0, 1)
                .grid(-HALF, y0, in, 0, h, 0, 0, 0, -2 * in, 2, 2, -1, 0, 0)
                .grid(HALF, y0, -in, 0, h, 0, 0, 0, 2 * in, 2, 2, 1, 0, 0)
                .build();
    }

    /**
     * A rectangle on side {@code face} (0 north, 1 south, 2 west, 3 east), in face fractions: {@code a}
     * along the face (u runs the same way on every side), {@code b} up from the base. Corners go round
     * counter-clockwise seen from outside, so culling keeps the outer face.
     */
    private static void sideRect(PoseStack.Pose pose, VertexConsumer c, int face, float a0, float b0, float a1, float b1,
                                 float u0, float vTop, float u1, float vBottom, int color, int light) {
        float y0 = b0 * HEIGHT;
        float y1 = b1 * HEIGHT;
        float up0 = -HALF + a0 * 2.0F * HALF; // runs with the axis
        float up1 = -HALF + a1 * 2.0F * HALF;
        float down0 = HALF - a0 * 2.0F * HALF; // runs against it
        float down1 = HALF - a1 * 2.0F * HALF;
        switch (face) {
            case 0 -> GhostQuads.quad(pose, c, color, light, 0, 0, -1, u0, vTop, u1, vBottom,
                    up0, y0, -HALF, up0, y1, -HALF, up1, y1, -HALF, up1, y0, -HALF);
            case 1 -> GhostQuads.quad(pose, c, color, light, 0, 0, 1, u0, vTop, u1, vBottom,
                    down0, y0, HALF, down0, y1, HALF, down1, y1, HALF, down1, y0, HALF);
            case 2 -> GhostQuads.quad(pose, c, color, light, -1, 0, 0, u0, vTop, u1, vBottom,
                    -HALF, y0, down0, -HALF, y1, down0, -HALF, y1, down1, -HALF, y0, down1);
            default -> GhostQuads.quad(pose, c, color, light, 1, 0, 0, u0, vTop, u1, vBottom,
                    HALF, y0, up0, HALF, y1, up0, HALF, y1, up1, HALF, y0, up1);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomAnchorEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
