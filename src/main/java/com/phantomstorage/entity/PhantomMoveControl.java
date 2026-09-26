package com.phantomstorage.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Floaty, Allay-like steering: eases velocity toward the wanted point with an arrival slowdown,
 * and drifts to a stop when nothing asks it to move. Straight-line (the chest phases through
 * blocks), so there is no pathfinding cost at all.
 */
public class PhantomMoveControl extends MoveControl {
    private static final double STEERING = 0.18;
    private static final double ARRIVAL = 0.12;
    private static final double DRIFT_DAMPING = 0.85;

    public PhantomMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().scale(DRIFT_DAMPING));
            return;
        }
        this.operation = Operation.WAIT;

        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double maxSpeed = this.mob.getAttributeValue(Attributes.FLYING_SPEED) * this.speedModifier;

        Vec3 desired = dist < 1.0E-4
                ? Vec3.ZERO
                : new Vec3(dx, dy, dz).scale(Math.min(maxSpeed, dist * ARRIVAL) / dist);
        this.mob.setDeltaMovement(this.mob.getDeltaMovement().lerp(desired, STEERING));

        if (dx * dx + dz * dz > 0.25) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 12.0F));
        }
    }
}
