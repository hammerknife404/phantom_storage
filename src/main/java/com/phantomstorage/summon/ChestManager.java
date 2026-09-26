package com.phantomstorage.summon;

import com.phantomstorage.entity.HoverBounds;
import com.phantomstorage.entity.PhantomChestEntity;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.registry.ModRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side registry enforcing "one chest per player, ever". All access happens on the server
 * thread, so check-then-spawn in {@link #summon} cannot race.
 */
public final class ChestManager {
    private static final Map<UUID, PhantomChestEntity> ACTIVE = new HashMap<>();

    private ChestManager() {}

    /** The owner's live chest, or null. Lazily forgets chests that were removed (chunk unload, /kill). */
    @Nullable
    public static PhantomChestEntity getActive(UUID owner) {
        PhantomChestEntity chest = ACTIVE.get(owner);
        if (chest != null && chest.isRemoved()) {
            ACTIVE.remove(owner);
            return null;
        }
        return chest;
    }

    public static boolean summon(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || getActive(player.getUUID()) != null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        PhantomChestEntity chest = ModRegistries.PHANTOM_CHEST.get().create(level);
        if (chest == null) {
            return false;
        }
        chest.bindOwner(player);

        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(1.5);
        double x = player.getX() + flat.x;
        double z = player.getZ() + flat.z;
        double y = HoverBounds.clampY(level, x, player.getY() + 1.0, z);
        chest.moveTo(x, y, z, player.getYRot() + 180.0F, 0.0F);
        chest.yBodyRot = chest.getYRot();
        chest.yHeadRot = chest.getYRot();

        if (!level.addFreshEntity(chest)) {
            return false;
        }
        ACTIVE.put(player.getUUID(), chest);

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.45, z, 24, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5F, 1.4F);
        return true;
    }

    /**
     * Removes the player's chest (if any). Closes the menu first so the crafting grid and cursor
     * stack return to the player before anything else happens.
     *
     * @param disconnecting true on logout: close without sending packets to a dying connection.
     */
    public static void dismiss(ServerPlayer player, boolean disconnecting) {
        if (player.containerMenu instanceof PhantomChestMenu) {
            if (disconnecting) {
                player.doCloseContainer();
            } else {
                player.closeContainer();
            }
        }
        PhantomChestEntity chest = ACTIVE.remove(player.getUUID());
        if (chest != null && !chest.isRemoved()) {
            chest.vanish();
        }
    }

    public static void forget(PhantomChestEntity chest) {
        UUID owner = chest.getOwnerUUID();
        if (owner != null) {
            ACTIVE.remove(owner, chest);
        }
    }

    static void clear() {
        ACTIVE.clear();
    }
}
