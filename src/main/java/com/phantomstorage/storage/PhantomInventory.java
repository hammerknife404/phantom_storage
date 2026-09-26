package com.phantomstorage.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * A player's 12x9 phantom storage. Attached to the player and persisted in the player's data file,
 * so it is written in the same atomic save as the player's own inventory: an item is never in both
 * or neither after a crash.
 */
public class PhantomInventory extends SimpleContainer implements INBTSerializable<CompoundTag> {
    public static final int COLUMNS = 12;
    public static final int ROWS = 9;
    public static final int SIZE = COLUMNS * ROWS;

    public PhantomInventory() {
        super(SIZE);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, getItems(), provider);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        clearContent();
        ContainerHelper.loadAllItems(tag, getItems(), provider);
    }
}
