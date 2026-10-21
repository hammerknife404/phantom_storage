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
 * A ghostly tinted-glass tank in a {@link TankStyle} frame (base slab and top rim), with every tank's
 * fluid inside, stacked bottom-up in tank order with the active tank's layer moved to the top, each
 * layer a quarter of the fluid space when its tank is full. Bobs like the chest. Everything is drawn
 * from vanilla textures (block atlas, or the ender chest's own model texture), so there is no model
 * or texture to maintain. Never darker than light level 7, or the brightest fluid's own light (lava).
 *
 * <pre>
 *  ┌────────────┐ rim    1 px      phantom styles: ghostly ender chest base and lid,
 *  │  │ glass │ │                  phantom wings off the rim, glowing phantom eyes on
 *  │  │ fluid │ │                  the front (optionally the chest latch between them)
 *  ╞══╧═══════╧═╡ base   2 px
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
    /** Glass and fluid sit this far inside the base and rim. */
    private static final float GLASS_INSET = 0.75F * PX;
    private static final ResourceLocation DEEPSLATE = ResourceLocation.withDefaultNamespace("block/polished_deepslate");
    private static final ResourceLocation ENDER_CHEST = ResourceLocation.withDefaultNamespace("textures/entity/chest/ender.png");
    /** Ender chest texture regions (64x64 sheet, vanilla chest model UVs) as u0, v0, u1, v1 fractions. */
    private static final float[] ENDER_LID_TOP = px(14, 0, 28, 14);
    private static final float[] ENDER_LID_UNDER = px(28, 0, 42, 14);
    private static final float[] ENDER_LID_SIDE = px(14, 15, 28, 18);
    private static final float[] ENDER_BASE_TOP = px(14, 19, 28, 33);
    private static final float[] ENDER_BASE_UNDER = px(28, 19, 42, 33);
    private static final float[] ENDER_BASE_SIDE = px(14, 40, 28, 43);
    private static final float[] ENDER_LOCK = px(1, 1, 3, 5);
    /** ARGB: the ender chest parts are ghostly, like the chest pet. */
    private static final int GHOST_COLOR = 0xCCFFFFFF;

    private static final ResourceLocation PHANTOM = ResourceLocation.withDefaultNamespace("textures/entity/phantom.png");
    private static final ResourceLocation PHANTOM_EYES = ResourceLocation.withDefaultNamespace("textures/entity/phantom_eyes.png");
    /** Phantom model UVs (64x64): the wing tip's top face (13x9 at (25,24)) and the head front (7x3 at (5,5)). */
    private static final float[] WING = px(25, 24, 38, 33);
    private static final float[] EYES = px(5, 5, 12, 8);
    /** Wings at half the phantom's size: 3 px inner half, 6.5 px outer half, 4.5 px deep at the root. */
    private static final float WING_BASE = 3.0F * PX;
    private static final float WING_TIP = 6.5F * PX;
    private static final float WING_DEPTH = 4.5F * PX;
    /** The wing root starts this far inside the lid edge, so it grows out from under the lid. */
    private static final float WING_TUCK = 1.5F * PX;
    /** Fraction of the root depth the outer edge loses at the front: the tip tapers and sweeps back. */
    private static final float WING_TAPER = 0.4F;
    private static final float FLAP_SPEED = 0.18F;
    private static final float FLAP_DEGREES = 18.0F;
    /** Eyes at 1.5x the phantom's 7x3 px, so the 2 px latch fits between them. */
    private static final float EYES_HALF_W = 5.25F * PX;
    private static final float EYES_H = 4.5F * PX;
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

        float inner = HALF - GLASS_INSET - INSET;
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
        float glass = HALF - GLASS_INSET;
        switch (entity.getDisplayStyle()) {
            case DEEPSLATE -> {
                TextureAtlasSprite slab = sprite(DEEPSLATE);
                box(pose, consumer, slab, -HALF, 0.0F, -HALF, HALF, BASE, HALF, FRAME_COLOR, light);
                box(pose, consumer, slab, -HALF, HEIGHT - RIM, -HALF, HALF, HEIGHT, HALF, FRAME_COLOR, light);
            }
            case PHANTOM, PHANTOM_LATCH -> {
                VertexConsumer ender = buffer.getBuffer(RenderType.entityTranslucent(ENDER_CHEST));
                uvBox(pose, ender, -HALF, 0.0F, -HALF, HALF, BASE, HALF,
                        ENDER_BASE_TOP, ENDER_BASE_UNDER, ENDER_BASE_SIDE, GHOST_COLOR, light);
                uvBox(pose, ender, -HALF, HEIGHT - RIM, -HALF, HALF, HEIGHT, HALF,
                        ENDER_LID_TOP, ENDER_LID_UNDER, ENDER_LID_SIDE, GHOST_COLOR, light);
                if (entity.getDisplayStyle() == TankStyle.PHANTOM_LATCH) {
                    // The chest's latch, hanging from the rim on the front (+z, the way the pet faces).
                    uvBox(pose, ender, -PX, HEIGHT - RIM - 3.0F * PX, glass, PX, HEIGHT - RIM, glass + PX,
                            ENDER_LOCK, ENDER_LOCK, ENDER_LOCK, GHOST_COLOR, light);
                }
                VertexConsumer wings = buffer.getBuffer(RenderType.entityTranslucent(PHANTOM));
                float flap = Mth.sin(age * FLAP_SPEED) * FLAP_DEGREES;
                wing(pose, wings, 1, flap, light);
                wing(pose, wings, -1, flap, light);
                consumer = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            }
        }
        box(pose, consumer, sprite(GLASS), -glass, BASE, -glass, glass, HEIGHT - RIM, glass, GLASS_COLOR, light);
        if (entity.getDisplayStyle() != TankStyle.DEEPSLATE) {
            // Glowing eyes on the front of the glass, just under the rim; full-bright like a phantom's.
            VertexConsumer eyes = buffer.getBuffer(RenderType.eyes(PHANTOM_EYES));
            float z = glass + 0.002F;
            float top = HEIGHT - RIM - 0.5F * PX;
            // The eyes render type culls back faces, so wind counter-clockwise as seen from the front.
            int bright = LightTexture.FULL_BRIGHT;
            vertex(pose, eyes, 0xFFFFFFFF, bright, 0, 0, 1, -EYES_HALF_W, top - EYES_H, z, EYES[0], EYES[3]);
            vertex(pose, eyes, 0xFFFFFFFF, bright, 0, 0, 1, EYES_HALF_W, top - EYES_H, z, EYES[2], EYES[3]);
            vertex(pose, eyes, 0xFFFFFFFF, bright, 0, 0, 1, EYES_HALF_W, top, z, EYES[2], EYES[1]);
            vertex(pose, eyes, 0xFFFFFFFF, bright, 0, 0, 1, -EYES_HALF_W, top, z, EYES[0], EYES[1]);
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** A box whose faces each show a fixed region of the bound texture, stretched to fit. */
    /**
     * One phantom wing on side {@code side} (+1 = +x, -1 = -x): a single thin membrane, no box edges,
     * rooted under the lid. The inner half swings {@code flap} degrees and the outer half twice that,
     * like the phantom's, but both halves share the joint vertices and one continuous stretch of the
     * wing texture, so the wing bends there instead of looking bolted together. Drawn once; the render
     * type doesn't cull, so it shows from above and below.
     */
    private static void wing(PoseStack.Pose pose, VertexConsumer c, int side, float flap, int light) {
        float a = flap * Mth.DEG_TO_RAD;
        float rootX = HALF - WING_TUCK;
        float rootY = HEIGHT - RIM * 0.5F;
        float jointX = rootX + Mth.cos(a) * WING_BASE;
        float jointY = rootY + Mth.sin(a) * WING_BASE;
        float tipX = jointX + Mth.cos(2.0F * a) * WING_TIP;
        float tipY = jointY + Mth.sin(2.0F * a) * WING_TIP;
        float z0 = -WING_DEPTH / 2.0F;
        float z1 = WING_DEPTH / 2.0F;
        float tipZ0 = z0 + WING_DEPTH * WING_TAPER;

        float u0 = WING[0];
        float uJoint = Mth.lerp(WING_BASE / (WING_BASE + WING_TIP), WING[0], WING[2]);
        float u1 = WING[2];
        float v0 = WING[1];
        float v1 = WING[3];
        float vTip0 = Mth.lerp(WING_TAPER, v0, v1);
        int color = GHOST_COLOR;
        // Inner half: root -> joint.
        vertex(pose, c, color, light, 0, 1, 0, side * rootX, rootY, z0, u0, v0);
        vertex(pose, c, color, light, 0, 1, 0, side * rootX, rootY, z1, u0, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z1, uJoint, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z0, uJoint, v0);
        // Outer half: joint -> tapered tip.
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z0, uJoint, v0);
        vertex(pose, c, color, light, 0, 1, 0, side * jointX, jointY, z1, uJoint, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * tipX, tipY, z1, u1, v1);
        vertex(pose, c, color, light, 0, 1, 0, side * tipX, tipY, tipZ0, u1, vTip0);
    }

    private static void uvBox(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0, float x1, float y1, float z1,
                              float[] top, float[] bottom, float[] side, int color, int light) {
        quad(pose, c, color, light, 0, 1, 0, top[0], top[1], top[2], top[3], x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, c, color, light, 0, -1, 0, bottom[0], bottom[1], bottom[2], bottom[3], x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(pose, c, color, light, 0, 0, -1, side[0], side[1], side[2], side[3], x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, c, color, light, 0, 0, 1, side[0], side[1], side[2], side[3], x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(pose, c, color, light, -1, 0, 0, side[0], side[1], side[2], side[3], x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(pose, c, color, light, 1, 0, 0, side[0], side[1], side[2], side[3], x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** Pixel rectangle on a 64x64 texture as u0, v0, u1, v1 fractions. */
    private static float[] px(int u0, int v0, int u1, int v1) {
        return new float[] {u0 / 64.0F, v0 / 64.0F, u1 / 64.0F, v1 / 64.0F};
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
