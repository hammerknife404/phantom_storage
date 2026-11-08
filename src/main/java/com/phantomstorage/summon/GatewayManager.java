package com.phantomstorage.summon;

import com.phantomstorage.entity.PhantomGatewayEntity;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.item.PhantomCharmItem;
import com.phantomstorage.registry.ModRegistries;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The charm's gateway mode: a doorway onto the owner's anchored Phantom Anchor ({@link PhantomGatewayEntity}).
 * It opens a couple of blocks in front of them and stays open for {@link #LIFETIME} ticks; anything
 * that steps in (the owner, other players, mobs, items) comes out on the nearest safe spot beside
 * the anchor, across dimensions if need be. Pets don't go through: they follow their owners anyway,
 * and an owner's following pets are brought along when they cross to another dimension. One gateway
 * per player; opening another closes the first. All on the server thread.
 */
public final class GatewayManager {
    public static final int LIFETIME = 15 * 20;
    /** How far in front of the player it opens: the first of these with room for it. */
    private static final double[] REACH = {2.0, 1.5, 2.5};
    /** At most this many arrivals a tick, however many items a farm drops into it. */
    private static final int MAX_PER_TICK = 8;
    /** Couldn't land: try that one again after this long, not every tick. */
    private static final int RETRY_TICKS = 40;

    private static final Map<UUID, PhantomGatewayEntity> OPEN = new HashMap<>();

    private GatewayManager() {}

    @Nullable
    private static PhantomGatewayEntity getOpen(UUID owner) {
        PhantomGatewayEntity gate = OPEN.get(owner);
        if (gate != null && gate.isRemoved()) {
            OPEN.remove(owner);
            return null;
        }
        return gate;
    }

    /**
     * Charm use in gateway mode: open one (closing any other), or, sneaking, close it.
     *
     * @return the charm cooldown in ticks, or {@link PhantomCharmItem#FAILED}.
     */
    public static int use(ServerPlayer player) {
        UUID id = player.getUUID();
        if (player.isShiftKeyDown()) {
            PhantomGatewayEntity open = getOpen(id);
            if (open == null) {
                return PhantomCharmItem.FAILED;
            }
            close(open);
            player.displayClientMessage(Component.translatable("message.phantomstorage.gateway.closed"), true);
            return PhantomCharmItem.SHORT_COOLDOWN_TICKS;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return PhantomCharmItem.FAILED;
        }
        if (!AnchorManager.isAnchored(player, PetKind.ANCHOR)) {
            player.displayClientMessage(Component.translatable("message.phantomstorage.gateway.no_anchor"), true);
            return PhantomCharmItem.FAILED;
        }
        if (PetManager.getActive(PetKind.ANCHOR, id) == null) {
            player.displayClientMessage(Component.translatable("message.phantomstorage.anchor.loading"), true);
            return PhantomCharmItem.FAILED;
        }
        ServerLevel level = player.serverLevel();
        PhantomGatewayEntity gate = ModRegistries.PHANTOM_GATEWAY.get().create(level);
        Vec3 at = gate == null ? null : doorway(player, gate);
        if (at == null) {
            player.displayClientMessage(Component.translatable("message.phantomstorage.gateway.no_room"), true);
            return PhantomCharmItem.FAILED;
        }
        PhantomGatewayEntity old = getOpen(id);
        if (old != null) {
            close(old);
        }
        float yaw = player.getYRot() + 180.0F;
        gate.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        gate.open(id, yaw, LIFETIME);
        if (!level.addFreshEntity(gate)) {
            return PhantomCharmItem.FAILED;
        }
        OPEN.put(id, gate);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 40, 0.3, 0.8, 0.3, 0.05);
        level.playSound(null, at.x, at.y + 1.0, at.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.phantomstorage.gateway.opened"), true);
        return PhantomCharmItem.COOLDOWN_TICKS;
    }

    /** Where it opens: straight ahead of the player at their feet, wherever it fits clear of blocks. */
    @Nullable
    private static Vec3 doorway(ServerPlayer player, PhantomGatewayEntity gate) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        flat = flat.normalize();
        for (double reach : REACH) {
            Vec3 at = player.position().add(flat.scale(reach));
            AABB box = gate.getType().getDimensions().makeBoundingBox(at);
            if (player.level().noCollision(box) && player.level().getWorldBorder().isWithinBounds(box)) {
                return at;
            }
        }
        return null;
    }

    /** Closes it with a flourish. */
    public static void close(PhantomGatewayEntity gate) {
        if (gate.level() instanceof ServerLevel level && !gate.isRemoved()) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, gate.getX(), gate.getY() + 1.0, gate.getZ(), 24, 0.3, 0.8, 0.3, 0.02);
            level.playSound(null, gate.getX(), gate.getY() + 1.0, gate.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        gate.discard();
    }

    /**
     * Each tick of an open gateway: close it once its time is up, its owner has gone, or the anchor is
     * no longer anchored; otherwise send through whatever stands in it.
     */
    public static void tick(PhantomGatewayEntity gate) {
        if (!(gate.level() instanceof ServerLevel level)) {
            return;
        }
        UUID ownerId = gate.getOwnerId();
        MinecraftServer server = level.getServer();
        ServerPlayer owner = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner == null || getOpen(ownerId) != gate || level.getGameTime() >= gate.closesAt()
                || !AnchorManager.isAnchored(owner, PetKind.ANCHOR)) {
            close(gate);
            return;
        }
        // Still opening, or the anchor isn't back yet (it was just re-created): nothing goes through yet.
        PhantomPetEntity anchor = PetManager.getActive(PetKind.ANCHOR, ownerId);
        if (gate.openness(0.0F) < 1.0F || anchor == null || !anchor.isAnchored()
                || !(anchor.level() instanceof ServerLevel to)) {
            return;
        }
        List<Entity> inside = level.getEntities(gate, gate.getBoundingBox(), GatewayManager::canEnter);
        Map<EntityDimensions, Optional<Vec3>> landings = new HashMap<>();
        int sent = 0;
        for (Entity entity : inside) {
            if (sent++ >= MAX_PER_TICK) {
                break;
            }
            send(entity, owner, anchor, to, landings);
        }
    }

    /**
     * Anything that could take a portal (no bosses, nothing riding something else), except pets and
     * gateways. A player riding their chest may: they get off first.
     */
    private static boolean canEnter(Entity entity) {
        if (entity instanceof PhantomPetEntity || entity instanceof PhantomGatewayEntity
                || !entity.isAlive() || entity.isSpectator() || entity.isOnPortalCooldown()) {
            return false;
        }
        if (entity.isPassenger()) {
            return entity instanceof Player && entity.getVehicle() instanceof PhantomPetEntity;
        }
        return entity.canUsePortal(false);
    }

    private static void send(Entity entity, ServerPlayer owner, PhantomPetEntity anchor, ServerLevel to,
                             Map<EntityDimensions, Optional<Vec3>> landings) {
        ServerLevel from = (ServerLevel) entity.level();
        boolean crossing = from != to;
        if (crossing && !entity.canChangeDimensions(from, to)) {
            entity.setPortalCooldown(RETRY_TICKS);
            return;
        }
        EntityDimensions size = entity instanceof Player player ? player.getDimensions(Pose.STANDING) : entity.getDimensions(entity.getPose());
        Vec3 landing = landings.computeIfAbsent(size, s -> Optional.ofNullable(AnchorTravel.findLanding(s, anchor))).orElse(null);
        if (landing == null) {
            entity.setPortalCooldown(RETRY_TICKS);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.phantomstorage.anchor.no_landing"), true);
            }
            return;
        }
        Vec3 start = entity.position();
        if (entity instanceof ServerPlayer player) {
            Map<PetKind, PhantomPetEntity> followers = crossing && player == owner ? followers(owner) : Map.of();
            player.teleportTo(to, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), player.getXRot());
            player.resetFallDistance();
            player.setPortalCooldown();
            PetManager.bringAlong(player, followers);
            player.displayClientMessage(Component.translatable(player == owner
                    ? "message.phantomstorage.gateway.through" : "message.phantomstorage.gateway.through_other"), true);
        } else if (entity.teleportTo(to, landing.x, landing.y, landing.z, Set.of(), entity.getYRot(), entity.getXRot()) && !crossing) {
            // Crossing makes a new entity in the other dimension, far from any gateway; staying, it's this one.
            entity.setPortalCooldown();
        }
        if (entity instanceof LivingEntity) {
            AnchorTravel.effects(from, start, 0.8F);
            AnchorTravel.effects(to, landing, 1.2F);
        } else {
            to.sendParticles(ParticleTypes.REVERSE_PORTAL, landing.x, landing.y + 0.25, landing.z, 6, 0.1, 0.1, 0.1, 0.02);
        }
    }

    /** The owner's pets following them right now: the ones a crossing would leave behind. */
    private static Map<PetKind, PhantomPetEntity> followers(ServerPlayer owner) {
        Map<PetKind, PhantomPetEntity> followers = new EnumMap<>(PetKind.class);
        for (PetKind kind : PetKind.values()) {
            PhantomPetEntity pet = PetManager.getActive(kind, owner.getUUID());
            if (pet != null && pet.level() == owner.level() && !pet.isAnchored()) {
                followers.put(kind, pet);
            }
        }
        return followers;
    }

    public static void forget(PhantomGatewayEntity gate) {
        UUID owner = gate.getOwnerId();
        if (owner != null) {
            OPEN.remove(owner, gate);
        }
    }

    static void clear() {
        OPEN.clear();
    }
}
