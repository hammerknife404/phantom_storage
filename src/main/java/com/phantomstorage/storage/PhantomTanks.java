package com.phantomstorage.storage;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A player's phantom fluid storage: {@link #TANKS} tanks of {@link #CAPACITY} mB, one fluid each.
 * Attached to the player like {@link PhantomInventory}, so it saves atomically with the player.
 *
 * <p>As a whole it is an {@link IFluidHandler} for bucket use on the pet: fills top up a tank that
 * already holds the fluid before starting an empty one, and plain drains take from the first
 * non-empty tank. The GUI addresses single tanks through {@link #tank(int)}.
 */
public class PhantomTanks implements IFluidHandler, INBTSerializable<CompoundTag> {
    public static final int TANKS = 4;
    public static final int CAPACITY = 32_000;
    private static final String KEY_TANKS = "Tanks";

    private final FluidTank[] tanks = new FluidTank[TANKS];

    public PhantomTanks() {
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i] = new FluidTank(CAPACITY);
        }
    }

    public FluidTank tank(int index) {
        return this.tanks[index];
    }

    /** Client copy: replace contents with what the server sent. */
    public void set(List<FluidStack> fluids) {
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i].setFluid(i < fluids.size() ? fluids.get(i).copy() : FluidStack.EMPTY);
        }
    }

    public int totalAmount() {
        int total = 0;
        for (FluidTank tank : this.tanks) {
            total += tank.getFluidAmount();
        }
        return total;
    }

    /** The fluid with the most volume, for rendering the pet; empty when all tanks are. */
    public FluidStack dominant() {
        FluidStack best = FluidStack.EMPTY;
        for (FluidTank tank : this.tanks) {
            if (tank.getFluidAmount() > best.getAmount()) {
                best = tank.getFluid();
            }
        }
        return best;
    }

    // ---- IFluidHandler -------------------------------------------------------------------------

    @Override
    public int getTanks() {
        return TANKS;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.tanks[tank].getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.tanks[tank].isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        // Matching tanks first, so one fluid doesn't spread over several tanks.
        for (FluidTank tank : this.tanks) {
            if (!tank.isEmpty() && FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                int filled = tank.fill(resource, action);
                if (filled > 0) {
                    return filled;
                }
            }
        }
        for (FluidTank tank : this.tanks) {
            if (tank.isEmpty()) {
                return tank.fill(resource, action);
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        for (FluidTank tank : this.tanks) {
            FluidStack drained = tank.drain(resource, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (FluidTank tank : this.tanks) {
            if (!tank.isEmpty()) {
                return tank.drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }

    // ---- persistence ---------------------------------------------------------------------------

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (FluidTank tank : this.tanks) {
            list.add(tank.writeToNBT(provider, new CompoundTag()));
        }
        CompoundTag tag = new CompoundTag();
        tag.put(KEY_TANKS, list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ListTag list = tag.getList(KEY_TANKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i].setFluid(FluidStack.EMPTY);
            if (i < list.size()) {
                this.tanks[i].readFromNBT(provider, list.getCompound(i));
            }
        }
    }
}
