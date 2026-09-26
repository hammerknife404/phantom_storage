package com.phantomstorage.menu;

import com.phantomstorage.entity.PhantomChestEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.PhantomInventory;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * One screen: 12x9 storage, a 3x3 crafting grid, and a 3x3 void filter.
 * All state is server-authoritative; the client copy is a dummy container fed by vanilla sync.
 */
public class PhantomChestMenu extends AbstractContainerMenu {
    // Layout (pixels, relative to the GUI's top-left). Shared with the screen.
    public static final int GUI_WIDTH = 292;
    public static final int GUI_HEIGHT = 266;
    public static final int STORAGE_X = 8;
    public static final int STORAGE_Y = 16;
    public static final int CRAFT_X = 232;
    public static final int CRAFT_Y = 16;
    public static final int RESULT_X = 250;
    public static final int RESULT_Y = 84;
    public static final int VOID_X = 232;
    public static final int VOID_Y = 122;
    public static final int PLAYER_INV_X = 35;
    public static final int PLAYER_INV_Y = 184;
    public static final int HOTBAR_Y = 242;

    // Slot index ranges (end-exclusive).
    public static final int STORAGE_START = 0;
    public static final int STORAGE_END = STORAGE_START + PhantomInventory.SIZE;
    public static final int RESULT_SLOT = STORAGE_END;
    public static final int CRAFT_START = RESULT_SLOT + 1;
    public static final int CRAFT_END = CRAFT_START + 9;
    public static final int VOID_START = CRAFT_END;
    public static final int VOID_END = VOID_START + 9;
    public static final int INV_START = VOID_END;
    public static final int INV_END = INV_START + 27;
    public static final int HOTBAR_START = INV_END;
    public static final int HOTBAR_END = HOTBAR_START + 9;

    /** Ticks an item stays visible in a void slot before it is destroyed. */
    public static final int VOID_DELAY_TICKS = 5;

    private final Player player;
    private final Container storage;
    @Nullable
    private final PhantomChestEntity chest;
    private final TransientCraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final SimpleContainer voidSlots = new SimpleContainer(9);
    private final long[] voidExpiry = new long[9];

    /** Client constructor. */
    public PhantomChestMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new SimpleContainer(PhantomInventory.SIZE), null);
    }

    /** Server constructor: {@code storage} is the owner's attached inventory. */
    public PhantomChestMenu(int windowId, Inventory playerInventory, Container storage, @Nullable PhantomChestEntity chest) {
        super(ModRegistries.PHANTOM_CHEST_MENU.get(), windowId);
        checkContainerSize(storage, PhantomInventory.SIZE);
        this.player = playerInventory.player;
        this.storage = storage;
        this.chest = chest;
        Arrays.fill(this.voidExpiry, -1L);

        for (int row = 0; row < PhantomInventory.ROWS; row++) {
            for (int col = 0; col < PhantomInventory.COLUMNS; col++) {
                this.addSlot(new Slot(storage, col + row * PhantomInventory.COLUMNS,
                        STORAGE_X + col * 18, STORAGE_Y + row * 18));
            }
        }

        this.addSlot(new ResultSlot(this.player, this.craftSlots, this.resultSlots, 0, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.craftSlots, col + row * 3, CRAFT_X + col * 18, CRAFT_Y + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new VoidSlot(this.voidSlots, col + row * 3, VOID_X + col * 18, VOID_Y + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }

        if (chest != null) {
            chest.onMenuOpened();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.chest == null) {
            return true; // client side; the server decides
        }
        return this.chest.isAlive()
                && this.chest.isOwnedBy(player)
                && this.chest.level() == player.level()
                && player.distanceToSqr(this.chest) <= 64.0;
    }

    // ---- crafting ------------------------------------------------------------------------------

    @Override
    public void slotsChanged(Container container) {
        if (container == this.craftSlots) {
            this.updateCraftingResult();
        }
    }

    private void updateCraftingResult() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Level level = serverPlayer.level();
        CraftingInput input = this.craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe =
                serverPlayer.serverLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = recipe.get();
            if (this.resultSlots.setRecipeUsed(level, serverPlayer, holder)) {
                ItemStack assembled = holder.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }
        this.resultSlots.setItem(0, result);
        this.setRemoteSlot(RESULT_SLOT, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                this.containerId, this.incrementStateId(), RESULT_SLOT, result));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    // ---- void filter ---------------------------------------------------------------------------

    /**
     * Called by the server every tick while the menu is open (and after each click).
     * Items get a short visible beat, then are destroyed. Nine array checks per tick; zero cost when closed.
     */
    @Override
    public void broadcastChanges() {
        if (!this.player.level().isClientSide()) {
            this.tickVoid(this.player.level().getGameTime());
        }
        super.broadcastChanges();
    }

    private void tickVoid(long now) {
        for (int i = 0; i < this.voidExpiry.length; i++) {
            if (this.voidSlots.getItem(i).isEmpty()) {
                this.voidExpiry[i] = -1L;
            } else if (this.voidExpiry[i] < 0L) {
                this.voidExpiry[i] = now + VOID_DELAY_TICKS;
            } else if (now >= this.voidExpiry[i]) {
                this.voidSlots.setItem(i, ItemStack.EMPTY);
                this.voidExpiry[i] = -1L;
            }
        }
    }

    // ---- shift-click ---------------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == RESULT_SLOT) {
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!this.moveToPlayerThenStorage(stack)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < STORAGE_END) {
            if (!this.moveToPlayer(stack)) {
                return ItemStack.EMPTY;
            }
        } else if (index < VOID_END) {
            // Crafting grid and void filter: back to the player first, then storage.
            if (!this.moveToPlayerThenStorage(stack)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory: into storage (never into the void filter), else swap inventory <-> hotbar.
            if (!this.moveTo(stack, STORAGE_START, STORAGE_END, false)) {
                boolean moved = index < INV_END
                        ? this.moveTo(stack, HOTBAR_START, HOTBAR_END, false)
                        : this.moveTo(stack, INV_START, INV_END, false);
                if (!moved) {
                    return ItemStack.EMPTY;
                }
            }
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
        if (index == RESULT_SLOT) {
            player.drop(stack, false);
        }
        return original;
    }

    /** Non-short-circuiting: a partial move to the player still lets the rest overflow into storage. */
    private boolean moveToPlayerThenStorage(ItemStack stack) {
        return this.moveToPlayer(stack) | this.moveTo(stack, STORAGE_START, STORAGE_END, false);
    }

    private boolean moveToPlayer(ItemStack stack) {
        return this.moveTo(stack, INV_START, HOTBAR_END, true);
    }

    private boolean moveTo(ItemStack stack, int start, int end, boolean reverse) {
        return !stack.isEmpty() && this.moveItemStackTo(stack, start, end, reverse);
    }

    // ---- close ---------------------------------------------------------------------------------

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            this.clearContainer(player, this.craftSlots);
            this.voidSlots.clearContent();
            if (this.chest != null) {
                this.chest.onMenuClosed();
            }
        }
    }
}
