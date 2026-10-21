package com.phantomstorage.summon;

import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.item.PhantomCharmItem;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Phantoms keep their distance from their own kind, the way they avoid cats: they won't target a
 * player who carries a Phantom Charm, nor anyone while a phantom pet is within {@link #PET_RADIUS}
 * blocks of the phantom. Without a target they just keep circling. Server-side, events only.
 */
public final class PhantomRepel {
    /** Same reach as vanilla's cat check. */
    private static final double PET_RADIUS = 16.0;
    /** How often a phantom that already has a target re-checks it. */
    private static final int RECHECK_TICKS = 10;

    private PhantomRepel() {}

    public static void register(IEventBus bus) {
        bus.addListener(PhantomRepel::onChangeTarget);
        bus.addListener(PhantomRepel::onEntityTick);
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Phantom phantom
                && event.getNewAboutToBeSetTarget() instanceof Player player
                && isRepelled(phantom, player)) {
            event.setCanceled(true);
        }
    }

    /** Drops a target picked before the charm or pet showed up. */
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Phantom phantom
                && !phantom.level().isClientSide()
                && phantom.tickCount % RECHECK_TICKS == 0
                && phantom.getTarget() instanceof Player player
                && isRepelled(phantom, player)) {
            phantom.setTarget(null);
        }
    }

    private static boolean isRepelled(Phantom phantom, Player player) {
        return player.getInventory().contains(stack -> stack.getItem() instanceof PhantomCharmItem)
                || !phantom.level().getEntitiesOfClass(PhantomPetEntity.class,
                        phantom.getBoundingBox().inflate(PET_RADIUS)).isEmpty();
    }
}
