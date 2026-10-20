package com.phantomstorage.entity;

import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.summon.PetKind;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The summoned chest: opens the owner's item storage ({@code ModRegistries.STORAGE}). */
public class PhantomChestEntity extends PhantomPetEntity {
    private static final EntityDataAccessor<Boolean> DATA_OPEN =
            SynchedEntityData.defineId(PhantomChestEntity.class, EntityDataSerializers.BOOLEAN);
    private static final float LID_SPEED = 0.1F;

    private float lidOpenness;
    private float lidOpennessO;

    public PhantomChestEntity(EntityType<? extends PhantomChestEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public PetKind kind() {
        return PetKind.CHEST;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OPEN, false);
    }

    @Override
    public void interact(ServerPlayer owner, InteractionHand hand) {
        owner.openMenu(new SimpleMenuProvider(
                (windowId, inventory, p) -> new PhantomChestMenu(windowId, inventory, p.getData(ModRegistries.STORAGE), this),
                this.getMenuTitle()));
    }

    public Component getMenuTitle() {
        return this.titleOr("container.phantomstorage.phantom_chest");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.lidOpennessO = this.lidOpenness;
            this.lidOpenness = Mth.clamp(this.lidOpenness + (this.isOpen() ? LID_SPEED : -LID_SPEED), 0.0F, 1.0F);
        }
    }

    // ---- lid ----------------------------------------------------------------------------------

    @Override
    protected void onFirstViewer() {
        this.entityData.set(DATA_OPEN, true);
        this.level().playSound(null, this.getX(), this.getY() + 0.5, this.getZ(),
                SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 0.5F, this.random.nextFloat() * 0.1F + 0.9F);
    }

    @Override
    protected void onLastViewerLeft() {
        this.entityData.set(DATA_OPEN, false);
        this.level().playSound(null, this.getX(), this.getY() + 0.5, this.getZ(),
                SoundEvents.ENDER_CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, this.random.nextFloat() * 0.1F + 0.9F);
    }

    public boolean isOpen() {
        return this.entityData.get(DATA_OPEN);
    }

    public float getLidOpenness(float partialTick) {
        return Mth.lerp(partialTick, this.lidOpennessO, this.lidOpenness);
    }
}
