package com.phantomstorage.menu;

import com.phantomstorage.entity.PhantomAnchorEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.summon.PetKind;
import com.phantomstorage.summon.PetManager;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The anchor's panel, in the same theme as the other pets' screens: where it is and whether it's
 * anchored, a "Stay here" checkbox and a dismiss button. It carries nothing, so there are no slots.
 * The position and dimension come with the open (the pet holds still while its panel is open);
 * whether it's anchored is synced, since the checkbox changes it.
 * Layout: {@link AnchorLayout}.
 */
public class PhantomAnchorMenu extends AbstractContainerMenu {
    public static final int BUTTON_STAY = 0;
    public static final int BUTTON_DISMISS = 1;

    @Nullable
    private final PhantomAnchorEntity anchor;
    private final BlockPos pos;
    private final ResourceLocation dimension;
    private final DataSlot anchored;

    /** Client: what the server wrote with {@link #writeOpenData}. */
    public PhantomAnchorMenu(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        super(ModRegistries.PHANTOM_ANCHOR_MENU.get(), windowId);
        this.anchor = null;
        this.pos = data.readBlockPos();
        this.dimension = data.readResourceLocation();
        this.anchored = this.addDataSlot(DataSlot.standalone());
        this.anchored.set(data.readBoolean() ? 1 : 0);
    }

    /** Server: on the owner's own anchor. */
    public PhantomAnchorMenu(int windowId, Inventory playerInventory, PhantomAnchorEntity anchor) {
        super(ModRegistries.PHANTOM_ANCHOR_MENU.get(), windowId);
        this.anchor = anchor;
        this.pos = anchor.blockPosition();
        this.dimension = anchor.level().dimension().location();
        this.anchored = this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return anchor.isAnchored() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                // The server's value comes from the anchor itself.
            }
        });
        anchor.onMenuOpened();
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, PhantomAnchorEntity anchor) {
        buf.writeBlockPos(anchor.blockPosition());
        buf.writeResourceLocation(anchor.level().dimension().location());
        buf.writeBoolean(anchor.isAnchored());
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public ResourceLocation getDimension() {
        return this.dimension;
    }

    public boolean isAnchored() {
        return this.anchored.get() != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.anchor == null || !(player instanceof ServerPlayer owner)) {
            return false;
        }
        if (id == BUTTON_STAY) {
            this.anchor.toggleStay(owner);
            return true;
        }
        if (id == BUTTON_DISMISS) {
            // Closes this menu first, like the charm's dismiss.
            PetManager.dismiss(PetKind.ANCHOR, owner, false);
            owner.displayClientMessage(Component.translatable(PetKind.ANCHOR.message("dismissed")), true);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.anchor == null || this.anchor.canBeUsedBy(player); // client side: the server decides
    }

    @Override
    public void removed(Player player) {
        if (this.anchor != null) {
            this.anchor.onMenuClosed();
        }
        super.removed(player);
    }
}
