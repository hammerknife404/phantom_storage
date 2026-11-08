package com.phantomstorage.entity;

import com.phantomstorage.summon.GatewayManager;
import java.util.UUID;
import javax.annotation.Nullable;
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
import net.minecraft.world.phys.Vec3;

/**
 * A Phantom Gateway: a short-lived doorway, opened with the charm, onto its owner's anchored Phantom
 * Anchor. Whatever steps into it comes out beside the anchor, in whichever dimension that is; the
 * travel itself is {@link GatewayManager}'s. It faces the way it was opened ({@link #getYRot()}),
 * holds nothing, and like the pets is never saved, so it can't outlive the session.
 */
public class PhantomGatewayEntity extends Entity {
    /** Game time it closes at, so the client can shrink it shut on time. */
    private static final EntityDataAccessor<Long> DATA_CLOSES_AT =
            SynchedEntityData.defineId(PhantomGatewayEntity.class, EntityDataSerializers.LONG);
    /** Ticks it takes to open, and to close. */
    public static final int SWING_TICKS = 10;

    @Nullable
    private UUID owner;

    public PhantomGatewayEntity(EntityType<? extends PhantomGatewayEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Server-side, before it's added: who opened it, and for how long. */
    public void open(UUID owner, float yaw, int lifetime) {
        this.owner = owner;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.entityData.set(DATA_CLOSES_AT, this.level().getGameTime() + lifetime);
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
        for (int i = 0; i < 2; i++) {
            double a = (random.nextDouble() - 0.5) * this.getBbWidth();
            double y = this.getY() + random.nextDouble() * this.getBbHeight();
            this.level().addParticle(ParticleTypes.PORTAL,
                    this.getX() + across.x * a, y, this.getZ() + across.z * a,
                    (random.nextDouble() - 0.5) * 1.5, -random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 1.5);
        }
        if (random.nextInt(4) == 0) {
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL,
                    this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.02, 0.0);
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
