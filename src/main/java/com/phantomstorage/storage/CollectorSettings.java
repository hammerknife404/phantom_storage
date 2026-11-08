package com.phantomstorage.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * A player's Phantom Collector settings and its experience bank, attached to the player like the other
 * storage (the collector entity itself holds nothing). The filter lists what to take, or with
 * {@link #isSkip() skip} on, what to leave; empty, it takes everything.
 */
public class CollectorSettings implements INBTSerializable<CompoundTag> {
    public static final int MIN_RANGE = 3;
    public static final int MAX_RANGE = 12;
    public static final int DEFAULT_RANGE = 8;
    private static final String KEY_RANGE = "Range";
    private static final String KEY_SKIP = "Skip";
    private static final String KEY_BANK = "BankedXp";
    private static final String KEY_FILTER = "Filter";

    private int range = DEFAULT_RANGE;
    private boolean skip;
    /** Experience collected while anchored (its owner may be anywhere); taken from its panel. */
    private int bankedXp;
    private final ItemFilter filter = new ItemFilter();

    public int range() {
        return this.range;
    }

    public void adjustRange(int delta) {
        this.range = Mth.clamp(this.range + delta, MIN_RANGE, MAX_RANGE);
    }

    public boolean isSkip() {
        return this.skip;
    }

    public void toggleSkip() {
        this.skip = !this.skip;
    }

    public ItemFilter filter() {
        return this.filter;
    }

    /** Whether the collector should pick {@code stack} up. */
    public boolean allows(ItemStack stack) {
        if (this.filter.isEmpty()) {
            return true;
        }
        return this.filter.matches(stack) != this.skip;
    }

    public int bankedXp() {
        return this.bankedXp;
    }

    /** Adds to the bank, stopping at the int limit rather than wrapping. */
    public void bank(int xp) {
        this.bankedXp = (int) Math.min(Integer.MAX_VALUE, (long) this.bankedXp + Math.max(0, xp));
    }

    /** Empties the bank, returning what was in it. */
    public int takeXp() {
        int xp = this.bankedXp;
        this.bankedXp = 0;
        return xp;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_RANGE, this.range);
        tag.putBoolean(KEY_SKIP, this.skip);
        tag.putInt(KEY_BANK, this.bankedXp);
        tag.put(KEY_FILTER, this.filter.save(provider));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.range = tag.contains(KEY_RANGE) ? Mth.clamp(tag.getInt(KEY_RANGE), MIN_RANGE, MAX_RANGE) : DEFAULT_RANGE;
        this.skip = tag.getBoolean(KEY_SKIP);
        this.bankedXp = Math.max(0, tag.getInt(KEY_BANK));
        this.filter.load(provider, tag.getCompound(KEY_FILTER));
    }
}
