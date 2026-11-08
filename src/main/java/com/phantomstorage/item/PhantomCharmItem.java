package com.phantomstorage.item;

import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.summon.AnchorManager;
import com.phantomstorage.summon.AnchorTravel;
import com.phantomstorage.summon.CharmMode;
import com.phantomstorage.summon.GatewayManager;
import com.phantomstorage.summon.PetKind;
import com.phantomstorage.summon.PetManager;
import com.phantomstorage.summon.PhantomBell;
import com.phantomstorage.summon.PhantomSaddle;
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
 * The one charm for every pet. Its mode ({@link ModRegistries#CHARM_MODE}) picks what it does; sneak +
 * scroll cycles it (see {@code CharmModePayload}), skipping modes the player hasn't unlocked yet.
 * Pet modes: use summons the owner's pet of that kind, or recalls it to their side if it's already out;
 * for the anchor, takes the owner to it instead, or swaps places with it when looked at ({@link AnchorTravel}).
 * Sneak-use dismisses it. The saddle rides the chest ({@link PhantomSaddle}); the gateway opens a portal
 * to the anchored anchor ({@link GatewayManager}); the bell calls every pet at once ({@link PhantomBell}). Never duplicates a pet or moves one across dimensions.
 * All of these share one cooldown, whatever the mode.
 */
public class PhantomCharmItem extends Item {
    public static final int COOLDOWN_TICKS = 5 * 20;
    /** Getting on or off a chest that's already out: just long enough to stop a double click. */
    public static final int SHORT_COOLDOWN_TICKS = 10;
    /** A use that did nothing: no cooldown, no swing. */
    public static final int FAILED = -1;

    public PhantomCharmItem(Properties properties) {
        super(properties);
    }

    public static CharmMode getMode(ItemStack stack) {
        return stack.getOrDefault(ModRegistries.CHARM_MODE.get(), CharmMode.CHEST);
    }

    /**
     * Server-side: switch to the next (or previous) unlocked mode and say which on the action bar.
     * With nothing else unlocked, says what the next mode needs instead.
     */
    public static void cycleMode(ServerPlayer player, ItemStack stack, boolean forward) {
        CharmMode[] modes = CharmMode.values();
        int step = forward ? 1 : modes.length - 1;
        int current = getMode(stack).ordinal();
        for (int i = 1; i < modes.length; i++) {
            CharmMode mode = modes[(current + step * i) % modes.length];
            if (mode.isUnlocked(player)) {
                stack.set(ModRegistries.CHARM_MODE.get(), mode);
                player.displayClientMessage(Component.translatable("message.phantomstorage.charm.mode", mode.displayName()), true);
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), player.getSoundSource(), 0.3F, 1.6F);
                return;
            }
        }
        CharmMode next = modes[(current + step) % modes.length];
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
        CharmMode mode = getMode(stack);
        if (!mode.isUnlocked(serverPlayer)) {
            serverPlayer.displayClientMessage(mode.lockedMessage(serverPlayer), true);
            return InteractionResultHolder.fail(stack);
        }
        int cooldown = switch (mode) {
            case SADDLE -> PhantomSaddle.use(serverPlayer);
            case GATEWAY -> GatewayManager.use(serverPlayer);
            case BELL -> PhantomBell.use(serverPlayer);
            default -> usePet(mode.pet(), serverPlayer);
        };
        if (cooldown == FAILED) {
            return InteractionResultHolder.fail(stack);
        }
        // Cooldown covers summon and dismiss alike, so the pair can't be spammed.
        if (cooldown > 0) {
            serverPlayer.getCooldowns().addCooldown(this, cooldown);
        }
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(stack);
    }

    /** A pet mode: summon, recall, travel (anchor) or, sneaking, dismiss. */
    private static int usePet(PetKind kind, ServerPlayer player) {
        PhantomPetEntity active = PetManager.getActive(kind, player.getUUID());
        // Anchored, but its entity isn't back yet (just logged in; its chunk is still loading).
        boolean anchorLoading = active == null && AnchorManager.isAnchored(player, kind);
        if (player.isShiftKeyDown() && (active != null || anchorLoading)) {
            PetManager.dismiss(kind, player, false);
            player.displayClientMessage(Component.translatable(kind.message("dismissed")), true);
        } else if (anchorLoading) {
            player.displayClientMessage(Component.translatable(kind.message("loading")), true);
            return FAILED;
        } else if (active == null) {
            if (!PetManager.summon(kind, player)) {
                return FAILED;
            }
        } else if (kind == PetKind.ANCHOR) {
            if (!AnchorTravel.use(player, active)) {
                return FAILED;
            }
        } else if (active.level() == player.level()) {
            PetManager.recall(kind, player, active);
            player.displayClientMessage(Component.translatable(kind.message("recalled")), true);
        } else {
            return FAILED;
        }
        return COOLDOWN_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CharmMode mode = getMode(stack);
        tooltip.add(Component.translatable("item.phantomstorage.phantom_charm.tooltip.mode", mode.displayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        String prefix = "item.phantomstorage.phantom_charm.tooltip.";
        List<String> lines = switch (mode) {
            case ANCHOR -> List.of("summon.anchor", "swap", "dismiss");
            case SADDLE -> List.of("saddle", "saddle.fly", "dismiss.chest");
            case GATEWAY -> List.of("gateway", "gateway.close");
            case BELL -> List.of("bell", "bell.dismiss");
            default -> List.of("summon", "dismiss");
        };
        for (String line : lines) {
            tooltip.add(Component.translatable(prefix + line).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(prefix + "cycle").withStyle(ChatFormatting.GRAY));
    }
}
