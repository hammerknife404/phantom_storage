package com.phantomstorage.network;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.item.PhantomCharmItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: the player scrolled while sneaking with a Phantom Charm in the main hand.
 * The server re-checks all of that before cycling the charm's mode.
 */
public record CharmModePayload(boolean forward) implements CustomPacketPayload {
    public static final Type<CharmModePayload> TYPE = new Type<>(PhantomStorage.id("charm_mode"));
    public static final StreamCodec<ByteBuf, CharmModePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(CharmModePayload::new, CharmModePayload::forward);

    @Override
    public Type<CharmModePayload> type() {
        return TYPE;
    }

    /** Runs on the server main thread. */
    static void handle(CharmModePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isShiftKeyDown()) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof PhantomCharmItem) {
                PhantomCharmItem.cycleMode(player, stack, payload.forward());
            }
        }
    }
}
