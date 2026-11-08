package com.phantomstorage.entity;

import com.phantomstorage.menu.PhantomCollectorMenu;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.summon.CollectorWork;
import com.phantomstorage.summon.PetKind;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The summoned collector: a small orb of the void that gathers dropped items and experience
 * ({@link CollectorWork}). It follows like the other pets, collecting around its owner; told to stay,
 * it is anchored like the anchor and collects around its spot, wherever its owner is. Carries nothing:
 * items go to the owner's storage, its settings and banked experience live on the owner.
 * Right-clicking it opens its panel ({@link PhantomCollectorMenu}).
 */
public class PhantomCollectorEntity extends PhantomPetEntity {
    public PhantomCollectorEntity(EntityType<? extends PhantomCollectorEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public PetKind kind() {
        return PetKind.COLLECTOR;
    }

    /** Its panel: filter, range, banked experience, stay / follow and dismiss. */
    @Override
    public void interact(ServerPlayer owner, InteractionHand hand) {
        owner.openMenu(new SimpleMenuProvider(
                        (windowId, inventory, p) -> new PhantomCollectorMenu(windowId, inventory, this, owner.getData(ModRegistries.COLLECTOR)),
                        this.titleOr("container.phantomstorage.phantom_collector")),
                buf -> PhantomCollectorMenu.writeOpenData(buf, this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && !this.isRemoved() && this.tickCount % CollectorWork.INTERVAL == 0) {
            // By the player list, not getOwner(): an anchored collector's owner may be in another dimension.
            UUID ownerId = this.getOwnerUUID();
            MinecraftServer server = this.level().getServer();
            if (ownerId != null && server != null && server.getPlayerList().getPlayer(ownerId) instanceof ServerPlayer owner) {
                CollectorWork.collect(this, owner);
            }
        }
    }
}
