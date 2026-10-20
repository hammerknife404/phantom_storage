package com.phantomstorage.summon;

import com.mojang.serialization.Codec;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.registry.ModRegistries;
import io.netty.buffer.ByteBuf;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** The pets a player can have out at once (at most one of each), and the Phantom Charm's modes. */
public enum PetKind implements StringRepresentable {
    CHEST("chest", ModRegistries.PHANTOM_CHEST, PhantomChestMenu.class),
    TANK("tank", ModRegistries.PHANTOM_TANK, PhantomTankMenu.class);

    public static final Codec<PetKind> CODEC = StringRepresentable.fromEnum(PetKind::values);
    public static final StreamCodec<ByteBuf, PetKind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], PetKind::ordinal);

    private final String key;
    private final Supplier<? extends EntityType<? extends PhantomPetEntity>> type;
    private final Class<? extends AbstractContainerMenu> menuClass;

    PetKind(String key, Supplier<? extends EntityType<? extends PhantomPetEntity>> type,
            Class<? extends AbstractContainerMenu> menuClass) {
        this.key = key;
        this.type = type;
        this.menuClass = menuClass;
    }

    public EntityType<? extends PhantomPetEntity> entityType() {
        return this.type.get();
    }

    /** Whether {@code menu} is this kind's menu (it must close before the pet goes). */
    public boolean isMenu(AbstractContainerMenu menu) {
        return this.menuClass.isInstance(menu);
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    /** "Chest" / "Tank", from {@code pet.phantomstorage.<kind>}. */
    public Component displayName() {
        return Component.translatable("pet.phantomstorage." + this.key);
    }

    /** {@code message.phantomstorage.<kind>.<what>} */
    public String message(String what) {
        return "message.phantomstorage." + this.key + "." + what;
    }
}
