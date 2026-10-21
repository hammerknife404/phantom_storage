package com.phantomstorage.item;

import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.summon.PetKind;
import com.phantomstorage.summon.PetManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The one summoner for every pet. Its mode ({@link ModRegistries#CHARM_MODE}) picks the pet; sneak +
 * scroll cycles it (see {@code CharmModePayload}), skipping pets the player hasn't unlocked yet.
 * Use: summons the owner's pet of that kind, or recalls it to their side if it's already out.
 * Sneak-use: dismisses it. Never duplicates a pet or moves it across dimensions.
 * All three share one cooldown, whatever the mode.
 */
public class PhantomCharmItem extends Item {
    public static final int COOLDOWN_TICKS = 5 * 20;

    public PhantomCharmItem(Properties properties) {
        super(properties);
    }

    public static PetKind getMode(ItemStack stack) {
        return stack.getOrDefault(ModRegistries.CHARM_MODE.get(), PetKind.CHEST);
    }

    /**
     * Server-side: switch to the next (or previous) unlocked pet kind and say which on the action bar.
     * With nothing else unlocked, says what the next pet needs instead.
     */
    public static void cycleMode(ServerPlayer player, ItemStack stack, boolean forward) {
        PetKind[] kinds = PetKind.values();
        int step = forward ? 1 : kinds.length - 1;
        int current = getMode(stack).ordinal();
        for (int i = 1; i < kinds.length; i++) {
            PetKind mode = kinds[(current + step * i) % kinds.length];
            if (mode.isUnlocked(player)) {
                stack.set(ModRegistries.CHARM_MODE.get(), mode);
                player.displayClientMessage(Component.translatable("message.phantomstorage.charm.mode", mode.displayName()), true);
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), player.getSoundSource(), 0.3F, 1.6F);
                return;
            }
        }
        PetKind next = kinds[(current + step) % kinds.length];
        if (next.ordinal() != current) {
            player.displayClientMessage(next.lockedMessage(player), true);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The client only predicts; the server decides (and swings the arm) on success.
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.consume(stack);
        }
        // Server-side cooldown check; vanilla also checks this, but never trust the client path alone.
        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        PetKind kind = getMode(stack);
        if (!kind.isUnlocked(serverPlayer)) {
            serverPlayer.displayClientMessage(kind.lockedMessage(serverPlayer), true);
            return InteractionResultHolder.fail(stack);
        }
        PhantomPetEntity active = PetManager.getActive(kind, serverPlayer.getUUID());
        if (active != null) {
            if (serverPlayer.isShiftKeyDown()) {
                PetManager.dismiss(kind, serverPlayer, false);
                serverPlayer.displayClientMessage(Component.translatable(kind.message("dismissed")), true);
            } else if (active.level() == serverPlayer.level()) {
                active.recallTo(serverPlayer);
                serverPlayer.displayClientMessage(Component.translatable(kind.message("recalled")), true);
            } else {
                return InteractionResultHolder.fail(stack);
            }
        } else if (!PetManager.summon(kind, serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        // Cooldown covers both summon and dismiss, so the pair can't be spammed.
        serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.phantomstorage.phantom_charm.tooltip.mode", getMode(stack).displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.phantomstorage.phantom_charm.tooltip.summon").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.phantomstorage.phantom_charm.tooltip.dismiss").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.phantomstorage.phantom_charm.tooltip.cycle").withStyle(ChatFormatting.GRAY));
    }
}
