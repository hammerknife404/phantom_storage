package com.phantomstorage.item;

import com.phantomstorage.summon.ChestManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Use: summons the owner's Phantom Chest. Sneak-use: dismisses it.
 * Never relocates or duplicates an existing chest.
 */
public class PhantomCharmItem extends Item {
    public static final int COOLDOWN_TICKS = 5 * 20;

    public PhantomCharmItem(Properties properties) {
        super(properties);
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
        if (ChestManager.getActive(serverPlayer.getUUID()) != null) {
            // Sneak-use dismisses; a plain use never relocates or re-summons an active chest.
            if (!serverPlayer.isShiftKeyDown()) {
                serverPlayer.displayClientMessage(Component.translatable("message.phantomstorage.already_active"), true);
                return InteractionResultHolder.fail(stack);
            }
            ChestManager.dismiss(serverPlayer, false);
            serverPlayer.displayClientMessage(Component.translatable("message.phantomstorage.dismissed"), true);
        } else if (!ChestManager.summon(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        // Cooldown covers both summon and dismiss, so the pair can't be spammed.
        serverPlayer.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.phantomstorage.phantom_charm.tooltip.summon").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.phantomstorage.phantom_charm.tooltip.dismiss").withStyle(ChatFormatting.GRAY));
    }
}
