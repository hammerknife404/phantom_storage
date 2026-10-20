package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomTankEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * A ghostly glass cube with the tank's fullest fluid inside, filled to the overall level, bobbing
 * like the chest. Both are drawn from block-atlas sprites (vanilla glass, the fluid's own still
 * texture), so there is no model or entity texture to maintain. Never darker than light level 7,
 * or the fluid's own light level if brighter (lava).
 */
public class PhantomTankRenderer extends EntityRenderer<PhantomTankEntity> {
    private static final ResourceLocation GLASS = ResourceLocation.withDefaultNamespace("block/glass");
    /** ARGB: translucent with a faint violet tint, to match the chest. */
    private static final int GLASS_COLOR = 0xB0D8CCFF;
    private static final int FLUID_ALPHA = 0xD0;
    private static final int MIN_BLOCK_LIGHT = 7;
    private static final float HALF = 11.0F / 32.0F;
    private static final float HEIGHT = 0.6875F;
    private static final float INSET = 1.0F / 32.0F;

    public PhantomTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
        this.shadowStrength = 0.4F;
    }

    @Override
    public void render(PhantomTankEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float age = entity.tickCount + partialTick;
        poseStack.translate(0.0, 0.06 + Mth.sin(age * 0.08F) * 0.06F, 0.0);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        Fluid fluid = entity.getDisplayFluid();
        float fill = Mth.clamp(entity.getDisplayFill(), 0.0F, 1.0F);
        int blockLight = Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT);
        if (fluid != Fluids.EMPTY) {
            blockLight = Math.max(blockLight, fluid.getFluidType().getLightLevel());
        }
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();

        if (fluid != Fluids.EMPTY && fill > 0.0F) {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            int color = FLUID_ALPHA << 24 | (ext.getTintColor() & 0xFFFFFF);
            float inner = HALF - INSET;
            float top = INSET + (HEIGHT - 2.0F * INSET) * Math.max(fill, 0.04F);
            box(pose, consumer, sprite(ext.getStillTexture()), -inner, INSET, -inner, inner, top, inner, color, light);
        }
        box(pose, consumer, sprite(GLASS), -HALF, 0.0F, -HALF, HALF, HEIGHT, HALF, GLASS_COLOR, light);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    /** All six faces; side faces show only as much of the sprite as the box is tall. */
    private static void box(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s,
                            float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        float u0 = s.getU0();
        float u1 = s.getU1();
        float v0 = s.getV0();
        float v1 = s.getV1();
        float vTop = v1 - (v1 - v0) * Math.min(1.0F, (y1 - y0) / (2.0F * HALF));
        quad(pose, c, color, light, 0, 1, 0, u0, v0, u1, v1, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, c, color, light, 0, -1, 0, u0, v0, u1, v1, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(pose, c, color, light, 0, 0, -1, u0, vTop, u1, v1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, c, color, light, 0, 0, 1, u0, vTop, u1, v1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(pose, c, color, light, -1, 0, 0, u0, vTop, u1, v1, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(pose, c, color, light, 1, 0, 0, u0, vTop, u1, v1, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** Corners in order bottom-left, top-left, top-right, bottom-right of the face's texture. */
    private static void quad(PoseStack.Pose pose, VertexConsumer c, int color, int light, float nx, float ny, float nz,
                             float u0, float vTop, float u1, float vBottom,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, c, color, light, nx, ny, nz, ax, ay, az, u0, vBottom);
        vertex(pose, c, color, light, nx, ny, nz, bx, by, bz, u0, vTop);
        vertex(pose, c, color, light, nx, ny, nz, cx, cy, cz, u1, vTop);
        vertex(pose, c, color, light, nx, ny, nz, dx, dy, dz, u1, vBottom);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int color, int light, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        c.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomTankEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
