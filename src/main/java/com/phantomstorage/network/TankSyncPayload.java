package com.phantomstorage.network;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.PhantomTankMenu;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: the full tank contents, active tank and options for an open {@link PhantomTankMenu}.
 * Vanilla data slots carry only 16-bit ints, which can't describe a fluid with components, so the
 * menu sends this whenever any of it changes.
 */
public record TankSyncPayload(int containerId, List<FluidStack> fluids, int active, boolean overfill, boolean drainAll)
        implements CustomPacketPayload {
    public static final Type<TankSyncPayload> TYPE = new Type<>(PhantomStorage.id("tank_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TankSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TankSyncPayload::containerId,
            FluidStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), TankSyncPayload::fluids,
            ByteBufCodecs.VAR_INT, TankSyncPayload::active,
            ByteBufCodecs.BOOL, TankSyncPayload::overfill,
            ByteBufCodecs.BOOL, TankSyncPayload::drainAll,
            TankSyncPayload::new);

    @Override
    public Type<TankSyncPayload> type() {
        return TYPE;
    }

    /** Runs on the client main thread. A stale id (menu already closed) is ignored. */
    static void handle(TankSyncPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof PhantomTankMenu menu && menu.containerId == payload.containerId()) {
            menu.receive(payload.fluids(), payload.active(), payload.overfill(), payload.drainAll());
        }
    }
}
