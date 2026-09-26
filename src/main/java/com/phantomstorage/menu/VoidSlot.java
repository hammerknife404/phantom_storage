package com.phantomstorage.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/** A void filter slot. Deletion timing is driven by {@link PhantomChestMenu#broadcastChanges()}. */
public class VoidSlot extends Slot {
    public VoidSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }
}
