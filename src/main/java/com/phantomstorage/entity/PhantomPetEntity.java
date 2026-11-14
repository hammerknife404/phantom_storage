package com.phantomstorage.entity;

import com.phantomstorage.entity.goal.PhantomFollowOwnerGoal;
import com.phantomstorage.entity.goal.PhantomStayGoal;
import com.phantomstorage.summon.AnchorManager;
import com.phantomstorage.summon.PetKind;
import com.phantomstorage.summon.PetManager;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * A summoned phantom pet: flies after its owner, phases through blocks, and is invulnerable to
 * everything but /kill. A {@linkplain #isRideable() rideable} one carries its owner, who steers it
 * where they look; ridden, it collides with blocks like any mount.
 * Deliberately holds no contents: whatever it carries lives on the owner
 * (player attachments in {@code ModRegistries}), so the entity can vanish at any moment without risk.
 * Never saved to disk ({@link #shouldBeSaved()}); {@link PetManager} keeps one of each kind per player.
 */
public abstract class PhantomPetEntity extends TamableAnimal {
    private static final int OWNER_CHECK_INTERVAL = 20;
    /** Beyond this the pet blinks back to its owner instead of flying. */
    private static final double TELEPORT_DISTANCE_SQR = 12.0 * 12.0;
    private static final double TELEPORT_RING = 2.0;
    private static final int TELEPORT_ATTEMPTS = 8;
    private static final double MENU_REACH_SQR = 64.0;
    /** Missed this many ticks: it sits in a chunk that no longer ticks (see {@link PetManager#rescueStranded}). */
    private static final long STRANDED_TICKS = 20;

    private int openCount;
    /** Set by a recall; the follow goal picks it up and settles in place instead of wandering off. */
    private boolean recalled;
    /** Set by {@link #relocate}; the stay goal picks it up and holds the new spot. */
    private boolean relocated;
    /** Game time of the last server tick. */
    private long lastTickTime;

    /**
     * Whether a rider is holding jump, which the game keeps from other classes. Set by the client to
     * read the local player's input; only the rider's own client moves its mount, so the server's
     * answer never matters.
     */
    private static Predicate<Player> riderRising = player -> false;

    protected PhantomPetEntity(EntityType<? extends PhantomPetEntity> type, Level level) {
        super(type, level);
        this.lastTickTime = level.getGameTime();
        this.moveControl = new PhantomMoveControl(this);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    public abstract PetKind kind();

    /** The owner right-clicked this pet (not sneaking, not with a name tag). Server-side only. */
    public abstract void interact(ServerPlayer owner, InteractionHand hand);

    /**
     * The owner sneak-right-clicked this pet. Return true if that was handled; otherwise it toggles
     * stay / follow. Server-side only.
     */
    public boolean sneakInteract(ServerPlayer owner, InteractionHand hand) {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PhantomStayGoal(this));
        this.goalSelector.addGoal(2, new PhantomFollowOwnerGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 6.0F));
    }

    // ---- ownership ----------------------------------------------------------------------------

    public void bindOwner(ServerPlayer owner) {
        this.setTame(true, false);
        this.setOwnerUUID(owner.getUUID());
    }

    @Nullable
    public Player getOwnerPlayer() {
        return this.getOwner() instanceof Player player ? player : null;
    }

    public void toggleStay(ServerPlayer player) {
        boolean stay = !this.isOrderedToSit();
        this.setOrderedToSit(stay);
        this.setInSittingPose(stay);
        this.navigation.stop();
        this.onStayToggled(player, stay);
        player.displayClientMessage(Component.translatable(this.kind().message(stay ? "stay" : "follow")), true);
    }

    /** Server-side, right after the owner toggled stay / follow: a pet that can anchor anchors or lets go. */
    protected void onStayToggled(ServerPlayer owner, boolean stay) {
        if (stay) {
            AnchorManager.anchor(owner, this);
        } else {
            AnchorManager.release(owner, this.kind());
        }
    }

    /**
     * Whether this pet holds its place while its owner is away, even in another dimension, instead of
     * being dismissed: one that {@linkplain PetKind#canAnchor() can anchor} and was told to stay.
     */
    public boolean isAnchored() {
        return this.kind().canAnchor() && this.isOrderedToSit();
    }

    /** Server-side, once, right after {@link #bindOwner} on summon (before the pet is added to the level). */
    public void onSummoned(ServerPlayer owner) {}

    /**
     * Server-side, instead of {@link #onSummoned}, when this pet replaces a stranded one
     * ({@link PetManager#rescueStranded}): keep its look, without summon side effects.
     */
    public void inheritFrom(PhantomPetEntity old, ServerPlayer owner) {
        this.setCustomName(old.getCustomName());
    }

    /** Whether {@code player} may keep a menu on this pet open. */
    public boolean canBeUsedBy(Player player) {
        return player.isAlive()
                && this.isAlive()
                && this.isOwnedBy(player)
                && this.level() == player.level()
                && player.distanceToSqr(this) <= MENU_REACH_SQR;
    }

    /** The custom name from a name tag, else {@code fallback}. */
    protected Component titleOr(String fallback) {
        Component custom = this.getCustomName();
        return custom != null ? custom : Component.translatable(fallback);
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @Override
    public void tick() {
        // Phases through blocks, except with a rider, who mustn't end up inside them.
        this.noPhysics = !this.isVehicle();
        super.tick();
        if (!this.level().isClientSide()) {
            this.lastTickTime = this.level().getGameTime();
            if (this.tickCount % OWNER_CHECK_INTERVAL == 0) {
                this.validateOwner();
            }
        }
    }

    /** Server-side: hasn't ticked for a second while the level has, i.e. left behind in a non-ticking chunk. */
    public boolean isStranded() {
        return this.level().getGameTime() - this.lastTickTime > STRANDED_TICKS;
    }

    /**
     * Self-healing invariant: an orphaned pet (owner offline, in another dimension, or not the
     * registered pet for that owner) removes itself. Covers every path the events might miss.
     */
    private void validateOwner() {
        UUID ownerId = this.getOwnerUUID();
        MinecraftServer server = this.level().getServer();
        ServerPlayer owner = ownerId == null || server == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner == null
                || (owner.level() != this.level() && !this.isAnchored())
                || PetManager.getActive(this.kind(), ownerId) != this) {
            this.discard();
        }
    }

    public void vanish() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 0.45, this.getZ(),
                    24, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5F, 0.8F);
        }
        this.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        // /kill is the only way to destroy a pet; an anchored one's spot goes too, or it would come back.
        UUID ownerId = this.getOwnerUUID();
        MinecraftServer server = this.level().getServer();
        if (reason == RemovalReason.KILLED && this.kind().canAnchor() && ownerId != null && server != null
                && !this.isRemoved() && server.getPlayerList().getPlayer(ownerId) instanceof ServerPlayer owner) {
            AnchorManager.onKilled(owner, this);
        }
        super.remove(reason);
        if (!this.level().isClientSide()) {
            PetManager.forget(this);
        }
    }

    /** Never written to chunk data: it cannot outlive a session or be duplicated by a world copy. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Lifetime is managed by PetManager, not the natural despawn rules.
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---- teleport -----------------------------------------------------------------------------

    public boolean isTooFarFrom(Player owner) {
        return this.distanceToSqr(owner) >= TELEPORT_DISTANCE_SQR;
    }

    /**
     * Blink to a free spot beside the owner, trying behind them first, then fanning out to the sides.
     * Unlike vanilla's tameable teleport this needs no walkable ground (the pet flies and phases
     * through blocks), so it works after big drops, over water, on ledges and in cramped caves.
     */
    public void teleportNear(Player owner) {
        this.placeNear(owner, 1.0);
    }

    /**
     * Item recall: pull the pet to ~2 blocks from the owner at their foot level, switch it back to
     * follow (a stay anchor would drag it away again) and have it settle there.
     */
    public void recallTo(Player owner) {
        if (this.isOrderedToSit()) {
            this.setOrderedToSit(false);
            this.setInSittingPose(false);
        }
        this.placeNear(owner, 0.0);
        this.recalled = true;
    }

    /**
     * Server-side, saddle: settle under the owner at {@code feet}, following again, and take them on.
     * False if they can't get on (sneaking, already riding something).
     */
    public boolean carry(ServerPlayer owner, Vec3 feet) {
        if (this.isOrderedToSit()) {
            this.setOrderedToSit(false);
            this.setInSittingPose(false);
        }
        this.blinkTo(feet.x, feet.y, feet.z);
        this.setYRot(owner.getYRot());
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        return owner.startRiding(this);
    }

    /** Consumed by the follow goal. */
    public boolean takeRecall() {
        boolean was = this.recalled;
        this.recalled = false;
        return was;
    }

    /** Server-side: blink straight to (x, y, z), e.g. to swap places with the owner. A pet told to stay stays there. */
    public void relocate(double x, double y, double z) {
        this.blinkTo(x, y, z);
        this.relocated = true;
    }

    /** Consumed by the stay goal. */
    public boolean takeRelocated() {
        boolean was = this.relocated;
        this.relocated = false;
        return was;
    }

    private void placeNear(Player owner, double heightAboveFeet) {
        Vec3 look = owner.getLookAngle();
        double behind = Mth.atan2(-look.z, -look.x);
        for (int i = 0; i < TELEPORT_ATTEMPTS; i++) {
            // 0, +45, -45, +90, -90, +135, -135, 180 degrees from "behind"
            double angle = behind + ((i + 1) / 2) * (i % 2 == 0 ? -1 : 1) * (Math.PI / 4.0);
            double x = owner.getX() + Math.cos(angle) * TELEPORT_RING;
            double z = owner.getZ() + Math.sin(angle) * TELEPORT_RING;
            double y = HoverBounds.clampY(this.level(), x, owner.getY() + heightAboveFeet, z);
            if (this.level().noCollision(this, this.getBoundingBox().move(x - this.getX(), y - this.getY(), z - this.getZ()))) {
                this.blinkTo(x, y, z);
                return;
            }
        }
        // Nowhere free (tight tunnel): share the owner's space; it floats out of blocks on its own.
        this.blinkTo(owner.getX(), owner.getY(), owner.getZ());
    }

    private void blinkTo(double x, double y, double z) {
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
        this.setDeltaMovement(Vec3.ZERO);
        this.navigation.stop();
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.45, z, 12, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ---- menu ---------------------------------------------------------------------------------

    public void onMenuOpened() {
        if (this.openCount++ == 0) {
            this.onFirstViewer();
        }
    }

    public void onMenuClosed() {
        if (this.openCount > 0 && --this.openCount == 0) {
            this.onLastViewerLeft();
        }
    }

    protected void onFirstViewer() {}

    protected void onLastViewerLeft() {}

    /** Server-side: the owner currently has this pet's menu open. */
    public boolean isBeingViewed() {
        return this.openCount > 0;
    }

    // ---- movement: free flight, phases through blocks and entities ---------------------------

    @Override
    public void travel(Vec3 travelVector) {
        if (!this.isControlledByLocalInstance()) {
            return;
        }
        if (this.getControllingPassenger() instanceof Player rider) {
            this.flyRidden(rider, travelVector);
        } else {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    // ---- riding: the owner steers ------------------------------------------------------------

    /**
     * Ridden: blocks per tick, a little under a player's own walk (0.216). No sprint: the chest is for getting
     * round rough ground and building on cliff faces, not long trips or racing.
     */
    private static final double RIDE_SPEED = 0.2;
    /** Backing up goes at this fraction of the speed. */
    private static final double RIDE_BACK = 0.5;
    /** How quickly it takes up a new heading, and how quickly it stops with no keys held. */
    private static final double RIDE_ACCEL = 0.2;
    private static final double RIDE_BRAKE = 0.25;
    /** The rider's head must clear blocks by this much, so they never take suffocation damage. */
    private static final double RIDE_HEAD_ROOM = 0.05;
    /**
     * Two scalings of the game's that ridden flight undoes, so the speeds above are what the rider gets: a
     * held key reads 0.98, and on the rider's client the game slows the mount's velocity by 0.98 each tick
     * before it moves ({@code LivingEntity.aiStep}). Left in, they settled it about 9% short.
     */
    private static final double HELD_KEY = 0.98;
    private static final double CLIENT_SLOWING = 0.98;

    /** Called by the client at startup: whether {@code player} is holding jump. */
    public static void setRiderRising(Predicate<Player> rising) {
        riderRising = rising;
    }

    /** Whether its owner can ride it (the chest, with the charm's saddle mode). */
    public boolean isRideable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.isRideable() && this.getPassengers().isEmpty()
                && passenger instanceof Player player && this.isOwnedBy(player);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player && this.isOwnedBy(player) ? player : null;
    }

    /** No mount hearts in the rider's HUD (it can't be hurt), so their food bar stays. */
    @Override
    public boolean showVehicleHealth() {
        return false;
    }

    /** (strafe, rise, forward) as the rider asks: WASD and jump, a held key as 1. */
    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 travelVector) {
        return new Vec3(rider.xxa / HELD_KEY, riderRising.test(rider) ? 1.0 : 0.0, rider.zza / HELD_KEY);
    }

    /** Faces where the rider faces. Both sides, so the server's copy turns too. */
    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        this.setYRot(rider.getYRot());
        this.yRotO = this.getYRot();
        this.setXRot(0.0F);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    /**
     * Rider's client only: fly where they look. Forward and back follow the view, pitch included; strafe
     * stays level; jump rises. Velocity eases toward that and brakes to a hover with no keys held.
     * Collides with blocks, and also keeps the rider's head out of them.
     */
    private void flyRidden(Player rider, Vec3 input) {
        float yaw = rider.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        double forward = input.z < 0.0 ? input.z * RIDE_BACK : input.z;
        Vec3 wish = rider.getViewVector(1.0F).scale(forward).add(left.scale(input.x)).add(0.0, input.y, 0.0);
        if (wish.lengthSqr() > 1.0) {
            wish = wish.normalize();
        }
        boolean steering = wish.lengthSqr() > 1.0E-4;
        Vec3 motion = this.getDeltaMovement().scale(1.0 / CLIENT_SLOWING)
                .lerp(wish.scale(RIDE_SPEED), steering ? RIDE_ACCEL : RIDE_BRAKE);
        if (!steering && motion.lengthSqr() < 1.0E-5) {
            motion = Vec3.ZERO;
        }
        motion = this.clearRiderHead(rider, motion);
        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        this.calculateEntityAnimation(true);
    }

    /**
     * {@code motion}, cut short where the rider's head would enter a block. Their body overlaps this
     * pet's own box, so only the part above it (up to just over their eyes, where vanilla checks for
     * suffocation) is tested; a two-block tunnel still fits.
     */
    private Vec3 clearRiderHead(Player rider, Vec3 motion) {
        AABB own = this.getBoundingBox();
        double top = rider.getEyeY() + RIDE_HEAD_ROOM;
        if (top <= own.maxY) {
            return motion;
        }
        double half = rider.getBbWidth() * 0.4;
        AABB head = new AABB(rider.getX() - half, own.maxY, rider.getZ() - half, rider.getX() + half, top, rider.getZ() + half);
        return Entity.collideBoundingBox(rider, motion, head, this.level(), List.of());
    }

    /** Off where it hovers, not on top of its lid, when there's room: a shorter drop. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 here = this.position();
        AABB box = passenger.getDimensions(Pose.STANDING).makeBoundingBox(here);
        return this.level().noCollision(passenger, box) ? here : super.getDismountLocationForPassenger(passenger);
    }

    /** It flies; no fall damage, for it or (vanilla passes it on) its rider. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        this.resetFallDistance();
    }

    /** Never through a portal, ridden or not: a pet never changes dimension; it's re-summoned instead. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            this.spawnAmbientParticles();
        }
    }

    /** Client-only, so it costs the server nothing. */
    private void spawnAmbientParticles() {
        RandomSource random = this.random;
        if (random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.PORTAL,
                    this.getRandomX(0.6), this.getRandomY() - 0.25, this.getRandomZ(0.6),
                    (random.nextDouble() - 0.5) * 0.5, -random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.5);
        }
        if (random.nextInt(12) == 0) {
            this.level().addParticle(ParticleTypes.SOUL,
                    this.getRandomX(0.5), this.getY() + random.nextDouble() * 0.4, this.getRandomZ(0.5),
                    0.0, 0.015, 0.0);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // No entity collision; also skips the per-tick AABB query.
    }

    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    // ---- durability: only /kill ---------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypes.GENERIC_KILL);
    }

    @Override
    @SuppressWarnings("deprecation") // NeoForge deprecates calling it; overriding is the intended use
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    // ---- TamableAnimal/Animal obligations: no breeding, food, or offspring --------------------

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal otherAnimal) {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return false;
    }
}
