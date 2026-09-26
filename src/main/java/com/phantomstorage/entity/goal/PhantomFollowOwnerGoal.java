package com.phantomstorage.entity.goal;

import com.phantomstorage.entity.HoverBounds;
import com.phantomstorage.entity.PhantomChestEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Allay-style following.
 * <ul>
 *   <li>Owner travelling: trail 6-9 blocks behind; coast while inside the band, catch up beyond it.</li>
 *   <li>Owner idle: drift in to an easy-reach hover beside them, within {@link HoverBounds}.</li>
 *   <li>Beyond 12 blocks: vanilla tameable teleport-to-owner ({@code TamableAnimal#tryToTeleportToOwner}).</li>
 * </ul>
 * Per-tick work is O(1); ground scans run at most every {@link #RETARGET_TICKS} ticks.
 */
public class PhantomFollowOwnerGoal extends Goal {
    private static final double TRAIL_MIN = 6.0;
    private static final double TRAIL_MAX = 9.0;
    private static final double IDLE_RADIUS = 2.5;
    private static final double HOVER_OFFSET = 1.0;
    private static final double TRAVEL_SPEED = 1.0;
    private static final double CATCH_UP_SPEED = 1.75;
    private static final double IDLE_SPEED = 0.6;
    /** Owner counts as travelling if they moved more than 0.5 blocks in the last sample window. */
    private static final double MOVING_THRESHOLD_SQR = 0.5 * 0.5;
    private static final int SAMPLE_TICKS = 10;
    private static final int RETARGET_TICKS = 5;

    private final PhantomChestEntity chest;
    @Nullable
    private Player owner;
    @Nullable
    private Vec3 target;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private boolean ownerTravelling;
    private double targetSpeed;
    private int sampleTimer;
    private int retargetTimer;
    private int teleportTimer;

    public PhantomFollowOwnerGoal(PhantomChestEntity chest) {
        this.chest = chest;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.chest.isOrderedToSit()) {
            return false;
        }
        Player player = this.chest.getOwnerPlayer();
        if (!isFollowable(player)) {
            return false;
        }
        this.owner = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.chest.isOrderedToSit() && isFollowable(this.owner);
    }

    private boolean isFollowable(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && player.level() == this.chest.level();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.lastOwnerPos = this.owner.position();
        this.ownerTravelling = false;
        this.target = null;
        this.sampleTimer = 0;
        this.retargetTimer = 0;
        this.teleportTimer = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.target = null;
    }

    @Override
    public void tick() {
        Player player = this.owner;
        if (player == null) {
            return;
        }
        if (++this.sampleTimer >= SAMPLE_TICKS) {
            this.sampleTimer = 0;
            Vec3 now = player.position();
            this.ownerTravelling = now.distanceToSqr(this.lastOwnerPos) > MOVING_THRESHOLD_SQR;
            this.lastOwnerPos = now;
        }

        this.chest.getLookControl().setLookAt(player, 10.0F, this.chest.getMaxHeadXRot());

        if (this.chest.shouldTryTeleportToOwner() && --this.teleportTimer <= 0) {
            this.teleportTimer = this.adjustedTickDelay(10);
            this.chest.tryToTeleportToOwner();
            this.retargetTimer = 0;
        }

        if (--this.retargetTimer <= 0) {
            this.retargetTimer = RETARGET_TICKS;
            this.retarget(player);
        }
        if (this.target != null) {
            this.chest.getMoveControl().setWantedPosition(this.target.x, this.target.y, this.target.z, this.targetSpeed);
        }
    }

    private void retarget(Player player) {
        Vec3 ownerPos = player.position();
        double dist = this.chest.distanceTo(player);
        double radius;
        if (this.ownerTravelling) {
            if (dist <= TRAIL_MIN) {
                this.target = null; // inside the trailing band: coast
                return;
            }
            radius = TRAIL_MIN;
            this.targetSpeed = dist > TRAIL_MAX ? CATCH_UP_SPEED : TRAVEL_SPEED;
        } else {
            radius = IDLE_RADIUS;
            this.targetSpeed = IDLE_SPEED;
        }

        // Approach along the line from the owner to where the chest already is, so it trails
        // behind rather than orbiting to a fixed offset.
        Vec3 flat = new Vec3(this.chest.getX() - ownerPos.x, 0.0, this.chest.getZ() - ownerPos.z);
        if (flat.lengthSqr() < 1.0E-4) {
            Vec3 look = player.getLookAngle();
            flat = new Vec3(-look.x, 0.0, -look.z);
            if (flat.lengthSqr() < 1.0E-4) {
                flat = new Vec3(0.0, 0.0, 1.0);
            }
        }
        Vec3 dir = flat.normalize();
        double x = ownerPos.x + dir.x * radius;
        double z = ownerPos.z + dir.z * radius;
        double y = HoverBounds.clampY(this.chest.level(), x, ownerPos.y + HOVER_OFFSET, z);
        this.target = new Vec3(x, y, z);
    }
}
