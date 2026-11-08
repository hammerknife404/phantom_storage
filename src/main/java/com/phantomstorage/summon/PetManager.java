package com.phantomstorage.summon;

import com.phantomstorage.entity.HoverBounds;
import com.phantomstorage.entity.PhantomPetEntity;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side registry enforcing "one pet of each kind per player, ever". All access happens on the
 * server thread, so check-then-spawn in {@link #summon} cannot race.
 */
public final class PetManager {
    private static final Map<PetKind, Map<UUID, PhantomPetEntity>> ACTIVE = new EnumMap<>(PetKind.class);

    static {
        for (PetKind kind : PetKind.values()) {
            ACTIVE.put(kind, new HashMap<>());
        }
    }

    private PetManager() {}

    /** The owner's live pet of this kind, or null. Lazily forgets pets that were removed (chunk unload, /kill). */
    @Nullable
    public static PhantomPetEntity getActive(PetKind kind, UUID owner) {
        Map<UUID, PhantomPetEntity> active = ACTIVE.get(kind);
        PhantomPetEntity pet = active.get(owner);
        if (pet != null && pet.isRemoved()) {
            active.remove(owner);
            return null;
        }
        return pet;
    }

    public static boolean summon(PetKind kind, ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || getActive(kind, player.getUUID()) != null) {
            return false;
        }
        return spawn(kind, player, null) != null;
    }

    /**
     * Charm recall: pull the pet beside the owner, following again (an anchored pet lets its spot go).
     * A stranded pet is replaced instead, which always works.
     */
    public static void recall(PetKind kind, ServerPlayer player, PhantomPetEntity pet) {
        AnchorManager.release(player, kind);
        PhantomPetEntity target = pet.isStranded() ? replace(kind, player, pet) : pet;
        if (target != null) {
            target.recallTo(player);
        }
    }

    /**
     * Owner-driven watchdog, run from the owner's tick. A pet left behind by a same-dimension teleport
     * (waystones, /tp, modded warps) sits in a chunk that no longer ticks, so neither its follow goal
     * nor its owner check can run; only the owner can notice. Pets told to stay are left alone.
     */
    public static void rescueStranded(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        for (PetKind kind : PetKind.values()) {
            PhantomPetEntity pet = getActive(kind, player.getUUID());
            if (pet != null && pet.level() == player.level() && !pet.isOrderedToSit() && pet.isStranded()) {
                replace(kind, player, pet);
            }
        }
    }

    /** Swaps a stranded pet for a fresh one beside the owner. Safe because pets hold no contents. */
    @Nullable
    private static PhantomPetEntity replace(PetKind kind, ServerPlayer player, PhantomPetEntity old) {
        if (kind.isMenu(player.containerMenu)) {
            player.closeContainer();
        }
        ACTIVE.get(kind).remove(player.getUUID(), old);
        old.discard();
        return spawn(kind, player, old);
    }

    /** @param replacing the stranded pet this one takes over from, or null for a plain summon. */
    @Nullable
    private static PhantomPetEntity spawn(PetKind kind, ServerPlayer player, @Nullable PhantomPetEntity replacing) {
        ServerLevel level = player.serverLevel();
        PhantomPetEntity pet = kind.entityType().create(level);
        if (pet == null) {
            return null;
        }
        pet.bindOwner(player);
        if (replacing == null) {
            pet.onSummoned(player);
        } else {
            pet.inheritFrom(replacing, player);
        }

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(1.5);
        double x = player.getX() + flat.x;
        double z = player.getZ() + flat.z;
        double y = HoverBounds.clampY(level, x, player.getY() + 1.0, z);
        if (!add(kind, player, level, pet, x, y, z, player.getYRot() + 180.0F)) {
            return null;
        }

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.45, z, 24, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5F, 1.4F);
        return pet;
    }

    /**
     * Re-creates an anchored pet at its saved spot, told to stay (after a login or restart). Quietly:
     * its owner is usually far away.
     */
    static void restoreAnchored(PetKind kind, ServerPlayer owner, ServerLevel level, Vec3 pos, @Nullable Component name) {
        if (getActive(kind, owner.getUUID()) != null) {
            return;
        }
        PhantomPetEntity pet = kind.entityType().create(level);
        if (pet == null) {
            return;
        }
        pet.bindOwner(owner);
        pet.setCustomName(name);
        pet.setOrderedToSit(true);
        pet.setInSittingPose(true);
        add(kind, owner, level, pet, pos.x, pos.y, pos.z, 0.0F);
    }

    /** Places a new pet, adds it to the level and makes it the owner's pet of that kind. */
    private static boolean add(PetKind kind, ServerPlayer owner, ServerLevel level, PhantomPetEntity pet,
                               double x, double y, double z, float yaw) {
        pet.moveTo(x, y, z, yaw, 0.0F);
        pet.yBodyRot = pet.getYRot();
        pet.yHeadRot = pet.getYRot();
        if (!level.addFreshEntity(pet)) {
            return false;
        }
        ACTIVE.get(kind).put(owner.getUUID(), pet);
        return true;
    }

    /**
     * Removes the player's pet of this kind (if any), for good: an anchored pet's spot goes too.
     * Closes its menu first so the crafting grid and cursor stack return to the player before anything
     * else happens.
     *
     * @param disconnecting true on logout: close without sending packets to a dying connection.
     */
    public static void dismiss(PetKind kind, ServerPlayer player, boolean disconnecting) {
        if (kind.isMenu(player.containerMenu)) {
            if (disconnecting) {
                player.doCloseContainer();
            } else {
                player.closeContainer();
            }
        }
        PhantomPetEntity pet = ACTIVE.get(kind).remove(player.getUUID());
        if (pet != null && !pet.isRemoved()) {
            pet.vanish();
        }
        AnchorManager.release(player, kind);
    }

    /**
     * Logout or dimension change: every pet goes, except an anchored one, which stays where it is.
     * On logout only its entity and loaded chunk go; the spot is kept and it comes back on next login.
     */
    public static void dismissAll(ServerPlayer player, boolean disconnecting) {
        for (PetKind kind : PetKind.values()) {
            if (AnchorManager.isAnchored(player, kind)) {
                if (disconnecting) {
                    PhantomPetEntity anchor = ACTIVE.get(kind).remove(player.getUUID());
                    if (anchor != null && anchor.isRemoved()) {
                        anchor = null;
                    }
                    AnchorManager.suspend(player, kind, anchor);
                    if (anchor != null) {
                        anchor.discard();
                    }
                }
                continue;
            }
            dismiss(kind, player, disconnecting);
        }
    }

    public static void forget(PhantomPetEntity pet) {
        UUID owner = pet.getOwnerUUID();
        if (owner != null) {
            ACTIVE.get(pet.kind()).remove(owner, pet);
        }
    }

    static void clear() {
        ACTIVE.values().forEach(Map::clear);
    }
}
