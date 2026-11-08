package com.phantomstorage.summon;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.AnchorSpot;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Keeps each player's anchored pets in place while they're online: a pet that {@linkplain
 * PetKind#canAnchor() can anchor} (the anchor, the collector) and was told to stay. Its spot lives on
 * the player ({@link AnchorSpot}, one per kind) and is the source of truth; the entity is never saved,
 * so after a login or restart it is re-created there once the chunk is up. While the owner is online
 * the spot's chunk is force-loaded and ticking, so farms and furnaces there keep running; while
 * they're offline, nothing is. All on the server thread.
 */
public final class AnchorManager {
    /**
     * One ticket controller per kind, its tickets owned by the player's UUID, so two pets anchored in
     * one chunk never release each other's ticket. Any found in a save are dropped on load: nobody is
     * online yet, and each owner's tickets come back when they log in.
     */
    private static final Map<PetKind, TicketController> TICKETS = new EnumMap<>(PetKind.class);
    /** The chunk each owner currently holds loaded, per kind. */
    private static final Map<PetKind, Map<UUID, Loaded>> LOADED = new EnumMap<>(PetKind.class);

    static {
        for (PetKind kind : PetKind.values()) {
            if (kind.canAnchor()) {
                TICKETS.put(kind, new TicketController(PhantomStorage.id(kind.getSerializedName()),
                        (level, tickets) -> List.copyOf(tickets.getEntityTickets().keySet()).forEach(tickets::removeAllTickets)));
                LOADED.put(kind, new HashMap<>());
            }
        }
    }

    private record Loaded(ServerLevel level, ChunkPos chunk) {}

    private AnchorManager() {}

    public static void registerTickets(RegisterTicketControllersEvent event) {
        TICKETS.values().forEach(event::register);
    }

    private static AttachmentType<AnchorSpot> spotType(PetKind kind) {
        return switch (kind) {
            case COLLECTOR -> ModRegistries.COLLECTOR_SPOT.get();
            default -> ModRegistries.ANCHOR.get();
        };
    }

    public static AnchorSpot spot(ServerPlayer player, PetKind kind) {
        return player.getData(spotType(kind));
    }

    /** The player's pet of this kind is anchored somewhere, whether or not its entity is back yet. */
    public static boolean isAnchored(ServerPlayer player, PetKind kind) {
        return kind.canAnchor() && spot(player, kind).isSet();
    }

    /** Anchor the owner's pet where it is now: remember the spot and keep its chunk loaded. */
    public static void anchor(ServerPlayer owner, PhantomPetEntity pet) {
        PetKind kind = pet.kind();
        if (kind.canAnchor() && pet.level() instanceof ServerLevel level) {
            spot(owner, kind).set(level.dimension(), pet.position(), pet.getCustomName());
            load(kind, owner.getUUID(), level, pet.chunkPosition());
        }
    }

    /** It follows again, or is gone for good: forget the spot and let its chunk go. */
    public static void release(ServerPlayer owner, PetKind kind) {
        if (kind.canAnchor()) {
            spot(owner, kind).clear();
            unload(kind, owner.getUUID());
        }
    }

    /**
     * Logout: the entity goes (it is never saved) and its chunk with it; the spot stays for next time.
     *
     * @param pet the live pet, or null if it wasn't back yet: the spot is kept as it is.
     */
    static void suspend(ServerPlayer owner, PetKind kind, @Nullable PhantomPetEntity pet) {
        if (pet != null && pet.isAnchored()) {
            anchor(owner, pet);
        }
        unload(kind, owner.getUUID());
    }

    /** /kill on the live pet (not a stale copy): it's gone, spot and all. */
    public static void onKilled(ServerPlayer owner, PhantomPetEntity pet) {
        if (PetManager.getActive(pet.kind(), owner.getUUID()) == pet) {
            release(owner, pet.kind());
        }
    }

    /** From the owner's tick, every few ticks: each anchorable kind in turn. */
    static void tick(ServerPlayer owner) {
        for (PetKind kind : TICKETS.keySet()) {
            tick(owner, kind);
        }
    }

    /**
     * Keep the spot, the loaded chunk and the entity in step. The spot follows the entity (a swap or
     * /tp may move it); with no entity yet, the chunk is loaded and the pet re-created once that
     * chunk's entities are in.
     */
    private static void tick(ServerPlayer owner, PetKind kind) {
        AnchorSpot spot = spot(owner, kind);
        if (!spot.isSet()) {
            unload(kind, owner.getUUID());
            return;
        }
        PhantomPetEntity live = PetManager.getActive(kind, owner.getUUID());
        if (live != null) {
            if (live.isAnchored()) {
                anchor(owner, live);
            } else {
                release(owner, kind);
            }
            return;
        }
        ServerLevel level = owner.server.getLevel(spot.dimension());
        if (level == null) {
            // Its dimension is gone (a removed mod or datapack): nothing to come back to.
            release(owner, kind);
            return;
        }
        BlockPos pos = BlockPos.containing(spot.pos());
        load(kind, owner.getUUID(), level, new ChunkPos(pos));
        if (level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos))) {
            PetManager.restoreAnchored(kind, owner, level, spot.pos(), spot.name());
        }
    }

    private static void load(PetKind kind, UUID owner, ServerLevel level, ChunkPos chunk) {
        Loaded now = new Loaded(level, chunk);
        Loaded was = LOADED.get(kind).put(owner, now);
        if (now.equals(was)) {
            return;
        }
        TicketController tickets = TICKETS.get(kind);
        // New chunk first, so a move between neighbours never lets go of the entity's chunk.
        tickets.forceChunk(level, owner, chunk.x, chunk.z, true, true);
        if (was != null) {
            tickets.forceChunk(was.level(), owner, was.chunk().x, was.chunk().z, false, true);
        }
    }

    private static void unload(PetKind kind, UUID owner) {
        Loaded was = LOADED.get(kind).remove(owner);
        if (was != null) {
            TICKETS.get(kind).forceChunk(was.level(), owner, was.chunk().x, was.chunk().z, false, true);
        }
    }

    /** Server stopped: tickets die with the levels, and stale ones are dropped on the next load. */
    static void clear() {
        LOADED.values().forEach(Map::clear);
    }
}
