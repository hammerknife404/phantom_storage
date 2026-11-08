package com.phantomstorage.entity;

import com.phantomstorage.menu.PhantomAnchorMenu;
import com.phantomstorage.summon.AnchorManager;
import com.phantomstorage.summon.PetKind;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The summoned anchor: a waypoint that follows like the other pets. Told to stay, it is anchored: it
 * keeps its chunk loaded while its owner is online and stays put through their dimension changes,
 * logouts and restarts ({@link AnchorManager}). The charm takes the owner to it, or swaps them
 * ({@code AnchorTravel}). Carries nothing; right-clicking it opens its panel ({@link PhantomAnchorMenu}).
 */
public class PhantomAnchorEntity extends PhantomPetEntity {
    public PhantomAnchorEntity(EntityType<? extends PhantomAnchorEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public PetKind kind() {
        return PetKind.ANCHOR;
    }

    @Override
    public boolean isAnchored() {
        return this.isOrderedToSit();
    }

    /** Its panel: where it is, whether it's anchored, stay / follow and dismiss. */
    @Override
    public void interact(ServerPlayer owner, InteractionHand hand) {
        owner.openMenu(new SimpleMenuProvider(
                        (windowId, inventory, p) -> new PhantomAnchorMenu(windowId, inventory, this),
                        this.titleOr("container.phantomstorage.phantom_anchor")),
                buf -> PhantomAnchorMenu.writeOpenData(buf, this));
    }

    @Override
    protected void onStayToggled(ServerPlayer owner, boolean stay) {
        if (stay) {
            AnchorManager.anchor(owner, this);
        } else {
            AnchorManager.release(owner);
        }
    }

    /** /kill is the only way to destroy it, so the spot goes too; otherwise it would be re-created there. */
    @Override
    public void remove(RemovalReason reason) {
        UUID ownerId = this.getOwnerUUID();
        MinecraftServer server = this.level().getServer();
        if (reason == RemovalReason.KILLED && ownerId != null && server != null && !this.isRemoved()
                && server.getPlayerList().getPlayer(ownerId) instanceof ServerPlayer owner) {
            AnchorManager.onKilled(owner, this);
        }
        super.remove(reason);
    }
}
