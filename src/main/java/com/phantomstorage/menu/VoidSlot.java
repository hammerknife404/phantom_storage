package com.phantomstorage.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/** A void filter slot. Contents are destroyed only by the trash can ({@link PhantomChestMenu#BUTTON_TRASH}). */
public class VoidSlot extends Slot {
    public VoidSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }
}
