package com.phantomstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.phantomstorage.entity.PhantomTankEntity;
import com.phantomstorage.entity.TankStyle;
import com.phantomstorage.storage.PhantomTanks;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
 * or texture to maintain. Opacity and the light-level-7 floor come from {@link PhantomClientConfig}; a glowing fluid (lava) lights it regardless.
 *
 * <pre>
 *  ┌────────────┐ rim    1 px      phantom style: ghostly ender chest base and lid,
 *  │  │ glass │ │                  phantom wings off the rim, glowing phantom eyes on
 *  │  │ fluid │ │                  the front
 *  ╞══╧═══════╧═╡ base   2 px
 * </pre>
 */
public class PhantomTankRenderer extends EntityRenderer<PhantomTankEntity> {
    private static final ResourceLocation GLASS = ResourceLocation.withDefaultNamespace("block/tinted_glass");
    /** Glass and fluid alpha relative to the frame's: a touch clearer, and a touch more solid. */
    private static final float GLASS_FACTOR = 0xB0 / (float) 0xCC;
    private static final float FLUID_FACTOR = 0xD0 / (float) 0xCC;
    private static final int MIN_BLOCK_LIGHT = 7;
    private static final float PX = GhostQuads.PX;
    private static final float HALF = 11.0F / 32.0F;
    /** Matches the 0.75 entity height. */
    private static final float HEIGHT = 12.0F * PX;
    private static final float BASE = 2.0F * PX;
    private static final float RIM = PX;
    /** Glass and fluid sit this far inside the base and rim. */
    private static final float GLASS_INSET = 0.75F * PX;
    private static final ResourceLocation ENDER_CHEST = ResourceLocation.withDefaultNamespace("textures/entity/chest/ender.png");
    /** Ender chest texture regions (64x64 sheet, vanilla chest model UVs) as u0, v0, u1, v1 fractions. */
    private static final float[] ENDER_LID_TOP = GhostQuads.px(14, 0, 28, 14);
    private static final float[] ENDER_LID_UNDER = GhostQuads.px(28, 0, 42, 14);
    private static final float[] ENDER_LID_SIDE = GhostQuads.px(14, 15, 28, 18);
    private static final float[] ENDER_BASE_TOP = GhostQuads.px(14, 19, 28, 33);
    private static final float[] ENDER_BASE_UNDER = GhostQuads.px(28, 19, 42, 33);
    private static final float[] ENDER_BASE_SIDE = GhostQuads.px(14, 40, 28, 43);
    /** The wing root starts this far inside the lid edge, so it grows out from under the lid. */
    private static final float WING_TUCK = 1.5F * PX;
    private static final float INSET = 1.0F / 32.0F;
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

        float alpha = PhantomClientConfig.tankAlpha();
        int ghost = PhantomClientConfig.white(alpha, 1.0F);
        int fluidAlpha = PhantomClientConfig.white(alpha, FLUID_FACTOR) & 0xFF000000;
        int blockLight = PhantomClientConfig.tankGlows()
                ? Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT) : LightTexture.block(packedLight);
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
            int color = fluidAlpha | (ext.getTintColor() & 0xFFFFFF);
            box(pose, consumer, GhostQuads.sprite(ext.getStillTexture()), -inner, bottom, -inner, inner, top, inner, color, light);
            bottom = top;
        }
        float glass = HALF - GLASS_INSET;
        switch (entity.getDisplayStyle()) {
            case PHANTOM -> {
                VertexConsumer ender = buffer.getBuffer(RenderType.entityTranslucent(ENDER_CHEST));
                uvBox(pose, ender, -HALF, 0.0F, -HALF, HALF, BASE, HALF,
                        ENDER_BASE_TOP, ENDER_BASE_UNDER, ENDER_BASE_SIDE, ghost, light);
                uvBox(pose, ender, -HALF, HEIGHT - RIM, -HALF, HALF, HEIGHT, HALF,
                        ENDER_LID_TOP, ENDER_LID_UNDER, ENDER_LID_SIDE, ghost, light);
                VertexConsumer wings = buffer.getBuffer(RenderType.entityTranslucent(GhostQuads.PHANTOM));
                float flap = GhostQuads.flap(age);
                GhostQuads.wing(pose, wings, 1, flap, HALF - WING_TUCK, HEIGHT - RIM * 0.5F, ghost, light);
                GhostQuads.wing(pose, wings, -1, flap, HALF - WING_TUCK, HEIGHT - RIM * 0.5F, ghost, light);
                consumer = buffer.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            }
        }
        box(pose, consumer, GhostQuads.sprite(GLASS), -glass, BASE, -glass, glass, HEIGHT - RIM, glass, PhantomClientConfig.white(alpha, GLASS_FACTOR), light);
        if (entity.getDisplayStyle() == TankStyle.PHANTOM) {
            // Glowing eyes on the front of the glass, just under the rim; full-bright like a phantom's.
            GhostQuads.eyes(pose, buffer, glass + 0.002F, HEIGHT - RIM - 0.5F * PX, alpha);
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** A box whose faces each show a fixed region of the bound texture, stretched to fit. */
    private static void uvBox(PoseStack.Pose pose, VertexConsumer c, float x0, float y0, float z0, float x1, float y1, float z1,
                              float[] top, float[] bottom, float[] side, int color, int light) {
        GhostQuads.quad(pose, c, color, light, 0, 1, 0, top[0], top[1], top[2], top[3], x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        GhostQuads.quad(pose, c, color, light, 0, -1, 0, bottom[0], bottom[1], bottom[2], bottom[3], x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        GhostQuads.quad(pose, c, color, light, 0, 0, -1, side[0], side[1], side[2], side[3], x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        GhostQuads.quad(pose, c, color, light, 0, 0, 1, side[0], side[1], side[2], side[3], x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        GhostQuads.quad(pose, c, color, light, -1, 0, 0, side[0], side[1], side[2], side[3], x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        GhostQuads.quad(pose, c, color, light, 1, 0, 0, side[0], side[1], side[2], side[3], x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** All six faces; side faces show only as much of the sprite as the box is tall. */
    private static void box(PoseStack.Pose pose, VertexConsumer c, TextureAtlasSprite s,
                            float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        float u0 = s.getU0();
        float u1 = s.getU1();
        float v0 = s.getV0();
        float v1 = s.getV1();
        float vTop = v1 - (v1 - v0) * Math.min(1.0F, (y1 - y0) / (2.0F * HALF));
        GhostQuads.quad(pose, c, color, light, 0, 1, 0, u0, v0, u1, v1, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        GhostQuads.quad(pose, c, color, light, 0, -1, 0, u0, v0, u1, v1, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        GhostQuads.quad(pose, c, color, light, 0, 0, -1, u0, vTop, u1, v1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        GhostQuads.quad(pose, c, color, light, 0, 0, 1, u0, vTop, u1, v1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        GhostQuads.quad(pose, c, color, light, -1, 0, 0, u0, vTop, u1, v1, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        GhostQuads.quad(pose, c, color, light, 1, 0, 0, u0, vTop, u1, v1, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomTankEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
