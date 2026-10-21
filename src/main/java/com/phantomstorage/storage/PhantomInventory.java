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
    private static final String KEY_SORT_MODE = "SortMode";
    private static final String KEY_SORT_DESCENDING = "SortDescending";

    private SortMode sortMode = SortMode.NAME;
    private boolean sortDescending;

    public PhantomInventory() {
        super(SIZE);
    }

    public SortMode getSortMode() {
        return this.sortMode;
    }

    public boolean isSortDescending() {
        return this.sortDescending;
    }

    public void cycleSortMode(boolean forward) {
        SortMode[] modes = SortMode.values();
        this.sortMode = modes[Math.floorMod(this.sortMode.ordinal() + (forward ? 1 : -1), modes.length)];
    }

    public void toggleSortDirection() {
        this.sortDescending = !this.sortDescending;
    }

    /** Mode and direction packed into one int, for a menu data slot: {@code ordinal * 2 + descending}. */
    public int packedSort() {
        return packSort(this.sortMode, this.sortDescending);
    }

    public static int packSort(SortMode mode, boolean descending) {
        return mode.ordinal() * 2 + (descending ? 1 : 0);
    }

    public static SortMode unpackSortMode(int packed) {
        SortMode[] modes = SortMode.values();
        return modes[Math.floorMod(packed / 2, modes.length)];
    }

    public static boolean unpackSortDescending(int packed) {
        return (packed & 1) != 0;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, getItems(), provider);
        tag.putString(KEY_SORT_MODE, this.sortMode.name());
        tag.putBoolean(KEY_SORT_DESCENDING, this.sortDescending);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        clearContent();
        ContainerHelper.loadAllItems(tag, getItems(), provider);
        this.sortMode = SortMode.NAME;
        for (SortMode mode : SortMode.values()) {
            if (mode.name().equals(tag.getString(KEY_SORT_MODE))) {
                this.sortMode = mode;
            }
        }
        this.sortDescending = tag.getBoolean(KEY_SORT_DESCENDING);
    }
}
