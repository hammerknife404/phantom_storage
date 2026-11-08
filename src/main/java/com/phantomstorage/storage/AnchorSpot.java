package com.phantomstorage.storage;

import com.google.gson.JsonParseException;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Where a player's Phantom Anchor is anchored, if it is. Attached to the player like the other storage,
 * so the anchor outlives logouts and restarts even though its entity is never saved: the entity is
 * re-created here. Unset while the anchor follows its owner or isn't out.
 */
public class AnchorSpot implements INBTSerializable<CompoundTag> {
    private static final String KEY_DIMENSION = "Dimension";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_NAME = "Name";

    @Nullable
    private ResourceKey<Level> dimension;
    private Vec3 pos = Vec3.ZERO;
    /** The anchor's name-tag name, so it survives the entity being re-created. */
    @Nullable
    private Component name;

    public boolean isSet() {
        return this.dimension != null;
    }

    /** Null when unset. */
    @Nullable
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public Vec3 pos() {
        return this.pos;
    }

    @Nullable
    public Component name() {
        return this.name;
    }

    public void set(ResourceKey<Level> dimension, Vec3 pos, @Nullable Component name) {
        this.dimension = dimension;
        this.pos = pos;
        this.name = name;
    }

    public void clear() {
        this.dimension = null;
        this.pos = Vec3.ZERO;
        this.name = null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (this.dimension != null) {
            tag.putString(KEY_DIMENSION, this.dimension.location().toString());
            tag.putDouble(KEY_X, this.pos.x);
            tag.putDouble(KEY_Y, this.pos.y);
            tag.putDouble(KEY_Z, this.pos.z);
            if (this.name != null) {
                tag.putString(KEY_NAME, Component.Serializer.toJson(this.name, provider));
            }
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.clear();
        if (!tag.contains(KEY_DIMENSION)) {
            return;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(KEY_DIMENSION));
        if (dimension == null) {
            return;
        }
        Component name = null;
        if (tag.contains(KEY_NAME)) {
            try {
                name = Component.Serializer.fromJson(tag.getString(KEY_NAME), provider);
            } catch (JsonParseException e) {
                // A name that no longer parses just drops; the spot still counts.
            }
        }
        this.set(ResourceKey.create(Registries.DIMENSION, dimension),
                new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z)), name);
    }
}
