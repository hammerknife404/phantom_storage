package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomTankEntity;
import com.phantomstorage.entity.TankStyle;
import com.phantomstorage.storage.PhantomTanks;
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
 * A ghostly tinted-glass tank in a {@link TankStyle} frame (base slab, top rim, corner posts), with
 * every tank's fluid inside, stacked bottom-up in tank order with the active tank's layer moved to
 * the top, each layer a quarter of the fluid space when its tank is full. Bobs like the chest.
 * Everything is drawn from vanilla block-atlas sprites, so there is no model or entity texture to
 * maintain. Never darker than light level 7, or the brightest fluid's own light level (lava).
 *
 * <pre>
 *  ┌▓┬────────┬▓┐ rim    1 px
 *  │▓│ glass  │▓│ posts  1.5 px square
 *  │▓│ fluid  │▓│
 *  ╞▓╧════════╧▓╡ base   2 px
 * </pre>
 */
public class PhantomTankRenderer extends EntityRenderer<PhantomTankEntity> {
    private static final ResourceLocation GLASS = ResourceLocation.withDefaultNamespace("block/tinted_glass");
    /** ARGB: extra translucency only; the texture already carries the violet tint. */
    private static final int GLASS_COLOR = 0xB0FFFFFF;
    private static final int FLUID_ALPHA = 0xD0;
    private static final int MIN_BLOCK_LIGHT = 7;
    private static final float PX = 1.0F / 16.0F;
    private static final float HALF = 11.0F / 32.0F;
    /** Matches the 0.75 entity height. */
    private static final float HEIGHT = 12.0F * PX;
    private static final float BASE = 2.0F * PX;
    private static final float RIM = PX;
    private static final float POST = 1.5F * PX;
    private static final float INSET = 1.0F / 32.0F;
    private static final int FRAME_COLOR = 0xFFFFFFFF;
    private static final float MIN_LAYER = 0.08F;

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

        int blockLight = Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT);
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            Fluid fluid = entity.getDisplayFluid(i);
            if (fluid != Fluids.EMPTY) {
                blockLight = Math.max(blockLight, fluid.getFluidType().getLightLevel());
            }
        }
        int light = LightTexture.pack(blockLight, LightTexture.sky(packedLight));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();

        float inner = HALF - POST;
        float floor = BASE + INSET;
        float layerMax = (HEIGHT - RIM - INSET - floor) / PhantomTanks.TANKS;
        float bottom = floor;
        int active = entity.getDisplayActive();
        for (int n = 0; n < PhantomTanks.TANKS; n++) {
            // Every other tank in order, then the active one last, on top.
            int i = n == PhantomTanks.TANKS - 1 ? active : n < active ? n : n + 1;
            Fluid fluid = entity.getDisplayFluid(i);
            float fill = Mth.clamp(entity.getDisplayFill(i), 0.0F, 1.0F);
            if (fluid == Fluids.EMPTY || fill <= 0.0F) {
                continue;
            }
            // A sliver of fluid still shows as a thin layer.
            float top = bottom + layerMax * Math.max(fill, MIN_LAYER);
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            int color = FLUID_ALPHA << 24 | (ext.getTintColor() & 0xFFFFFF);
            box(pose, consumer, sprite(ext.getStillTexture()), -inner, bottom, -inner, inner, top, inner, color, light);
            bottom = top;
        }
        frame(pose, consumer, entity.getDisplayStyle(), light);
        float glass = HALF - POST / 2.0F;
        box(pose, consumer, sprite(GLASS), -glass, BASE, -glass, glass, HEIGHT - RIM, glass, GLASS_COLOR, light);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void frame(PoseStack.Pose pose, VertexConsumer c, TankStyle style, int light) {
        box(pose, c, sprite(style.base), -HALF, 0.0F, -HALF, HALF, BASE, HALF, FRAME_COLOR, light);
        box(pose, c, sprite(style.rim), -HALF, HEIGHT - RIM, -HALF, HALF, HEIGHT, HALF, FRAME_COLOR, light);
        TextureAtlasSprite post = sprite(style.post);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x0 = sx < 0 ? -HALF : HALF - POST;
                float z0 = sz < 0 ? -HALF : HALF - POST;
                post(pose, c, post, style, x0, BASE, z0, x0 + POST, HEIGHT - RIM, z0 + POST, light);
            }
        }
    }

    /** A post: the style's UV strip stretched over its whole height on each side; no end caps needed. */
    private static void post(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s, TankStyle style,
                             float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float u0 = Mth.lerp(style.postU0, s.getU0(), s.getU1());
        float u1 = Mth.lerp(style.postU1, s.getU0(), s.getU1());
        float vTop = Mth.lerp(style.postV0, s.getV0(), s.getV1());
        float vBottom = Mth.lerp(style.postV1, s.getV0(), s.getV1());
        int color = FRAME_COLOR;
        quad(pose, c, color, light, 0, 0, -1, u0, vTop, u1, vBottom, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, c, color, light, 0, 0, 1, u0, vTop, u1, vBottom, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(pose, c, color, light, -1, 0, 0, u0, vTop, u1, vBottom, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(pose, c, color, light, 1, 0, 0, u0, vTop, u1, vBottom, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
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
