package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomGatewayEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The Phantom Gateway: a doorway of the End portal's starfield in a ghostly lodestone frame, the
 * Phantom Anchor's own stone, with the phantom eyes peering out of the void on both sides. As big as
 * the entity says ({@link PhantomGatewayEntity#doorWidth()} by {@link PhantomGatewayEntity#doorHeight()}:
 * 3 x 3, 2 x 3 or 1 x 2); the eyes scale with its width. It swells open from its centre and shrinks shut ({@link PhantomGatewayEntity#openness}).
 * It follows the anchor's opacity setting, void included, and the frame its glow setting. Drawn from
 * vanilla textures, like the anchor, the void with {@link PhantomVoid}.
 */
public class PhantomGatewayRenderer extends EntityRenderer<PhantomGatewayEntity> {
    private static final ResourceLocation LODESTONE_SIDE = ResourceLocation.withDefaultNamespace("block/lodestone_side");
    private static final float PX = GhostQuads.PX;
    private static final float FRAME = 2.0F * PX;
    /** Half the frame's thickness, front to back. */
    private static final float DEPTH = 1.0F * PX;
    private static final int MIN_BLOCK_LIGHT = 7;
    /** The shared eyes' size (as {@link GhostQuads} draws them) suits a 1.5-block doorway; they scale from there. */
    private static final float EYES_HALF_W = 5.25F * PX;
    private static final float EYES_H = 4.5F * PX;
    private static final float EYES_AT_WIDTH = 1.5F;
    /** The starfield for each size, [width][height] in blocks, built the first time that size is drawn (render thread only). */
    private static final PhantomVoid[][] PANES =
            new PhantomVoid[PhantomGatewayEntity.MAX_WIDTH + 1][PhantomGatewayEntity.MAX_HEIGHT + 1];

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
        int blocks = Mth.clamp(entity.doorWidth(), PhantomGatewayEntity.MIN_WIDTH, PhantomGatewayEntity.MAX_WIDTH);
        int tall = Mth.clamp(entity.doorHeight(), PhantomGatewayEntity.MIN_HEIGHT, PhantomGatewayEntity.MAX_HEIGHT);
        float height = tall;
        float swell = open * open * (3.0F - 2.0F * open);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poseStack.translate(0.0F, height / 2.0F, 0.0F);
        poseStack.scale(swell, swell, 1.0F);
        poseStack.translate(0.0F, -height / 2.0F, 0.0F);
        PoseStack.Pose pose = poseStack.last();

        float alpha = PhantomClientConfig.anchorAlpha();
        if (PANES[blocks][tall] == null) {
            PANES[blocks][tall] = pane(blocks, tall);
        }
        PANES[blocks][tall].draw(pose, buffer, alpha);

        int ghost = PhantomClientConfig.white(alpha, 1.0F);
        int blockLight = PhantomClientConfig.anchorGlows()
                ? Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT) : LightTexture.block(packedLight);
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        frame(pose, buffer.getBuffer(PhantomRenderTypes.ghost(InventoryMenu.BLOCK_ATLAS)),
                GhostQuads.sprite(LODESTONE_SIDE), ghost, light, blocks / 2.0F, height);

        // Eyes in the void just under the top bar, on both faces, sized to the doorway.
        float scale = blocks / EYES_AT_WIDTH;
        float eyesTop = height - FRAME - 2.0F * PX * scale;
        GhostQuads.eyes(pose, buffer, 0.01F, eyesTop, alpha, EYES_HALF_W * scale, EYES_H * scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        GhostQuads.eyes(poseStack.last(), buffer, 0.01F, eyesTop, alpha, EYES_HALF_W * scale, EYES_H * scale);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * The starfield inside the frame of a doorway {@code blocks} wide and {@code tall} high, both faces (the
     * passes cull), cut into half-block cells so a big doorway still holds its stars to the screen up
     * close. Corners: a, a + up, a + up + across, a + across.
     */
    private static PhantomVoid pane(int blocks, int tall) {
        float halfWidth = blocks / 2.0F;
        float x0 = -halfWidth + FRAME;
        float width = 2.0F * (halfWidth - FRAME);
        float y0 = FRAME;
        float height = tall - 2.0F * FRAME;
        int cutsAcross = blocks * 2;
        int cutsUp = tall * 2;
        return PhantomVoid.builder()
                .grid(x0, y0, 0.0F, width, 0, 0, 0, height, 0, cutsAcross, cutsUp, 0, 0, 1)
                .grid(x0, y0 + height, 0.0F, width, 0, 0, 0, -height, 0, cutsAcross, cutsUp, 0, 0, -1)
                .build();
    }

    /**
     * Two posts and two bars round a doorway {@code halfWidth} either side of its middle and {@code height}
     * tall, each built of pieces at most a block long so the texture repeats rather than stretches.
     */
    private static void frame(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite stone, int color, int light,
                              float halfWidth, float height) {
        float inner = halfWidth - FRAME;
        for (float y = 0.0F; y < height - 1.0E-4F; y += 1.0F) {
            float top = Math.min(y + 1.0F, height);
            box(pose, c, stone, color, light, -halfWidth, y, -DEPTH, -inner, top, DEPTH);
            box(pose, c, stone, color, light, inner, y, -DEPTH, halfWidth, top, DEPTH);
        }
        for (float x = -inner; x < inner - 1.0E-4F; x += 1.0F) {
            float end = Math.min(x + 1.0F, inner);
            box(pose, c, stone, color, light, x, 0.0F, -DEPTH, end, FRAME, DEPTH);
            box(pose, c, stone, color, light, x, height - FRAME, -DEPTH, end, height, DEPTH);
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
