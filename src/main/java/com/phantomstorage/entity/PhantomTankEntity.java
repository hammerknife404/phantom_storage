package com.phantomstorage.entity;

import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.FluidContainers;
import com.phantomstorage.storage.PhantomTanks;
import com.phantomstorage.summon.PetKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * it with a bucket, bottle or other fluid container pours in or takes out of the active tank without
 * opening the GUI; sneak-right-clicking with one cycles the active tank. Each tank's fluid and level,
 * and which is active, are synced from the owner's storage for the renderer.
 */
public class PhantomTankEntity extends PhantomPetEntity {
    private static final List<EntityDataAccessor<Integer>> DATA_FLUID = new ArrayList<>(PhantomTanks.TANKS);
    private static final List<EntityDataAccessor<Float>> DATA_FILL = new ArrayList<>(PhantomTanks.TANKS);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE =
            SynchedEntityData.defineId(PhantomTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(PhantomTankEntity.class, EntityDataSerializers.INT);
    private static final int DISPLAY_INTERVAL = 10;
    /** Opening the jar rings like amethyst: one of these, at a random pitch. */
    private static final List<SoundEvent> OPEN_SOUNDS = List.of(
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.AMETHYST_BLOCK_HIT,
            SoundEvents.AMETHYST_BLOCK_PLACE, SoundEvents.AMETHYST_BLOCK_STEP);

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
        builder.define(DATA_ACTIVE, 0);
        builder.define(DATA_STYLE, 0);
    }

    /** Each summon shows the next {@link TankStyle} frame. */
    @Override
    public void onSummoned(ServerPlayer owner) {
        PhantomTanks tanks = owner.getData(ModRegistries.TANKS);
        tanks.nextStyle();
        this.updateDisplay(tanks);
    }

    @Override
    public void interact(ServerPlayer owner, InteractionHand hand) {
        PhantomTanks tanks = owner.getData(ModRegistries.TANKS);
        ItemStack held = owner.getItemInHand(hand);
        if (FluidContainers.isContainer(held)) {
            FluidActionResult result = FluidContainers.interact(held, tanks.selected(), owner);
            if (result.isSuccess()) {
                owner.setItemInHand(hand, ItemUtils.createFilledResult(held, owner, result.getResult()));
            }
            this.updateDisplay(tanks);
            // The active tank's level, so the GUI isn't needed for exact numbers. Shown even when
            // nothing moved (full, wrong fluid), which says why. Overfill/drain all move the active
            // tank to the one actually used, so this is always the tank that changed.
            owner.displayClientMessage(levelMessage(tanks, tanks.getActive()), true);
            return;
        }
        owner.openMenu(new SimpleMenuProvider(
                (windowId, inventory, p) -> new PhantomTankMenu(windowId, inventory, tanks, this),
                this.titleOr("container.phantomstorage.phantom_tank")));
    }

    @Override
    protected void onFirstViewer() {
        SoundEvent sound = OPEN_SOUNDS.get(this.random.nextInt(OPEN_SOUNDS.size()));
        this.level().playSound(null, this.getX(), this.getY() + 0.5, this.getZ(),
                sound, SoundSource.NEUTRAL, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
    }

    /** "Tank 2: Water — 12,000 / 32,000 mB", or "Tank 2: Empty". */
    private static Component levelMessage(PhantomTanks tanks, int tank) {
        FluidStack fluid = tanks.getFluidInTank(tank);
        if (fluid.isEmpty()) {
            return Component.translatable("message.phantomstorage.tank.level_empty", tank + 1);
        }
        return Component.translatable("message.phantomstorage.tank.level", tank + 1, fluid.getHoverName(),
                String.format("%,d", fluid.getAmount()), String.format("%,d", PhantomTanks.CAPACITY));
    }

    /** Sneak-use with a fluid container cycles the active tank; empty-handed it still toggles stay. */
    @Override
    public boolean sneakInteract(ServerPlayer owner, InteractionHand hand) {
        if (!FluidContainers.isContainer(owner.getItemInHand(hand))) {
            return false;
        }
        PhantomTanks tanks = owner.getData(ModRegistries.TANKS);
        int active = tanks.cycleActive();
        FluidStack fluid = tanks.getFluidInTank(active);
        Component contents = fluid.isEmpty()
                ? Component.translatable("tooltip.phantomstorage.tank_empty")
                : Component.translatable("message.phantomstorage.tank.contents",
                        fluid.getHoverName(), String.format("%,d", fluid.getAmount()));
        owner.displayClientMessage(Component.translatable("message.phantomstorage.tank.active", active + 1, contents), true);
        this.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, 1.4F);
        this.updateDisplay(tanks);
        return true;
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
        this.entityData.set(DATA_ACTIVE, tanks.getActive());
        this.entityData.set(DATA_STYLE, tanks.getStyle());
    }

    /** Client: the frame to draw. */
    public TankStyle getDisplayStyle() {
        return TankStyle.byIndex(this.entityData.get(DATA_STYLE));
    }

    /** Client: the active tank's index. */
    public int getDisplayActive() {
        return this.entityData.get(DATA_ACTIVE);
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
