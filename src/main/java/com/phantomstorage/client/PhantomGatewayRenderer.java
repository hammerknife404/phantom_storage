package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomGatewayEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/**
 * The Phantom Gateway: a doorway of the End portal's starfield in a ghostly lodestone frame, the
 * Phantom Anchor's own stone, with the phantom eyes peering out of the void on both sides. It swells
 * open from its centre and shrinks shut ({@link PhantomGatewayEntity#openness}). The frame follows the
 * anchor's opacity and glow settings. Drawn from vanilla textures and shaders, like the anchor.
 */
public class PhantomGatewayRenderer extends EntityRenderer<PhantomGatewayEntity> {
    private static final ResourceLocation LODESTONE_SIDE = ResourceLocation.withDefaultNamespace("block/lodestone_side");
    private static final float PX = GhostQuads.PX;
    private static final float HALF_WIDTH = 12.0F * PX;
    private static final float HEIGHT = 32.0F * PX;
    private static final float FRAME = 2.0F * PX;
    /** Half the frame's thickness, front to back. */
    private static final float DEPTH = 1.0F * PX;
    private static final int MIN_BLOCK_LIGHT = 7;

    public PhantomGatewayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(PhantomGatewayEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float open = entity.openness(partialTick);
        if (open <= 0.0F) {
            return;
        }
        float swell = open * open * (3.0F - 2.0F * open);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.translate(0.0F, HEIGHT / 2.0F, 0.0F);
        poseStack.scale(swell, swell, 1.0F);
        poseStack.translate(0.0F, -HEIGHT / 2.0F, 0.0F);
        PoseStack.Pose pose = poseStack.last();

        pane(pose.pose(), buffer.getBuffer(RenderType.endPortal()));

        float alpha = PhantomClientConfig.anchorAlpha();
        int ghost = PhantomClientConfig.white(alpha, 1.0F);
        int blockLight = PhantomClientConfig.anchorGlows()
                ? Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT) : LightTexture.block(packedLight);
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        frame(pose, buffer.getBuffer(PhantomRenderTypes.ghost(InventoryMenu.BLOCK_ATLAS)),
                GhostQuads.sprite(LODESTONE_SIDE), ghost, light);

        // Eyes in the void just under the top bar, on both faces.
        float eyesTop = HEIGHT - FRAME - 2.0F * PX;
        GhostQuads.eyes(pose, buffer, 0.01F, eyesTop, alpha);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        GhostQuads.eyes(poseStack.last(), buffer, 0.01F, eyesTop, alpha);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** The starfield inside the frame, both faces (the shader's render type culls). */
    private static void pane(Matrix4f m, VertexConsumer c) {
        float x0 = -HALF_WIDTH + FRAME;
        float x1 = HALF_WIDTH - FRAME;
        float y0 = FRAME;
        float y1 = HEIGHT - FRAME;
        c.addVertex(m, x0, y0, 0.0F);
        c.addVertex(m, x1, y0, 0.0F);
        c.addVertex(m, x1, y1, 0.0F);
        c.addVertex(m, x0, y1, 0.0F);
        c.addVertex(m, x0, y1, 0.0F);
        c.addVertex(m, x1, y1, 0.0F);
        c.addVertex(m, x1, y0, 0.0F);
        c.addVertex(m, x0, y0, 0.0F);
    }

    /** Two posts and two bars, each built of pieces at most a block long so the texture repeats rather than stretches. */
    private static void frame(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite stone, int color, int light) {
        float inner = HALF_WIDTH - FRAME;
        for (float half : new float[] {0.0F, HEIGHT / 2.0F}) {
            box(pose, c, stone, color, light, -HALF_WIDTH, half, -DEPTH, -inner, half + HEIGHT / 2.0F, DEPTH);
            box(pose, c, stone, color, light, inner, half, -DEPTH, HALF_WIDTH, half + HEIGHT / 2.0F, DEPTH);
        }
        for (int side = -1; side <= 1; side += 2) {
            float x0 = Math.min(0.0F, side * inner);
            float x1 = Math.max(0.0F, side * inner);
            box(pose, c, stone, color, light, x0, 0.0F, -DEPTH, x1, FRAME, DEPTH);
            box(pose, c, stone, color, light, x0, HEIGHT - FRAME, -DEPTH, x1, HEIGHT, DEPTH);
        }
    }

    /**
     * A box's six faces, each wound counter-clockwise seen from outside (the ghost render type culls),
     * each showing a patch of {@code stone} its own size in pixels, so nothing stretches.
     */
    private static void box(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite stone, int color, int light,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        float w = x1 - x0;
        float h = y1 - y0;
        float d = z1 - z0;
        // Faces as (normal, corners from the viewer's bottom-left, counter-clockwise), with the face's size.
        face(pose, c, stone, color, light, 0, 0, 1, w, h, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(pose, c, stone, color, light, 0, 0, -1, w, h, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        face(pose, c, stone, color, light, 1, 0, 0, d, h, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        face(pose, c, stone, color, light, -1, 0, 0, d, h, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(pose, c, stone, color, light, 0, 1, 0, w, d, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(pose, c, stone, color, light, 0, -1, 0, w, d, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite stone, int color, int light,
                             float nx, float ny, float nz, float width, float height,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float u0 = stone.getU(0.0F);
        float u1 = stone.getU(Math.min(width, 1.0F));
        float v0 = stone.getV(0.0F);
        float v1 = stone.getV(Math.min(height, 1.0F));
        GhostQuads.vertex(pose, c, color, light, nx, ny, nz, ax, ay, az, u0, v1);
        GhostQuads.vertex(pose, c, color, light, nx, ny, nz, bx, by, bz, u1, v1);
        GhostQuads.vertex(pose, c, color, light, nx, ny, nz, cx, cy, cz, u1, v0);
        GhostQuads.vertex(pose, c, color, light, nx, ny, nz, dx, dy, dz, u0, v0);
    }

    @Override
    protected boolean shouldShowName(PhantomGatewayEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomGatewayEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
