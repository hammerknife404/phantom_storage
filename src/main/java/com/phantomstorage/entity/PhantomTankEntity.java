package com.phantomstorage.entity;

import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.FluidContainers;
import com.phantomstorage.storage.PhantomTanks;
import com.phantomstorage.summon.PetKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The summoned tank: opens the owner's fluid storage ({@code ModRegistries.TANKS}). Right-clicking
 * it with a bucket, bottle or other fluid container pours in or takes out without opening the GUI.
 * Each tank's fluid and level is synced from the owner's storage so the renderer can stack them.
 */
public class PhantomTankEntity extends PhantomPetEntity {
    private static final List<EntityDataAccessor<Integer>> DATA_FLUID = new ArrayList<>(PhantomTanks.TANKS);
    private static final List<EntityDataAccessor<Float>> DATA_FILL = new ArrayList<>(PhantomTanks.TANKS);
    private static final int DISPLAY_INTERVAL = 10;

    static {
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            DATA_FLUID.add(SynchedEntityData.defineId(PhantomTankEntity.class, EntityDataSerializers.INT));
            DATA_FILL.add(SynchedEntityData.defineId(PhantomTankEntity.class, EntityDataSerializers.FLOAT));
        }
    }

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
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            builder.define(DATA_FLUID.get(i), BuiltInRegistries.FLUID.getId(Fluids.EMPTY));
            builder.define(DATA_FILL.get(i), 0.0F);
        }
    }

    @Override
    public void interact(ServerPlayer owner, InteractionHand hand) {
        PhantomTanks tanks = owner.getData(ModRegistries.TANKS);
        ItemStack held = owner.getItemInHand(hand);
        if (FluidContainers.isContainer(held)) {
            FluidActionResult result = FluidContainers.interact(held, tanks, owner);
            if (result.isSuccess()) {
                owner.setItemInHand(hand, ItemUtils.createFilledResult(held, owner, result.getResult()));
            }
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
        for (int i = 0; i < PhantomTanks.TANKS; i++) {
            FluidStack fluid = tanks.getFluidInTank(i);
            this.entityData.set(DATA_FLUID.get(i), BuiltInRegistries.FLUID.getId(fluid.isEmpty() ? Fluids.EMPTY : fluid.getFluid()));
            this.entityData.set(DATA_FILL.get(i), fluid.getAmount() / (float) PhantomTanks.CAPACITY);
        }
    }

    /** Client: tank {@code i}'s fluid ({@link Fluids#EMPTY} when empty). */
    public Fluid getDisplayFluid(int i) {
        Fluid fluid = BuiltInRegistries.FLUID.byId(this.entityData.get(DATA_FLUID.get(i)));
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    /** Client: tank {@code i}'s level, 0..1 of one tank's capacity. */
    public float getDisplayFill(int i) {
        return this.entityData.get(DATA_FILL.get(i));
    }
}
