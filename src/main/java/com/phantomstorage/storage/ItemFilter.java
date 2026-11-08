package com.phantomstorage.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Nine ghost entries: single copies of items, never real stock. An item matches when an entry is the
 * same item type, whatever its components (enchantments, names, potion), so one entry covers every
 * variant. Used for the chest's void filter and the collector's filter, and shown through
 * {@code GhostSlot}s, which never move real items in or out.
 */
public class ItemFilter extends SimpleContainer {
    public static final int SIZE = 9;

    public ItemFilter() {
        super(SIZE);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (int i = 0; i < SIZE; i++) {
            if (this.getItem(i).is(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    /** Ghost entries hold one item each. */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, this.getItems(), provider);
        return tag;
    }

    public void load(HolderLookup.Provider provider, CompoundTag tag) {
        this.clearContent();
        ContainerHelper.loadAllItems(tag, this.getItems(), provider);
    }
}
