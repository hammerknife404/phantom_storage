package com.phantomstorage.entity;

import com.phantomstorage.menu.PhantomAnchorMenu;
import com.phantomstorage.summon.PetKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The summoned anchor: a waypoint that follows like the other pets. Told to stay, it is anchored: it
 * keeps its chunk loaded while its owner is online and stays put through their dimension changes,
 * logouts and restarts ({@code AnchorManager}). The charm takes the owner to it, or swaps them
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

    /** Its panel: where it is, whether it's anchored, stay / follow and dismiss. */
    @Override
    public void interact(ServerPlayer owner, InteractionHand hand) {
        owner.openMenu(new SimpleMenuProvider(
                        (windowId, inventory, p) -> new PhantomAnchorMenu(windowId, inventory, this),
                        this.titleOr("container.phantomstorage.phantom_anchor")),
                buf -> PhantomAnchorMenu.writeOpenData(buf, this));
    }
}
