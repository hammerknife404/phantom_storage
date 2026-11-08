package com.phantomstorage.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A filter entry ({@link com.phantomstorage.storage.ItemFilter}): shows a ghost copy of an item and never
 * holds real stock. Clicking it with an item sets the entry to a single copy (the cursor keeps its
 * stack); clicking it empty-handed clears it. Menus route clicks here through {@link #click}, so
 * vanilla's slot logic never moves items in or out.
 */
public class GhostSlot extends Slot {
    public GhostSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    /** Drawn as a fake item (no glint or animation); screens add a veil so it reads as a ghost. */
    @Override
    public boolean isFake() {
        return true;
    }

    /**
     * Handles a click on a ghost slot: set from the cursor, or clear. Number-key swaps, throws, clones
     * and the like do nothing. Call from {@link AbstractContainerMenu#clicked} before the vanilla logic.
     *
     * @return true when this was a ghost slot (handled or ignored); false to let vanilla handle the click.
     */
    public static boolean click(AbstractContainerMenu menu, int slotId, ClickType type) {
        if (slotId < 0 || slotId >= menu.slots.size() || !(menu.slots.get(slotId) instanceof GhostSlot slot)) {
            return false;
        }
        if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
            ItemStack carried = menu.getCarried();
            slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
        }
        return true;
    }
}
