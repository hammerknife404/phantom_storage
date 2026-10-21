package com.phantomstorage.menu;

import com.phantomstorage.entity.PhantomTankEntity;
import com.phantomstorage.network.TankSyncPayload;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.FluidContainers;
import com.phantomstorage.storage.PhantomTanks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The tank screen: {@link PhantomTanks#TANKS} gauges over the player inventory. There are no fluid
 * slots: clicking a gauge with a container on the cursor pours it in (left) or fills it (right),
 * clicking it with an empty cursor makes it the active tank, and each gauge has a trash button.
 * Two checkboxes set how bucket use on the pet treats the active tank ({@link PhantomTanks#selected()}).
 * All of it arrives as container-button clicks and runs server-side.
 *
 * <pre>
 * +------------------------------+
 * | title                        |
 * |  [##]   [##]   [##]   [##]   |  gauges
 * |   T      T      T      T     |  trash
 * | [ ] Overfill   [ ] Drain all |  options
 * | Inventory                    |
 * | player inventory + hotbar    |
 * +------------------------------+
 * </pre>
 */
public class PhantomTankMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 194;
    public static final int GAUGE_X = 17;
    public static final int GAUGE_STEP = 40;
    public static final int GAUGE_Y = 18;
    public static final int GAUGE_W = 22;
    public static final int GAUGE_H = 52;
    public static final int TRASH_Y = GAUGE_Y + GAUGE_H + 3;
    public static final int OPTION_Y = TRASH_Y + 17;
    public static final int OPTION_SIZE = 9;
    public static final int OVERFILL_X = 8;
    public static final int DRAIN_ALL_X = 92;
    public static final int INV_X = 8;
    public static final int INV_Y = 112;
    public static final int HOTBAR_Y = INV_Y + 58;
    public static final int INV_LABEL_Y = INV_Y - 11;

    /** {@link #clickMenuButton} id = tank * ACTIONS + action. */
    public static final int ACTION_POUR = 0;
    public static final int ACTION_TAKE = 1;
    public static final int ACTION_VOID = 2;
    public static final int ACTION_SELECT = 3;
    public static final int ACTIONS = 4;
    /** Checkbox ids, past every tank's actions. */
    public static final int BUTTON_OVERFILL = PhantomTanks.TANKS * ACTIONS;
    public static final int BUTTON_DRAIN_ALL = BUTTON_OVERFILL + 1;

    private static final int INV_SLOTS = 27;
    private static final int HOTBAR_END = INV_SLOTS + 9;

    private final Player player;
    private final PhantomTanks tanks;
    @Nullable
    private final PhantomTankEntity pet;
    /** Server: what the client was last sent; null forces a send. */
    @Nullable
    private List<FluidStack> lastSent;
    private int lastActive = -1;
    private boolean lastOverfill;
    private boolean lastDrainAll;

    /** Client constructor; contents arrive by {@link TankSyncPayload}. */
    public PhantomTankMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new PhantomTanks(), null);
    }

    /** Server constructor: {@code tanks} is the owner's attached storage. */
    public PhantomTankMenu(int windowId, Inventory playerInventory, PhantomTanks tanks, @Nullable PhantomTankEntity pet) {
        super(ModRegistries.PHANTOM_TANK_MENU.get(), windowId);
        this.player = playerInventory.player;
        this.tanks = tanks;
        this.pet = pet;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        if (pet != null) {
            pet.onMenuOpened();
        }
    }

    public static int gaugeX(int tank) {
        return GAUGE_X + tank * GAUGE_STEP;
    }

    public static int trashX(int tank) {
        return gaugeX(tank) + (GAUGE_W - PhantomLayout.ICON_SIZE) / 2;
    }

    public FluidStack getFluid(int tank) {
        return this.tanks.getFluidInTank(tank);
    }

    public int getActive() {
        return this.tanks.getActive();
    }

    public boolean isOverfill() {
        return this.tanks.isOverfill();
    }

    public boolean isDrainAll() {
        return this.tanks.isDrainAll();
    }

    /** Client: apply a {@link TankSyncPayload}. */
    public void receive(List<FluidStack> fluids, int active, boolean overfill, boolean drainAll) {
        this.tanks.set(fluids, active, overfill, drainAll);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.pet == null || this.pet.canBeUsedBy(player); // client side: the server decides
    }

    // ---- sync ----------------------------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        List<FluidStack> current = new ArrayList<>(PhantomTanks.TANKS);
        boolean changed = this.lastSent == null || this.lastActive != this.tanks.getActive()
                || this.lastOverfill != this.tanks.isOverfill() || this.lastDrainAll != this.tanks.isDrainAll();
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            FluidStack fluid = this.tanks.getFluidInTank(i);
            current.add(fluid.copy());
            if (!changed && !FluidStack.matches(fluid, this.lastSent.get(i))) {
                changed = true;
            }
        }
        if (changed) {
            this.lastSent = current;
            this.lastActive = this.tanks.getActive();
            this.lastOverfill = this.tanks.isOverfill();
            this.lastDrainAll = this.tanks.isDrainAll();
            PacketDistributor.sendToPlayer(serverPlayer, new TankSyncPayload(
                    this.containerId, current, this.lastActive, this.lastOverfill, this.lastDrainAll));
        }
    }

    // ---- buttons -------------------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_OVERFILL) {
            this.tanks.setOverfill(!this.tanks.isOverfill());
            return true;
        }
        if (id == BUTTON_DRAIN_ALL) {
            this.tanks.setDrainAll(!this.tanks.isDrainAll());
            return true;
        }
        int index = id / ACTIONS;
        if (id < 0 || index >= PhantomTanks.TANKS) {
            return false;
        }
        FluidTank tank = this.tanks.tank(index);
        switch (id % ACTIONS) {
            case ACTION_POUR -> this.transfer(player, tank, true);
            case ACTION_TAKE -> this.transfer(player, tank, false);
            case ACTION_SELECT -> this.tanks.setActive(index);
            default -> this.voidTank(player, tank);
        }
        return true;
    }

    /**
     * One container from the cursor stack at a time: a stack of 16 empty buckets fills one bucket,
     * which goes to the inventory (or the ground) while the rest stay on the cursor.
     */
    private void transfer(Player player, FluidTank tank, boolean pour) {
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        FluidActionResult result = pour
                ? FluidContainers.pour(carried, tank, player)
                : FluidContainers.take(carried, tank, player);
        if (!result.isSuccess()) {
            return;
        }
        ItemStack container = result.getResult();
        ItemStack rest = carried.copyWithCount(carried.getCount() - 1);
        if (rest.isEmpty()) {
            this.setCarried(container);
            return;
        }
        this.setCarried(rest);
        player.getInventory().add(container);
        if (!container.isEmpty()) {
            player.drop(container, false);
        }
    }

    private void voidTank(Player player, FluidTank tank) {
        if (!tank.isEmpty()) {
            tank.setFluid(FluidStack.EMPTY);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        }
    }

    // ---- shift-click: inventory <-> hotbar -----------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < INV_SLOTS
                ? this.moveItemStackTo(stack, INV_SLOTS, HOTBAR_END, false)
                : this.moveItemStackTo(stack, 0, INV_SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        if (!player.level().isClientSide()) {
            // Cursor stack back to the inventory even on logout, where vanilla would drop it on the ground.
            ItemStack carried = this.getCarried();
            this.setCarried(ItemStack.EMPTY);
            if (!carried.isEmpty()) {
                player.getInventory().add(carried);
                if (!carried.isEmpty()) {
                    player.drop(carried, false);
                }
            }
            if (this.pet != null) {
                this.pet.onMenuClosed();
            }
        }
        super.removed(player);
    }
}
