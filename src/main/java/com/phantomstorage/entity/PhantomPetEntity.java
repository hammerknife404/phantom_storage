package com.phantomstorage.entity;

import com.phantomstorage.entity.goal.PhantomFollowOwnerGoal;
import com.phantomstorage.entity.goal.PhantomStayGoal;
import com.phantomstorage.summon.PetKind;
import com.phantomstorage.summon.PetManager;
import java.util.UUID;
import javax.annotation.Nullable;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * A summoned phantom pet: flies after its owner, phases through blocks, and is invulnerable to
 * everything but /kill. Deliberately holds no contents: whatever it carries lives on the owner
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

    private int openCount;
    /** Set by a recall; the follow goal picks it up and settles in place instead of wandering off. */
    private boolean recalled;

    protected PhantomPetEntity(EntityType<? extends PhantomPetEntity> type, Level level) {
        super(type, level);
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
        player.displayClientMessage(Component.translatable(this.kind().message(stay ? "stay" : "follow")), true);
    }

    /** Server-side, once, right after {@link #bindOwner} on summon (before the pet is added to the level). */
    public void onSummoned(ServerPlayer owner) {}

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
        this.noPhysics = true;
        super.tick();
        if (!this.level().isClientSide() && this.tickCount % OWNER_CHECK_INTERVAL == 0) {
            this.validateOwner();
        }
    }

    /**
     * Self-healing invariant: an orphaned pet (owner offline, in another dimension, or not the
     * registered pet for that owner) removes itself. Covers every path the events might miss.
     */
    private void validateOwner() {
        UUID ownerId = this.getOwnerUUID();
        MinecraftServer server = this.level().getServer();
        ServerPlayer owner = ownerId == null || server == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.level() != this.level() || PetManager.getActive(this.kind(), ownerId) != this) {
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

    /** Consumed by the follow goal. */
    public boolean takeRecall() {
        boolean was = this.recalled;
        this.recalled = false;
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
        if (this.isControlledByLocalInstance()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
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
