package com.phantomstorage.storage;

import java.util.List;
import java.util.function.Function;
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
    private static final String KEY_OVERFILL = "Overfill";
    private static final String KEY_DRAIN_ALL = "DrainAll";

    private final FluidTank[] tanks = new FluidTank[TANKS];
    private final IFluidHandler selected = new Selected();
    private int active;
    /** Index into {@code TankStyle}; only the look of the pet. */
    private int style;
    /** Bucket use on the pet: pours may spill into other tanks. Off ("protected") by default. */
    private boolean overfill;
    /** Bucket use on the pet: pulls continue into the next tank once the active one is empty. Off by default. */
    private boolean drainAll;

    public PhantomTanks() {
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i] = new FluidTank(CAPACITY);
        }
    }

    public FluidTank tank(int index) {
        return this.tanks[index];
    }

    /** Client copy: replace contents with what the server sent. */
    public void set(List<FluidStack> fluids, int active, boolean overfill, boolean drainAll) {
        for (int i = 0; i < TANKS; i++) {
            this.tanks[i].setFluid(i < fluids.size() ? fluids.get(i).copy() : FluidStack.EMPTY);
        }
        this.setActive(active);
        this.overfill = overfill;
        this.drainAll = drainAll;
    }

    public boolean isOverfill() {
        return this.overfill;
    }

    public void setOverfill(boolean overfill) {
        this.overfill = overfill;
    }

    public boolean isDrainAll() {
        return this.drainAll;
    }

    public void setDrainAll(boolean drainAll) {
        this.drainAll = drainAll;
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
     * The handler for bucket use on the pet, around the active tank. Protected (both options off):
     * pours go only into the active tank and pulls come only from it. {@link #isOverfill() Overfill}
     * lets a pour that doesn't fit spill to the next tank with that fluid, else the next empty one;
     * {@link #isDrainAll() drain all} lets pulls carry on into the next non-empty tank once the active
     * one is empty. Either way the tank used becomes active, so the player can see where it went.
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
        tag.putBoolean(KEY_OVERFILL, this.overfill);
        tag.putBoolean(KEY_DRAIN_ALL, this.drainAll);
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
        this.overfill = tag.getBoolean(KEY_OVERFILL);
        this.drainAll = tag.getBoolean(KEY_DRAIN_ALL);
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
            // All or nothing per tank, so one bucket isn't split across two tanks.
            if (fitsWhole(this.activeTank(), resource)) {
                return this.activeTank().fill(resource, action);
            }
            if (!PhantomTanks.this.overfill) {
                return 0;
            }
            // Overfill: the next tank along holding this fluid with room, else the next empty one.
            int target = -1;
            for (int step = 1; step < TANKS && target < 0; step++) {
                int i = (PhantomTanks.this.active + step) % TANKS;
                FluidTank tank = PhantomTanks.this.tanks[i];
                if (!tank.isEmpty() && fitsWhole(tank, resource)) {
                    target = i;
                }
            }
            for (int step = 1; step < TANKS && target < 0; step++) {
                int i = (PhantomTanks.this.active + step) % TANKS;
                if (PhantomTanks.this.tanks[i].isEmpty()) {
                    target = i;
                }
            }
            if (target < 0) {
                return 0;
            }
            int filled = PhantomTanks.this.tanks[target].fill(resource, action);
            if (filled > 0 && action.execute()) {
                PhantomTanks.this.active = target;
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return this.drainChained(tank -> tank.drain(resource, action), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return this.drainChained(tank -> tank.drain(maxDrain, action), action);
        }

        /** The active tank; with "drain all" on and the active tank empty, the next non-empty one, which becomes active. */
        private FluidStack drainChained(Function<FluidTank, FluidStack> drain, FluidAction action) {
            FluidTank active = this.activeTank();
            if (!active.isEmpty() || !PhantomTanks.this.drainAll) {
                return drain.apply(active);
            }
            for (int step = 1; step < TANKS; step++) {
                int i = (PhantomTanks.this.active + step) % TANKS;
                FluidTank tank = PhantomTanks.this.tanks[i];
                if (!tank.isEmpty()) {
                    FluidStack drained = drain.apply(tank);
                    if (!drained.isEmpty() && action.execute()) {
                        PhantomTanks.this.active = i;
                    }
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        private static boolean fitsWhole(FluidTank tank, FluidStack resource) {
            return tank.fill(resource, FluidAction.SIMULATE) == resource.getAmount();
        }
    }
}
