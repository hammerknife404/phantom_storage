package com.phantomstorage.entity.goal;

import com.phantomstorage.entity.HoverBounds;
import com.phantomstorage.entity.PhantomChestEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Allay-style following, as a small set of phases:
 * <pre>
 * owner moving  -> TRAVEL : trail 6-9 blocks behind, swaying and bobbing aimlessly
 * owner stops   -> WANDER : drift 2-5 blocks out, 2-5 blocks above the owner's feet
 * still ~15 s   -> SETTLE : hover ~2 blocks beside the owner at their foot level, facing them
 * any idle time -> CALL   : owner looks at it ~0.5 s -> comes within reach, lingers
 * menu open     -> hold still
 * </pre>
 * Every target passes through {@link HoverBounds}, so the ground rules always win. Beyond 12 blocks,
 * vanilla tameable teleport-to-owner ({@code TamableAnimal#tryToTeleportToOwner}) kicks in.
 * Per-tick work is O(1); ground scans and look checks run at most every {@link #RETARGET_TICKS} ticks.
 */
public class PhantomFollowOwnerGoal extends Goal {
    // TRAVEL
    private static final double TRAIL_MIN = 6.0;
    private static final double TRAIL_MAX = 9.0;
    private static final double TRAVEL_SPEED = 1.0;
    private static final double CATCH_UP_SPEED = 1.75;
    private static final double DRIFT_SPEED = 0.4;
    /** Aimless travel motion: sideways sway and vertical bob around the trailing point. */
    private static final double SWAY = 1.0;
    private static final double BOB_LOW = -0.5;
    private static final double BOB_HIGH = 1.5;
    private static final int SWAY_MIN_TICKS = 30;
    private static final int SWAY_EXTRA_TICKS = 40;

    // WANDER
    private static final double WANDER_MIN = 2.0;
    private static final double WANDER_MAX = 5.0;
    private static final double WANDER_HEIGHT_MIN = 2.0;
    private static final double WANDER_HEIGHT_MAX = 5.0;
    private static final double WANDER_SPEED = 0.35;
    private static final int WANDER_MIN_TICKS = 40;
    private static final int WANDER_EXTRA_TICKS = 60;

    // SETTLE
    private static final int SETTLE_AFTER_TICKS = 15 * 20;
    /** Beside the owner, within vanilla's 3-block entity reach. */
    private static final double SETTLE_DISTANCE = 2.0;
    private static final double SETTLE_SPEED = 0.35;

    // CALL
    private static final double CALL_DISTANCE = 2.0;
    private static final double CALL_SPEED = 0.8;
    private static final int CALL_LOOK_TICKS = 10;
    private static final int CALL_LINGER_TICKS = 60;
    private static final double CALL_MAX_DISTANCE_SQR = 12.0 * 12.0;
    /** cos(~10 degrees): how directly the owner must be looking at the chest. */
    private static final double LOOK_DOT = 0.985;

    /** Chest base height above the owner's feet while travelling / when called. */
    private static final double HOVER_OFFSET = 1.0;
    /** Owner counts as travelling if they moved more than 0.5 blocks in the last sample window. */
    private static final double MOVING_THRESHOLD_SQR = 0.5 * 0.5;
    private static final int SAMPLE_TICKS = 10;
    private static final int RETARGET_TICKS = 5;

    private final PhantomChestEntity chest;
    @Nullable
    private Player owner;
    @Nullable
    private Vec3 target;
    /** Current idle destination: a wander point, the settle spot, or the call point. */
    @Nullable
    private Vec3 idleTarget;
    private boolean settled;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private boolean ownerTravelling;
    private double targetSpeed;
    private double sway;
    private double bob;
    /** Owner->chest direction captured when the sway was rolled; keeps the sway anchored to the owner. */
    private Vec3 swayDir = new Vec3(0.0, 0.0, 1.0);
    private int idleTicks;
    private int sampleTimer;
    private int retargetTimer;
    private int teleportTimer;
    private int idleTargetTimer;
    private int swayTimer;
    private int lookTicks;

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
        this.resetIdle();
        this.sampleTimer = 0;
        this.retargetTimer = 0;
        this.teleportTimer = 0;
        this.swayTimer = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.target = null;
        this.idleTarget = null;
    }

    private void resetIdle() {
        this.idleTicks = 0;
        this.idleTarget = null;
        this.settled = false;
        this.lookTicks = 0;
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
            if (this.ownerTravelling) {
                this.resetIdle();
            } else {
                this.idleTicks += SAMPLE_TICKS;
            }
        }

        this.chest.getLookControl().setLookAt(player, 10.0F, this.chest.getMaxHeadXRot());

        if (this.chest.shouldTryTeleportToOwner() && --this.teleportTimer <= 0) {
            this.teleportTimer = this.adjustedTickDelay(10);
            this.chest.tryToTeleportToOwner();
            this.idleTarget = null;
            this.settled = false;
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
        if (this.chest.isBeingViewed()) {
            // Don't drift away from an open GUI; re-settle from wherever it is once closed.
            this.target = null;
            this.idleTarget = null;
            this.settled = false;
            return;
        }
        if (this.ownerTravelling) {
            this.retargetTravel(player);
            return;
        }

        // CALL: looking at the chest brings it within reach, whatever the idle phase.
        this.lookTicks = this.isLookedAtBy(player) ? this.lookTicks + RETARGET_TICKS : 0;
        if (this.lookTicks >= CALL_LOOK_TICKS) {
            this.idleTarget = this.pointBeside(player, player.getLookAngle(), CALL_DISTANCE, player.getY() + HOVER_OFFSET);
            this.idleTargetTimer = CALL_LINGER_TICKS;
            this.settled = false;
            this.aim(this.idleTarget, CALL_SPEED);
            return;
        }
        this.idleTargetTimer -= RETARGET_TICKS;

        // SETTLE: after a while, rest beside the owner at their foot level and stay there.
        if (this.idleTicks >= SETTLE_AFTER_TICKS) {
            if (!this.settled && this.idleTargetTimer <= 0) {
                Vec3 fromOwner = this.chest.position().subtract(player.position());
                this.idleTarget = this.pointBeside(player, fromOwner, SETTLE_DISTANCE, player.getY());
                this.settled = true;
            }
            if (this.settled) {
                this.aim(this.idleTarget, SETTLE_SPEED);
                return;
            }
        }

        // WANDER: drift around and above the owner.
        Vec3 ownerPos = player.position();
        if (this.idleTarget == null
                || this.idleTargetTimer <= 0
                || horizontalDistSqr(this.idleTarget, ownerPos) > (WANDER_MAX + 0.5) * (WANDER_MAX + 0.5)) {
            this.idleTarget = this.pickWanderTarget(ownerPos);
        }
        this.aim(this.idleTarget, WANDER_SPEED);
    }

    /** TRAVEL: trail behind; inside the band, drift. Either way, sway and bob aimlessly. */
    private void retargetTravel(Player player) {
        RandomSource random = this.chest.getRandom();
        Vec3 ownerPos = player.position();
        Vec3 dir = this.flatDirection(this.chest.getX() - ownerPos.x, this.chest.getZ() - ownerPos.z, player);
        this.swayTimer -= RETARGET_TICKS;
        if (this.swayTimer <= 0) {
            this.swayTimer = SWAY_MIN_TICKS + random.nextInt(SWAY_EXTRA_TICKS + 1);
            this.sway = (random.nextDouble() * 2.0 - 1.0) * SWAY;
            this.bob = BOB_LOW + random.nextDouble() * (BOB_HIGH - BOB_LOW);
            this.swayDir = dir;
        }

        double dist = this.chest.distanceTo(player);
        double along;
        if (dist > TRAIL_MIN) {
            along = TRAIL_MIN; // catch up to the trailing ring
            this.swayDir = dir;
            this.targetSpeed = dist > TRAIL_MAX ? CATCH_UP_SPEED : TRAVEL_SPEED;
        } else {
            along = Math.max(dist, 3.0); // inside the band: drift around where it is, relative to the owner
            this.targetSpeed = DRIFT_SPEED;
        }
        // Base point and sideways sway are both measured in the owner's frame, so the chest can't
        // run away sideways: the target moves with the owner, not with the chest.
        Vec3 d = this.swayDir;
        this.target = this.hoverPoint(
                ownerPos.x + d.x * along - d.z * this.sway,
                ownerPos.y + HOVER_OFFSET + this.bob,
                ownerPos.z + d.z * along + d.x * this.sway);
    }

    /** Next drift point: a gentle arc (at most +-90 degrees) around the owner, 2-5 blocks above their feet. */
    private Vec3 pickWanderTarget(Vec3 ownerPos) {
        RandomSource random = this.chest.getRandom();
        double current = Mth.atan2(this.chest.getZ() - ownerPos.z, this.chest.getX() - ownerPos.x);
        double angle = current + (random.nextDouble() - 0.5) * Math.PI;
        double radius = WANDER_MIN + random.nextDouble() * (WANDER_MAX - WANDER_MIN);
        double height = WANDER_HEIGHT_MIN + random.nextDouble() * (WANDER_HEIGHT_MAX - WANDER_HEIGHT_MIN);
        this.idleTargetTimer = WANDER_MIN_TICKS + random.nextInt(WANDER_EXTRA_TICKS + 1);
        return this.hoverPoint(
                ownerPos.x + Math.cos(angle) * radius,
                ownerPos.y + height,
                ownerPos.z + Math.sin(angle) * radius);
    }

    private void aim(Vec3 point, double speed) {
        this.target = point;
        this.targetSpeed = speed;
    }

    /** A point {@code distance} out from the owner along {@code direction} (flattened), at height {@code y}. */
    private Vec3 pointBeside(Player player, Vec3 direction, double distance, double y) {
        Vec3 dir = this.flatDirection(direction.x, direction.z, player);
        return this.hoverPoint(player.getX() + dir.x * distance, y, player.getZ() + dir.z * distance);
    }

    private boolean isLookedAtBy(Player player) {
        Vec3 toChest = this.chest.getBoundingBox().getCenter().subtract(player.getEyePosition());
        double distSqr = toChest.lengthSqr();
        if (distSqr < 1.0E-4 || distSqr > CALL_MAX_DISTANCE_SQR) {
            return false;
        }
        return player.getViewVector(1.0F).dot(toChest.normalize()) > LOOK_DOT;
    }

    private Vec3 hoverPoint(double x, double y, double z) {
        return new Vec3(x, HoverBounds.clampY(this.chest.level(), x, y, z), z);
    }

    /** Normalised horizontal direction, falling back to "behind the owner" when degenerate. */
    private Vec3 flatDirection(double dx, double dz, Player player) {
        Vec3 flat = new Vec3(dx, 0.0, dz);
        if (flat.lengthSqr() < 1.0E-4) {
            Vec3 look = player.getLookAngle();
            flat = new Vec3(-look.x, 0.0, -look.z);
            if (flat.lengthSqr() < 1.0E-4) {
                flat = new Vec3(0.0, 0.0, 1.0);
            }
        }
        return flat.normalize();
    }

    private static double horizontalDistSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
