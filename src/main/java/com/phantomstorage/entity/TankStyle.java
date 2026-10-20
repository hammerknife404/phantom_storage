package com.phantomstorage.entity;

import net.minecraft.resources.ResourceLocation;

/**
 * Frame looks for the Phantom Tank, all vanilla block textures: a base slab, a top rim and four
 * corner posts around the tinted glass. The tank moves to the next style each time it's summoned.
 * Self-contained so it can be cut down to one style later.
 *
 * <p>Post UVs are fractions of the post texture, so thin textures (chain, end rod) use just their
 * visible strip instead of being squashed whole.
 */
public enum TankStyle {
    CRYING_OBSIDIAN("crying_obsidian", "obsidian", "iron_block", 0.0F, 0.0F, 1.0F, 1.0F),
    CAULDRON("cauldron_bottom", "cauldron_top", "cauldron_side", 0.0F, 0.0F, 1.0F, 1.0F),
    COPPER("cut_copper", "oxidized_cut_copper", "copper_block", 0.0F, 0.0F, 1.0F, 1.0F),
    END("purpur_block", "purpur_block", "end_rod", 0.0F, 0.0F, 2.0F / 16.0F, 15.0F / 16.0F),
    DEEPSLATE("polished_deepslate", "polished_deepslate", "chain", 6.0F / 16.0F, 0.0F, 9.0F / 16.0F, 1.0F);

    public final ResourceLocation base;
    public final ResourceLocation rim;
    public final ResourceLocation post;
    public final float postU0;
    public final float postV0;
    public final float postU1;
    public final float postV1;

    TankStyle(String base, String rim, String post, float postU0, float postV0, float postU1, float postV1) {
        this.base = block(base);
        this.rim = block(rim);
        this.post = block(post);
        this.postU0 = postU0;
        this.postV0 = postV0;
        this.postU1 = postU1;
        this.postV1 = postV1;
    }

    public static TankStyle byIndex(int index) {
        TankStyle[] styles = values();
        return styles[Math.floorMod(index, styles.length)];
    }

    private static ResourceLocation block(String name) {
        return ResourceLocation.withDefaultNamespace("block/" + name);
    }
}
