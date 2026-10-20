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
 * <p>As a whole it is an {@link IFluidHandler}: fills top up a tank that already holds the fluid
 * before starting an empty one, and plain drains take from the first non-empty tank. Bucket use on
 * the pet goes through {@link #selected()}, which prefers the {@linkplain #getActive() active tank}.
 * The GUI addresses single tanks through {@link #tank(int)}.
 */
public class PhantomTanks implements IFluidHandler, INBTSerializable<CompoundTag> {
    public static final int TANKS = 4;
    public static final int CAPACITY = 32_000;
    private static final String KEY_TANKS = "Tanks";
    private static final String KEY_ACTIVE = "Active";
    private static final String KEY_STYLE = "Style";

    private final FluidTank[] tanks = new FluidTank[TANKS];
    private final IFluidHandler selected = new Selected();
    private int active;
    /** Index into {@code TankStyle}; only the look of the pet. */
    private int style;

    public PhantomTanks() {
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i] = new FluidTank(CAPACITY);
        }
    }

    public FluidTank tank(int index) {
        return this.tanks[index];
    }

    /** Client copy: replace contents with what the server sent. */
    public void set(List<FluidStack> fluids, int active) {
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i].setFluid(i < fluids.size() ? fluids.get(i).copy() : FluidStack.EMPTY);
        }
        this.setActive(active);
    }

    /** The tank that bucket use on the pet works with first. */
    public int getActive() {
        return this.active;
    }

    public void setActive(int active) {
        this.active = Math.floorMod(active, TANKS);
    }

    /** Next tank along, wrapping round; returns the new active index. */
    public int cycleActive() {
        this.setActive(this.active + 1);
        return this.active;
    }

    public int getStyle() {
        return this.style;
    }

    /** Next frame style along; called on every summon. */
    public int nextStyle() {
        return ++this.style;
    }

    /**
     * The handler for bucket use on the pet. Drains come only from the active tank, so the player
     * always knows what they'll get. Fills go into the active tank, or wherever {@link #fill} would
     * put them when it holds another fluid or is full, so a pour never fails just for that.
     */
    public IFluidHandler selected() {
        return this.selected;
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
        // A tank that can cover the whole request first, so a nearly empty tank doesn't block a full bottle.
        for (FluidTank tank : this.tanks) {
            if (tank.getFluidAmount() >= resource.getAmount() && FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                return tank.drain(resource, action);
            }
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
        tag.putInt(KEY_ACTIVE, this.active);
        tag.putInt(KEY_STYLE, this.style);
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
        this.setActive(tag.getInt(KEY_ACTIVE));
        this.style = tag.getInt(KEY_STYLE);
    }

    private final class Selected implements IFluidHandler {
        private FluidTank activeTank() {
            return PhantomTanks.this.tanks[PhantomTanks.this.active];
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.activeTank().getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return this.activeTank().isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            // All or nothing in the active tank, so one bucket isn't split across two tanks.
            FluidTank tank = this.activeTank();
            if (tank.fill(resource, FluidAction.SIMULATE) == resource.getAmount()) {
                return tank.fill(resource, action);
            }
            return PhantomTanks.this.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return this.activeTank().drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return this.activeTank().drain(maxDrain, action);
        }
    }
}
