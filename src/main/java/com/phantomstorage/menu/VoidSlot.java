package com.phantomstorage.menu;

import net.minecraft.world.Container;

/**
 * A void filter entry: a {@link GhostSlot} in the chest's void grid. Items matching any entry are
 * destroyed when the owner's collector picks them up ({@code PhantomInventory#voidFilter}).
 */
public class VoidSlot extends GhostSlot {
    public VoidSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }
}
