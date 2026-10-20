package com.phantomstorage.summon;

import com.phantomstorage.entity.PhantomChestEntity;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.registry.ModRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class PhantomChestEvents {
    private PhantomChestEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(PhantomChestEvents::onEntityInteract);
        bus.addListener(PhantomChestEvents::onLoggedOut);
        bus.addListener(PhantomChestEvents::onChangedDimension);
        bus.addListener(PhantomChestEvents::onRespawn);
        bus.addListener(PhantomChestEvents::onDeath);
        bus.addListener(PhantomChestEvents::onServerStopped);
    }

    /**
     * All right-click handling for the chest. Intercepting here (before Mob#interact) also blocks
     * leads and other vanilla interactions, so nobody but the owner can move or open it.
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof PhantomChestEntity chest)) {
            return;
        }
        Player player = event.getEntity();
        boolean owner = chest.isOwnedBy(player);

        // Let the owner name their pet with a name tag through the vanilla path.
        if (owner && !player.isShiftKeyDown() && player.getItemInHand(event.getHand()).is(Items.NAME_TAG)) {
            return;
        }

        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }
        if (!owner) {
            if (!player.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("message.phantomstorage.not_owner"), true);
            }
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isShiftKeyDown()) {
                chest.toggleStay(serverPlayer);
            } else {
                serverPlayer.openMenu(menuProvider(chest));
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
    }

    private static MenuProvider menuProvider(PhantomChestEntity chest) {
        return new SimpleMenuProvider(
                (windowId, inventory, p) -> new PhantomChestMenu(windowId, inventory, p.getData(ModRegistries.STORAGE), chest),
                chest.getMenuTitle());
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Fires before the player is saved, so returned grid items land in the saved inventory.
        if (event.getEntity() instanceof ServerPlayer player) {
            ChestManager.dismiss(player, true);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChestManager.dismiss(player, false);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PhantomChestEntity chest = ChestManager.getActive(player.getUUID());
            if (chest != null && chest.level() != player.level()) {
                ChestManager.dismiss(player, false);
            }
        }
    }

    /**
     * Vanilla leaves an open menu dangling on death, which would lose the crafting grid and void filter
     * contents with the old player object. Closing it here (before death drops) returns them to the
     * inventory, so they drop or are kept exactly like the rest of the inventory.
     */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof PhantomChestMenu) {
            player.closeContainer();
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ChestManager.clear();
    }
}
