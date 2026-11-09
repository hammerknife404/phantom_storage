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
 * travel itself is {@link GatewayManager}'s. It stands square to the block grid, facing the way it
 * was opened ({@link #getYRot()}), {@link #HEIGHT} blocks tall and {@link #doorWidth()} wide (3, or 2
 * where 3 doesn't fit); its box is the doorway itself, a block deep, not a square round it. It holds
 * nothing, and like the pets is never saved, so it can't outlive the session.
 */
public class PhantomGatewayEntity extends Entity {
    /** Game time it closes at, so the client can shrink it shut on time. */
    private static final EntityDataAccessor<Long> DATA_CLOSES_AT =
            SynchedEntityData.defineId(PhantomGatewayEntity.class, EntityDataSerializers.LONG);
    /** Width in blocks, synced so the client's box and look match the server's. */
    private static final EntityDataAccessor<Integer> DATA_WIDTH =
            SynchedEntityData.defineId(PhantomGatewayEntity.class, EntityDataSerializers.INT);
    /** Ticks it takes to open, and to close. */
    public static final int SWING_TICKS = 10;
    public static final int MAX_WIDTH = 3;
    public static final int MIN_WIDTH = 2;
    public static final double HEIGHT = 3.0;
    /** Half its box's depth: whatever comes this close to the doorway's face goes through. */
    public static final double HALF_DEPTH = 0.5;

    @Nullable
    private UUID owner;

    public PhantomGatewayEntity(EntityType<? extends PhantomGatewayEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Server-side, before it's added: who opened it, which way it faces, how wide, and for how long. */
    public void open(UUID owner, float yaw, int width, int lifetime) {
        this.owner = owner;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.entityData.set(DATA_WIDTH, Mth.clamp(width, MIN_WIDTH, MAX_WIDTH));
        this.entityData.set(DATA_CLOSES_AT, this.level().getGameTime() + lifetime);
    }

    /** Width in blocks. */
    public int doorWidth() {
        return this.entityData.get(DATA_WIDTH);
    }

    /**
     * The box of a doorway standing at {@code at} (its bottom middle) facing {@code yaw} (a side): {@code width}
     * along its face, {@link #HALF_DEPTH} either way through it, {@link #HEIGHT} up.
     */
    public static AABB box(Vec3 at, float yaw, int width) {
        boolean facesZ = Direction.fromYRot(yaw).getAxis() == Direction.Axis.Z;
        double along = width / 2.0;
        double dx = facesZ ? along : HALF_DEPTH;
        double dz = facesZ ? HALF_DEPTH : along;
        return new AABB(at.x - dx, at.y, at.z - dz, at.x + dx, at.y + HEIGHT, at.z + dz);
    }

    @Override
    protected AABB makeBoundingBox() {
        return box(this.position(), this.getYRot(), this.doorWidth());
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
        if (DATA_WIDTH.equals(key)) {
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
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 across = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        for (int i = 0; i < 2 + this.doorWidth() / 2; i++) {
            double a = (random.nextDouble() - 0.5) * this.doorWidth();
            double y = this.getY() + random.nextDouble() * HEIGHT;
            this.level().addParticle(ParticleTypes.PORTAL,
                    this.getX() + across.x * a, y, this.getZ() + across.z * a,
                    (random.nextDouble() - 0.5) * 1.5, -random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 1.5);
        }
        if (random.nextInt(4) == 0) {
            double a = (random.nextDouble() - 0.5) * this.doorWidth();
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL,
                    this.getX() + across.x * a, this.getY() + random.nextDouble() * HEIGHT, this.getZ() + across.z * a,
                    0.0, 0.02, 0.0);
        }
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
