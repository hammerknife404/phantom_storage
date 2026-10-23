package com.phantomstorage.summon;

import com.phantomstorage.entity.PhantomPetEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class PhantomPetEvents {
    private static final int STRANDED_CHECK_INTERVAL = 10;

    private PhantomPetEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(PhantomPetEvents::onEntityInteract);
        bus.addListener(PhantomPetEvents::onLoggedOut);
        bus.addListener(PhantomPetEvents::onChangedDimension);
        bus.addListener(PhantomPetEvents::onRespawn);
        bus.addListener(PhantomPetEvents::onDeath);
        bus.addListener(PhantomPetEvents::onServerStopped);
        bus.addListener(PhantomPetEvents::onAdvancementEarned);
        bus.addListener(PhantomPetEvents::onPlayerTick);
    }

    /**
     * All right-click handling for the pets. Intercepting here (before Mob#interact) also blocks
     * leads and other vanilla interactions, so nobody but the owner can move or open it.
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof PhantomPetEntity pet)) {
            return;
        }
        Player player = event.getEntity();
        boolean owner = pet.isOwnedBy(player);

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
                player.displayClientMessage(Component.translatable(pet.kind().message("not_owner")), true);
            }
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isShiftKeyDown()) {
                if (!pet.sneakInteract(serverPlayer, event.getHand())) {
                    pet.toggleStay(serverPlayer);
                }
            } else {
                pet.interact(serverPlayer, event.getHand());
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Fires before the player is saved, so returned grid items land in the saved inventory.
        if (event.getEntity() instanceof ServerPlayer player) {
            PetManager.dismissAll(player, true);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PetManager.dismissAll(player, false);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (PetKind kind : PetKind.values()) {
                PhantomPetEntity pet = PetManager.getActive(kind, player.getUUID());
                if (pet != null && pet.level() != player.level()) {
                    PetManager.dismiss(kind, player, false);
                }
            }
        }
    }

    /**
     * Vanilla leaves an open menu dangling on death, which would lose the crafting grid, void filter
     * and cursor contents with the old player object. Closing it here (before death drops) returns them to the
     * inventory, so they drop or are kept exactly like the rest of the inventory.
     */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (PetKind kind : PetKind.values()) {
                if (kind.isMenu(player.containerMenu)) {
                    player.closeContainer();
                }
            }
        }
    }

    /** Tells the player when an advancement unlocks a pet for their charm. */
    private static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (PetKind kind : PetKind.values()) {
                if (kind.isUnlockedBy(event.getAdvancement().id())) {
                    player.sendSystemMessage(Component.translatable("message.phantomstorage.unlocked", kind.displayName())
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % STRANDED_CHECK_INTERVAL == 0) {
            PetManager.rescueStranded(player);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        PetManager.clear();
    }
}
