package com.phantomstorage.summon;

import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.menu.PhantomAnchorMenu;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomCollectorMenu;
import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.registry.ModRegistries;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The pets a player can have out at once (at most one of each). Which ones they may summon, and
 * the charm modes that use a pet without being one, are {@link CharmMode}s.
 */
public enum PetKind implements StringRepresentable {
    CHEST("chest", ModRegistries.PHANTOM_CHEST, PhantomChestMenu.class, false),
    TANK("tank", ModRegistries.PHANTOM_TANK, PhantomTankMenu.class, false),
    /** Its panel shows where it is. */
    ANCHOR("anchor", ModRegistries.PHANTOM_ANCHOR, PhantomAnchorMenu.class, true),
    /** Told to stay, it anchors like the anchor and keeps collecting there. */
    COLLECTOR("collector", ModRegistries.PHANTOM_COLLECTOR, PhantomCollectorMenu.class, true);

    private final String key;
    private final Supplier<? extends EntityType<? extends PhantomPetEntity>> type;
    @Nullable
    private final Class<? extends AbstractContainerMenu> menuClass;
    private final boolean canAnchor;

    PetKind(String key, Supplier<? extends EntityType<? extends PhantomPetEntity>> type,
            @Nullable Class<? extends AbstractContainerMenu> menuClass, boolean canAnchor) {
        this.key = key;
        this.type = type;
        this.menuClass = menuClass;
        this.canAnchor = canAnchor;
    }

    /**
     * Whether, told to stay, this pet is anchored: it keeps its spot through its owner's logouts,
     * deaths, dimension changes and restarts, and its chunk loaded while they're online ({@link AnchorManager}).
     */
    public boolean canAnchor() {
        return this.canAnchor;
    }

    /** The charm mode that summons it. */
    public CharmMode mode() {
        return CharmMode.of(this);
    }

    public EntityType<? extends PhantomPetEntity> entityType() {
        return this.type.get();
    }

    /** Whether {@code menu} is this kind's menu (it must close before the pet goes). */
    public boolean isMenu(AbstractContainerMenu menu) {
        return this.menuClass != null && this.menuClass.isInstance(menu);
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    /** "Phantom Tank": the pet's entity name. */
    public Component fullName() {
        return this.entityType().getDescription();
    }


    /** {@code message.phantomstorage.<kind>.<what>} */
    public String message(String what) {
        return "message.phantomstorage." + this.key + "." + what;
    }
}
