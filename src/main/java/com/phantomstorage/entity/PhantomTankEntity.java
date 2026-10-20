package com.phantomstorage.entity;

import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.PhantomTanks;
import com.phantomstorage.summon.PetKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The summoned tank: opens the owner's fluid storage ({@code ModRegistries.TANKS}). Right-clicking
 * it with a bucket (or any fluid container) pours in or takes out without opening the GUI.
 * It shows its fullest fluid and overall fill level, synced from the owner's storage.
 */
public class PhantomTankEntity extends PhantomPetEntity {
    private static final EntityDataAccessor<Integer> DATA_FLUID =
            SynchedEntityData.defineId(PhantomTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FILL =
            SynchedEntityData.defineId(PhantomTankEntity.class, EntityDataSerializers.FLOAT);
    private static final int DISPLAY_INTERVAL = 10;
    private static final float TOTAL_CAPACITY = (float) PhantomTanks.TANKS * PhantomTanks.CAPACITY;

    public PhantomTankEntity(EntityType<? extends PhantomTankEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public PetKind kind() {
        return PetKind.TANK;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLUID, BuiltInRegistries.FLUID.getId(Fluids.EMPTY));
        builder.define(DATA_FILL, 0.0F);
    }

    @Override
    public void interact(ServerPlayer owner, InteractionHand hand) {
        PhantomTanks tanks = owner.getData(ModRegistries.TANKS);
        if (FluidUtil.getFluidHandler(owner.getItemInHand(hand)).isPresent()) {
            FluidUtil.interactWithFluidHandler(owner, hand, tanks);
            this.updateDisplay(tanks);
            return;
        }
        owner.openMenu(new SimpleMenuProvider(
                (windowId, inventory, p) -> new PhantomTankMenu(windowId, inventory, tanks, this),
                this.titleOr("container.phantomstorage.phantom_tank")));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount % DISPLAY_INTERVAL == 0
                && this.getOwnerPlayer() instanceof Player owner) {
            this.updateDisplay(owner.getData(ModRegistries.TANKS));
        }
    }

    /** Synced entity data only goes out when a value actually changes. */
    private void updateDisplay(PhantomTanks tanks) {
        FluidStack dominant = tanks.dominant();
        this.entityData.set(DATA_FLUID, BuiltInRegistries.FLUID.getId(dominant.isEmpty() ? Fluids.EMPTY : dominant.getFluid()));
        this.entityData.set(DATA_FILL, tanks.totalAmount() / TOTAL_CAPACITY);
    }

    /** Client: the fluid to draw inside the glass ({@link Fluids#EMPTY} when empty). */
    public Fluid getDisplayFluid() {
        Fluid fluid = BuiltInRegistries.FLUID.byId(this.entityData.get(DATA_FLUID));
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    /** Client: 0..1 of total capacity across all tanks. */
    public float getDisplayFill() {
        return this.entityData.get(DATA_FILL);
    }
}
