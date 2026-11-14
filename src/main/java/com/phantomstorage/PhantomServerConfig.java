package com.phantomstorage;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per world ({@code serverconfig/phantomstorage-server.toml}; in-game from the Mods list in single player,
 * or as an operator). The server sends it to every client on login, so the rider's client, which flies the
 * chest, uses the server's values.
 */
public final class PhantomServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue SADDLE_SPEED;
    private static final ModConfigSpec.BooleanValue SADDLE_SPRINT;
    private static final ModConfigSpec.DoubleValue SADDLE_SPRINT_SPEED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("saddle");
        SADDLE_SPEED = builder
                .comment("How fast the ridden Phantom Chest flies, in blocks a second. A player walks at 4.3.")
                .defineInRange("speed", 4.0, 1.0, 20.0);
        SADDLE_SPRINT = builder
                .comment("Whether sprinting while riding flies at sprintSpeed instead.")
                .define("sprint", false);
        SADDLE_SPRINT_SPEED = builder
                .comment("How fast it flies while the rider sprints, in blocks a second, if sprint is on. A player sprints at 5.6.")
                .defineInRange("sprintSpeed", 5.4, 1.0, 30.0);
        builder.pop();
        SPEC = builder.build();
    }

    private PhantomServerConfig() {}

    /** Ridden cruising speed, in blocks a tick. */
    public static double saddleSpeed() {
        return SPEC.isLoaded() ? SADDLE_SPEED.get() / 20.0 : 0.2;
    }

    public static boolean saddleSprint() {
        return SPEC.isLoaded() && SADDLE_SPRINT.get();
    }

    /** Ridden sprinting speed, in blocks a tick. */
    public static double saddleSprintSpeed() {
        return SPEC.isLoaded() ? SADDLE_SPRINT_SPEED.get() / 20.0 : 0.27;
    }
}
