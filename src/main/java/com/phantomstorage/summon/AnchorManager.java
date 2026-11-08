package com.phantomstorage.summon;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.AnchorSpot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Keeps each player's anchored Phantom Anchor in place while they're online. The spot lives on the
 * player ({@link AnchorSpot}) and is the source of truth; the entity is never saved, so after a login
 * or restart it is re-created there once the chunk is up. While the owner is online the spot's chunk
 * is force-loaded and ticking, so farms and furnaces there keep running; while they're offline,
 * nothing is. All on the server thread.
 */
public final class AnchorManager {
    /**
     * Tickets are owned by the player's UUID. Any found in a save are dropped on load: nobody is
     * online yet, and each owner's ticket comes back when they log in.
     */
    private static final TicketController TICKETS = new TicketController(PhantomStorage.id("anchor"),
            (level, tickets) -> List.copyOf(tickets.getEntityTickets().keySet()).forEach(tickets::removeAllTickets));
    /** The chunk each owner currently holds loaded. */
    private static final Map<UUID, Loaded> LOADED = new HashMap<>();

    private record Loaded(ServerLevel level, ChunkPos chunk) {}

    private AnchorManager() {}

    public static void registerTickets(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    public static AnchorSpot spot(ServerPlayer player) {
        return player.getData(ModRegistries.ANCHOR);
    }

    /** The player's anchor is anchored somewhere, whether or not its entity is back yet. */
    public static boolean isAnchored(ServerPlayer player) {
        return spot(player).isSet();
    }

    /** Anchor the owner's anchor where it is now: remember the spot and keep its chunk loaded. */
    public static void anchor(ServerPlayer owner, PhantomPetEntity anchor) {
        if (anchor.level() instanceof ServerLevel level) {
            spot(owner).set(level.dimension(), anchor.position(), anchor.getCustomName());
            load(owner.getUUID(), level, anchor.chunkPosition());
        }
    }

    /** It follows again, or is gone for good: forget the spot and let its chunk go. */
    public static void release(ServerPlayer owner) {
        spot(owner).clear();
        unload(owner.getUUID());
    }

    /**
     * Logout: the entity goes (it is never saved) and its chunk with it; the spot stays for next time.
     *
     * @param anchor the live anchor, or null if it wasn't back yet: the spot is kept as it is.
     */
    static void suspend(ServerPlayer owner, @Nullable PhantomPetEntity anchor) {
        if (anchor != null && anchor.isAnchored()) {
            anchor(owner, anchor);
        }
        unload(owner.getUUID());
    }

    /** /kill on the live anchor (not a stale copy): it's gone, spot and all. */
    public static void onKilled(ServerPlayer owner, PhantomPetEntity anchor) {
        if (PetManager.getActive(PetKind.ANCHOR, owner.getUUID()) == anchor) {
            release(owner);
        }
    }

    /**
     * From the owner's tick, every few ticks: keep the spot, the loaded chunk and the entity in step.
     * The spot follows the entity (a swap or /tp may move it); with no entity yet, the chunk is loaded
     * and the anchor re-created once that chunk's entities are in.
     */
    static void tick(ServerPlayer owner) {
        AnchorSpot spot = spot(owner);
        if (!spot.isSet()) {
            unload(owner.getUUID());
            return;
        }
        PhantomPetEntity live = PetManager.getActive(PetKind.ANCHOR, owner.getUUID());
        if (live != null) {
            if (live.isAnchored()) {
                anchor(owner, live);
            } else {
                release(owner);
            }
            return;
        }
        ServerLevel level = owner.server.getLevel(spot.dimension());
        if (level == null) {
            // Its dimension is gone (a removed mod or datapack): nothing to come back to.
            release(owner);
            return;
        }
        BlockPos pos = BlockPos.containing(spot.pos());
        load(owner.getUUID(), level, new ChunkPos(pos));
        if (level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos))) {
            PetManager.restoreAnchor(owner, level, spot.pos(), spot.name());
        }
    }

    private static void load(UUID owner, ServerLevel level, ChunkPos chunk) {
        Loaded now = new Loaded(level, chunk);
        Loaded was = LOADED.put(owner, now);
        if (now.equals(was)) {
            return;
        }
        // New chunk first, so a move between neighbours never lets go of the entity's chunk.
        TICKETS.forceChunk(level, owner, chunk.x, chunk.z, true, true);
        if (was != null) {
            TICKETS.forceChunk(was.level(), owner, was.chunk().x, was.chunk().z, false, true);
        }
    }

    private static void unload(UUID owner) {
        Loaded was = LOADED.remove(owner);
        if (was != null) {
            TICKETS.forceChunk(was.level(), owner, was.chunk().x, was.chunk().z, false, true);
        }
    }

    /** Server stopped: tickets die with the levels, and stale ones are dropped on the next load. */
    static void clear() {
        LOADED.clear();
    }
}
