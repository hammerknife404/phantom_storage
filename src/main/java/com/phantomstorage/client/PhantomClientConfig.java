package com.phantomstorage.client;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only look of the pets ({@code config/phantomstorage-client.toml}, also editable in-game from
 * the Mods list). Values are read every frame, so changes show at once.
 */
public final class PhantomClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue ALL_PETS_OPACITY;
    private static final ModConfigSpec.IntValue CHEST_OPACITY;
    private static final ModConfigSpec.IntValue TANK_OPACITY;
    private static final ModConfigSpec.BooleanValue CHEST_GLOW;
    private static final ModConfigSpec.BooleanValue TANK_GLOW;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("opacity");
        ALL_PETS_OPACITY = builder
                .comment("Opacity of every phantom pet, in percent. Multiplies the per-pet values below.")
                .defineInRange("allPets", 100, 10, 100);
        CHEST_OPACITY = builder
                .comment("Phantom Chest opacity, in percent.")
                .defineInRange("chest", 80, 10, 100);
        TANK_OPACITY = builder
                .comment("Phantom Tank opacity, in percent: frame and wings; the glass and fluid scale with it.")
                .defineInRange("tank", 80, 10, 100);
        builder.pop();
        builder.push("glow");
        CHEST_GLOW = builder
                .comment("Never render the Phantom Chest darker than light level 7.")
                .define("chest", true);
        TANK_GLOW = builder
                .comment("Never render the Phantom Tank darker than light level 7 (its eyes always glow).")
                .define("tank", true);
        builder.pop();
        SPEC = builder.build();
    }

    private PhantomClientConfig() {}

    /** 0..1: the chest's opacity times the all-pets opacity. */
    public static float chestAlpha() {
        return alpha(CHEST_OPACITY);
    }

    /** 0..1: the tank's opacity times the all-pets opacity. */
    public static float tankAlpha() {
        return alpha(TANK_OPACITY);
    }

    public static boolean chestGlows() {
        return CHEST_GLOW.get();
    }

    public static boolean tankGlows() {
        return TANK_GLOW.get();
    }

    /** {@code alpha} (0..1) in the top byte of an otherwise white ARGB colour, scaled by {@code factor}. */
    public static int white(float alpha, float factor) {
        return Mth.clamp(Math.round(alpha * factor * 255.0F), 0, 255) << 24 | 0xFFFFFF;
    }

    private static float alpha(ModConfigSpec.IntValue pet) {
        return pet.get() / 100.0F * (ALL_PETS_OPACITY.get() / 100.0F);
    }
}
