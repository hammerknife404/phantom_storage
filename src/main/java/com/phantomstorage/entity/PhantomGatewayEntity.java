package com.phantomstorage.entity;

import com.phantomstorage.summon.GatewayManager;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Phantom Gateway: a short-lived doorway, opened with the charm, onto its owner's anchored Phantom
 * Anchor. Whatever steps into it comes out beside the anchor, in whichever dimension that is; the
 * travel itself is {@link GatewayManager}'s. It is square to the block grid and turned to the way it
 * was opened ({@link #getYRot()}). Standing, it is {@link #doorWidth()} by {@link #doorHeight()} blocks
 * (3 x 3, 2 x 3 or 1 x 2) and its box is the doorway itself, a block deep; lying flat ({@link Lie}),
 * opened looking steeply down or up, it is a square (3, 2 or 1 blocks a side) and its box is the
 * block layer it lies in. It holds nothing, and like the pets is never saved, so it can't outlive the
 * session.
 */
public class PhantomGatewayEntity extends Entity {
    /** Game time it closes at, so the client can shrink it shut on time. */
    private static final EntityDataAccessor<Long> DATA_CLOSES_AT =
            SynchedEntityData.defineId(PhantomGatewayEntity.class, EntityDataSerializers.LONG);
    /** Width and height in blocks, synced so the client's box and look match the server's. */
    private static final EntityDataAccessor<Integer> DATA_WIDTH =
            SynchedEntityData.defineId(PhantomGatewayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEIGHT =
            SynchedEntityData.defineId(PhantomGatewayEntity.class, EntityDataSerializers.INT);
    /** {@link Lie}, by ordinal. */
    private static final EntityDataAccessor<Integer> DATA_LIE =
            SynchedEntityData.defineId(PhantomGatewayEntity.class, EntityDataSerializers.INT);
    /** Ticks it takes to open, and to close. */
    public static final int SWING_TICKS = 10;
    public static final int MAX_WIDTH = 3;
    public static final int MIN_WIDTH = 1;
    public static final int MAX_HEIGHT = 3;
    public static final int MIN_HEIGHT = 1;
    /** Half its box's depth: whatever comes this close to the doorway's face goes through. */
    public static final double HALF_DEPTH = 0.5;

    /**
     * How it stands. A lying gateway sits in a one-block layer (its box) with its starfield at
     * {@link #plane} up that layer, its face toward where the player looked from.
     */
    public enum Lie {
        /** Upright, a doorway. */
        STANDING(0.0F, false),
        /** On a floor, looked down at: just above the ground, facing up. */
        FLOOR(1.0F / 16.0F, false),
        /** Under a ceiling, looked up at: just below it, facing down. */
        CEILING(15.0F / 16.0F, true),
        /** In mid-air below the player, looked down at: facing up. */
        AIR_BELOW(0.5F, false),
        /** In mid-air above the player, looked up at: facing down. */
        AIR_ABOVE(0.5F, true);

        /** How far up its layer the starfield lies (lying gateways only). */
        public final float plane;
        /** Its face looks down (seen from below). */
        public final boolean facesDown;

        Lie(float plane, boolean facesDown) {
            this.plane = plane;
            this.facesDown = facesDown;
        }

        public boolean flat() {
            return this != STANDING;
        }

        static Lie of(int ordinal) {
            Lie[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : STANDING;
        }
    }

    @Nullable
    private UUID owner;

    public PhantomGatewayEntity(EntityType<? extends PhantomGatewayEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Server-side, before it's added: who opened it, which way it faces, how it lies, its size, and for how long. */
    public void open(UUID owner, float yaw, Lie lie, int width, int height, int lifetime) {
        this.owner = owner;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.entityData.set(DATA_LIE, lie.ordinal());
        this.entityData.set(DATA_WIDTH, Mth.clamp(width, MIN_WIDTH, MAX_WIDTH));
        this.entityData.set(DATA_HEIGHT, Mth.clamp(height, MIN_HEIGHT, MAX_HEIGHT));
        this.entityData.set(DATA_CLOSES_AT, this.level().getGameTime() + lifetime);
    }

    /** Width in blocks. */
    public int doorWidth() {
        return this.entityData.get(DATA_WIDTH);
    }

    /** Height in blocks; for a lying gateway, its other side (the one that runs away from the player). */
    public int doorHeight() {
        return this.entityData.get(DATA_HEIGHT);
    }

    public Lie lie() {
        return Lie.of(this.entityData.get(DATA_LIE));
    }

    /**
     * The box of a gateway at {@code at} turned to {@code yaw} (a side). Standing, {@code at} is its bottom
     * middle: {@code width} along its face, {@link #HALF_DEPTH} either way through it, {@code height} up.
     * Lying, {@code at} is the middle of the bottom of the block layer it lies in, and the box is that
     * layer: {@code width} across the way it was opened, {@code height} along it, a block thick.
     */
    public static AABB box(Vec3 at, float yaw, Lie lie, int width, int height) {
        boolean facesZ = Direction.fromYRot(yaw).getAxis() == Direction.Axis.Z;
        if (lie.flat()) {
            double dx = (facesZ ? width : height) / 2.0;
            double dz = (facesZ ? height : width) / 2.0;
            return new AABB(at.x - dx, at.y, at.z - dz, at.x + dx, at.y + 1.0, at.z + dz);
        }
        double along = width / 2.0;
        double dx = facesZ ? along : HALF_DEPTH;
        double dz = facesZ ? HALF_DEPTH : along;
        return new AABB(at.x - dx, at.y, at.z - dz, at.x + dx, at.y + height, at.z + dz);
    }

    @Override
    protected AABB makeBoundingBox() {
        return box(this.position(), this.getYRot(), this.lie(), this.doorWidth(), this.doorHeight());
    }

    /** The client learns its facing after its position, so the box follows the rotation too. */
    @Override
    public void setYRot(float yRot) {
        super.setYRot(yRot);
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key) || DATA_LIE.equals(key)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Nullable
    public UUID getOwnerId() {
        return this.owner;
    }

    public long closesAt() {
        return this.entityData.get(DATA_CLOSES_AT);
    }

    /** 0 to 1: how open it is, easing open after it appears and shut before it closes. */
    public float openness(float partialTick) {
        float age = this.tickCount + partialTick;
        float left = this.closesAt() - (this.level().getGameTime() + partialTick);
        return Mth.clamp(Math.min(age, left) / SWING_TICKS, 0.0F, 1.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CLOSES_AT, 0L);
        builder.define(DATA_WIDTH, MAX_WIDTH);
        builder.define(DATA_HEIGHT, MAX_HEIGHT);
        builder.define(DATA_LIE, Lie.STANDING.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.spawnParticles();
        } else {
            GatewayManager.tick(this);
        }
    }

    /** Client-only: motes drawn in toward the doorway, and a few drifting off its face. */
    private void spawnParticles() {
        RandomSource random = this.random;
        for (int i = 0; i < 2 + this.doorWidth() / 2; i++) {
            Vec3 at = this.randomPoint(random);
            this.level().addParticle(ParticleTypes.PORTAL, at.x, at.y, at.z,
                    (random.nextDouble() - 0.5) * 1.5, -random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 1.5);
        }
        if (random.nextInt(4) == 0) {
            Vec3 at = this.randomPoint(random);
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 0.0, 0.02, 0.0);
        }
    }

    /** A random point on its starfield. */
    private Vec3 randomPoint(RandomSource random) {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 across = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        double a = (random.nextDouble() - 0.5) * this.doorWidth();
        double b = random.nextDouble() * this.doorHeight();
        Lie lie = this.lie();
        if (lie.flat()) {
            Vec3 along = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
            b -= this.doorHeight() / 2.0;
            return new Vec3(this.getX() + across.x * a + along.x * b, this.getY() + lie.plane, this.getZ() + across.z * a + along.z * b);
        }
        return new Vec3(this.getX() + across.x * a, this.getY() + b, this.getZ() + across.z * a);
    }

    // ---- not a thing you can push, hit or keep -------------------------------------------------

    /** Never written to chunk data, like the pets. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!this.level().isClientSide()) {
            GatewayManager.forget(this);
        }
    }
}
