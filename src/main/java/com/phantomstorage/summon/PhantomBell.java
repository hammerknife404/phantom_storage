package com.phantomstorage.summon;

import com.phantomstorage.item.PhantomCharmItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The charm's bell mode: every pet at once. Use summons each unlocked pet that isn't out; sneak-use
 * dismisses every pet that follows and closes the gateway. Anchored pets are left where they are,
 * since dismissing one forgets its spot: dismiss those one at a time in their own mode.
 */
public final class PhantomBell {
    private PhantomBell() {}

    /** @return the charm cooldown in ticks, or {@link PhantomCharmItem#FAILED}. */
    public static int use(ServerPlayer player) {
        if (player.isShiftKeyDown()) {
            return dismissAll(player);
        }
        if (!player.isAlive() || player.isSpectator()) {
            return PhantomCharmItem.FAILED;
        }
        int summoned = 0;
        for (PetKind kind : PetKind.values()) {
            if (kind.mode().isUnlocked(player)
                    && PetManager.getActive(kind, player.getUUID()) == null
                    && !AnchorManager.isAnchored(player, kind)
                    && PetManager.summon(kind, player)) {
                summoned++;
            }
        }
        if (summoned == 0) {
            player.displayClientMessage(Component.translatable("message.phantomstorage.bell.all_out"), true);
            return PhantomCharmItem.FAILED;
        }
        ring(player, 1.0F);
        player.displayClientMessage(Component.translatable("message.phantomstorage.bell.summoned", summoned), true);
        return PhantomCharmItem.COOLDOWN_TICKS;
    }

    private static int dismissAll(ServerPlayer player) {
        int dismissed = 0;
        for (PetKind kind : PetKind.values()) {
            if (PetManager.getActive(kind, player.getUUID()) != null && !AnchorManager.isAnchored(player, kind)) {
                PetManager.dismiss(kind, player, false);
                dismissed++;
            }
        }
        boolean closed = GatewayManager.closeFor(player);
        if (dismissed == 0 && !closed) {
            player.displayClientMessage(Component.translatable("message.phantomstorage.bell.none_out"), true);
            return PhantomCharmItem.FAILED;
        }
        ring(player, 0.7F);
        player.displayClientMessage(Component.translatable("message.phantomstorage.bell.dismissed", dismissed), true);
        return PhantomCharmItem.COOLDOWN_TICKS;
    }

    private static void ring(ServerPlayer player, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, pitch);
    }
}
