package com.phantomstorage.summon;

import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.item.PhantomCharmItem;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The charm's saddle mode: the owner rides their Phantom Chest and steers it where they look (the
 * flying is in {@link PhantomPetEntity}). No entity of its own: use brings the chest under them,
 * summoning it if it isn't out; use again, or sneak, gets them off; sneak-use dismisses the chest.
 */
public final class PhantomSaddle {
    private PhantomSaddle() {}

    /** @return the charm cooldown in ticks, or {@link PhantomCharmItem#FAILED}. */
    public static int use(ServerPlayer player) {
        PhantomPetEntity chest = PetManager.getActive(PetKind.CHEST, player.getUUID());
        if (player.isShiftKeyDown()) {
            if (chest == null) {
                return PhantomCharmItem.FAILED;
            }
            PetManager.dismiss(PetKind.CHEST, player, false);
            player.displayClientMessage(Component.translatable(PetKind.CHEST.message("dismissed")), true);
            return PhantomCharmItem.COOLDOWN_TICKS;
        }
        if (chest != null && player.getVehicle() == chest) {
            player.stopRiding();
            return PhantomCharmItem.SHORT_COOLDOWN_TICKS;
        }
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping()
                || (chest != null && chest.level() != player.level())) {
            return PhantomCharmItem.FAILED;
        }
        Vec3 feet = seat(player);
        if (feet == null) {
            player.displayClientMessage(Component.translatable("message.phantomstorage.saddle.no_room"), true);
            return PhantomCharmItem.FAILED;
        }
        boolean summoned = chest == null;
        if (summoned) {
            if (!PetManager.summon(PetKind.CHEST, player)) {
                return PhantomCharmItem.FAILED;
            }
            chest = PetManager.getActive(PetKind.CHEST, player.getUUID());
        } else {
            chest = PetManager.live(PetKind.CHEST, player, chest);
        }
        if (chest == null || !chest.carry(player, feet)) {
            return summoned ? PhantomCharmItem.COOLDOWN_TICKS : PhantomCharmItem.FAILED;
        }
        player.serverLevel().playSound(null, feet.x, feet.y, feet.z, SoundEvents.HORSE_SADDLE, SoundSource.PLAYERS, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable("message.phantomstorage.saddle.mounted"), true);
        return summoned ? PhantomCharmItem.COOLDOWN_TICKS : PhantomCharmItem.SHORT_COOLDOWN_TICKS;
    }

    /**
     * Where the chest goes: at the player's feet, or centred on their block if that's clear and their own
     * spot isn't; and only if they'd still fit once lifted onto it. Null if neither works.
     */
    @Nullable
    private static Vec3 seat(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 own = player.position();
        Vec3 centred = new Vec3(player.getBlockX() + 0.5, own.y, player.getBlockZ() + 0.5);
        for (Vec3 feet : new Vec3[] {own, centred}) {
            AABB chest = PetKind.CHEST.entityType().getDimensions().makeBoundingBox(feet);
            // The rider sits on the lid: their feet end up this far above the chest's base.
            double lift = chest.getYsize() - player.getAttachments().get(EntityAttachment.VEHICLE, 0, player.getYRot()).y;
            AABB rider = player.getBoundingBox().move(feet.x - own.x, lift, feet.z - own.z);
            if (level.noCollision(chest) && level.noCollision(player, rider)) {
                return feet;
            }
        }
        return null;
    }
}
