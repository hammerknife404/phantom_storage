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
 * Allay-style following.
 * <ul>
 *   <li>Owner travelling: trail 6-9 blocks behind; coast while inside the band, catch up beyond it.</li>
 *   <li>Owner idle: drift slowly around them, 2-5 blocks out, within {@link HoverBounds}.</li>
 *   <li>Owner looks at the chest for ~half a second: it comes within reach and lingers.</li>
 *   <li>Menu open: holds still.</li>
 *   <li>Beyond 12 blocks: vanilla tameable teleport-to-owner ({@code TamableAnimal#tryToTeleportToOwner}).</li>
 * </ul>
 * Per-tick work is O(1); ground scans and look checks run at most every {@link #RETARGET_TICKS} ticks.
 */
public class PhantomFollowOwnerGoal extends Goal {
    private static final double TRAIL_MIN = 6.0;
    private static final double TRAIL_MAX = 9.0;
    private static final double TRAVEL_SPEED = 1.0;
    private static final double CATCH_UP_SPEED = 1.75;

    private static final double WANDER_MIN = 2.0;
    private static final double WANDER_MAX = 5.0;
    private static final double WANDER_SPEED = 0.35;
    private static final int WANDER_MIN_TICKS = 40;
    private static final int WANDER_EXTRA_TICKS = 60;

    /** Within vanilla's 3-block entity reach. */
    private static final double CALL_DISTANCE = 2.0;
    private static final double CALL_SPEED = 0.8;
    private static final int CALL_LOOK_TICKS = 10;
    private static final int CALL_LINGER_TICKS = 60;
    private static final double CALL_MAX_DISTANCE_SQR = 12.0 * 12.0;
    /** cos(~10 degrees): how directly the owner must be looking at the chest. */
    private static final double LOOK_DOT = 0.985;

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
    @Nullable
    private Vec3 wanderTarget;
    private Vec3 lastOwnerPos = Vec3.ZERO;
    private boolean ownerTravelling;
    private double targetSpeed;
    private int sampleTimer;
    private int retargetTimer;
    private int teleportTimer;
    private int wanderTimer;
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
        this.wanderTarget = null;
        this.sampleTimer = 0;
        this.retargetTimer = 0;
        this.teleportTimer = 0;
        this.lookTicks = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.target = null;
        this.wanderTarget = null;
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
            this.wanderTarget = null;
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
            // Don't drift away from an open GUI.
            this.target = null;
            this.wanderTarget = null;
            return;
        }
        if (this.ownerTravelling) {
            this.lookTicks = 0;
            this.wanderTarget = null;
            this.retargetTrailing(player);
            return;
        }

        this.lookTicks = this.isLookedAtBy(player) ? this.lookTicks + RETARGET_TICKS : 0;
        if (this.lookTicks >= CALL_LOOK_TICKS) {
            // Come within reach and linger there for a moment after the owner looks away.
            this.wanderTarget = this.pointInFrontOf(player, CALL_DISTANCE);
            this.wanderTimer = CALL_LINGER_TICKS;
            this.target = this.wanderTarget;
            this.targetSpeed = CALL_SPEED;
            return;
        }

        this.wanderTimer -= RETARGET_TICKS;
        Vec3 ownerPos = player.position();
        if (this.wanderTarget == null
                || this.wanderTimer <= 0
                || horizontalDistSqr(this.wanderTarget, ownerPos) > (WANDER_MAX + 0.5) * (WANDER_MAX + 0.5)) {
            this.wanderTarget = this.pickWanderTarget(ownerPos);
        }
        this.target = this.wanderTarget;
        this.targetSpeed = WANDER_SPEED;
    }

    private void retargetTrailing(Player player) {
        double dist = this.chest.distanceTo(player);
        if (dist <= TRAIL_MIN) {
            this.target = null; // inside the trailing band: coast
            return;
        }
        this.targetSpeed = dist > TRAIL_MAX ? CATCH_UP_SPEED : TRAVEL_SPEED;
        // Approach along the owner->chest line so it trails behind rather than orbiting a fixed offset.
        Vec3 ownerPos = player.position();
        Vec3 dir = this.flatDirection(this.chest.getX() - ownerPos.x, this.chest.getZ() - ownerPos.z, player);
        this.target = this.hoverPoint(ownerPos.x + dir.x * TRAIL_MIN, ownerPos.y + HOVER_OFFSET, ownerPos.z + dir.z * TRAIL_MIN);
    }

    /** Next drift point: a gentle arc (at most +-90 degrees) around the owner from where the chest is now. */
    private Vec3 pickWanderTarget(Vec3 ownerPos) {
        RandomSource random = this.chest.getRandom();
        double current = Mth.atan2(this.chest.getZ() - ownerPos.z, this.chest.getX() - ownerPos.x);
        double angle = current + (random.nextDouble() - 0.5) * Math.PI;
        double radius = WANDER_MIN + random.nextDouble() * (WANDER_MAX - WANDER_MIN);
        double lift = random.nextDouble() * 1.0 - 0.3;
        this.wanderTimer = WANDER_MIN_TICKS + random.nextInt(WANDER_EXTRA_TICKS + 1);
        return this.hoverPoint(
                ownerPos.x + Math.cos(angle) * radius,
                ownerPos.y + HOVER_OFFSET + lift,
                ownerPos.z + Math.sin(angle) * radius);
    }

    private Vec3 pointInFrontOf(Player player, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = this.flatDirection(look.x, look.z, player);
        return this.hoverPoint(player.getX() + dir.x * distance, player.getY() + HOVER_OFFSET, player.getZ() + dir.z * distance);
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
