package com.phantomstorage.storage;

import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Moves fluid between one container item and a handler. Anything with a fluid-handler capability
 * (buckets, modded tanks) goes through {@link FluidUtil}; glass and water bottles have no capability
 * in NeoForge, so they are handled here as 250 mB of water each.
 */
public final class FluidContainers {
    public static final int BOTTLE = FluidType.BUCKET_VOLUME / 4;

    private FluidContainers() {}

    /** Whether {@code stack} is something these methods can work with. */
    public static boolean isContainer(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || isWaterBottle(stack) || FluidUtil.getFluidHandler(stack).isPresent();
    }

    /** Empties one {@code container} into {@code target}; the result is the emptied container. */
    public static FluidActionResult pour(ItemStack container, IFluidHandler target, @Nullable Player player) {
        if (isWaterBottle(container)) {
            FluidStack water = new FluidStack(Fluids.WATER, BOTTLE);
            if (target.fill(water, IFluidHandler.FluidAction.SIMULATE) != BOTTLE) {
                return FluidActionResult.FAILURE;
            }
            target.fill(water, IFluidHandler.FluidAction.EXECUTE);
            playSound(player, true);
            return new FluidActionResult(new ItemStack(Items.GLASS_BOTTLE));
        }
        return FluidUtil.tryEmptyContainer(container, target, Integer.MAX_VALUE, player, true);
    }

    /** Fills one {@code container} from {@code source}; the result is the filled container. */
    public static FluidActionResult take(ItemStack container, IFluidHandler source, @Nullable Player player) {
        if (container.is(Items.GLASS_BOTTLE)) {
            FluidStack water = new FluidStack(Fluids.WATER, BOTTLE);
            if (source.drain(water, IFluidHandler.FluidAction.SIMULATE).getAmount() != BOTTLE) {
                return FluidActionResult.FAILURE;
            }
            source.drain(water, IFluidHandler.FluidAction.EXECUTE);
            playSound(player, false);
            return new FluidActionResult(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }
        return FluidUtil.tryFillContainer(container, source, Integer.MAX_VALUE, player, true);
    }

    /**
     * Fill the container if it can take fluid, else empty it, like right-clicking a cauldron.
     * One container from {@code stack} is used.
     */
    public static FluidActionResult interact(ItemStack stack, IFluidHandler handler, @Nullable Player player) {
        FluidActionResult filled = take(stack, handler, player);
        return filled.isSuccess() ? filled : pour(stack, handler, player);
    }

    private static boolean isWaterBottle(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
    }

    private static void playSound(@Nullable Player player, boolean pour) {
        if (player != null) {
            player.level().playSound(null, player.getX(), player.getY() + 0.5, player.getZ(),
                    pour ? SoundEvents.BOTTLE_EMPTY : SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
