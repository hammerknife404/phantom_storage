package com.phantomstorage.menu;

import com.phantomstorage.entity.PhantomCollectorEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.storage.CollectorSettings;
import com.phantomstorage.storage.ItemFilter;
import com.phantomstorage.summon.PetKind;
import com.phantomstorage.summon.PetManager;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The collector's panel, in the shared theme: its filter (ghost entries, take or skip), where it is,
 * its range, the experience it has banked, stay / follow and dismiss, over the player inventory (to
 * pick filter items from). Settings live on the owner ({@link CollectorSettings}); the position and
 * dimension come with the open (the pet holds still while its panel is open). Layout:
 * {@link CollectorLayout}.
 */
public class PhantomCollectorMenu extends AbstractContainerMenu {
    public static final int BUTTON_RANGE_DOWN = 0;
    public static final int BUTTON_RANGE_UP = 1;
    public static final int BUTTON_SKIP = 2;
    public static final int BUTTON_TAKE_XP = 3;
    public static final int BUTTON_STAY = 4;
    public static final int BUTTON_DISMISS = 5;

    public static final int FILTER_START = 0;
    public static final int FILTER_END = FILTER_START + ItemFilter.SIZE;
    public static final int INV_START = FILTER_END;
    public static final int INV_END = INV_START + 27;
    public static final int HOTBAR_END = INV_END + 9;

    @Nullable
    private final PhantomCollectorEntity collector;
    @Nullable
    private final CollectorSettings settings;
    private final BlockPos pos;
    private final ResourceLocation dimension;
    private final DataSlot range;
    private final DataSlot skip;
    private final DataSlot anchored;
    /** Data slots carry 16 bits: the bank goes as two halves. */
    private final DataSlot xpLow;
    private final DataSlot xpHigh;

    /** Client: what the server wrote with {@link #writeOpenData}; the rest arrives by sync. */
    public PhantomCollectorMenu(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(windowId, playerInventory, null, null, data.readBlockPos(), data.readResourceLocation());
    }

    /** Server: on the owner's own collector. */
    public PhantomCollectorMenu(int windowId, Inventory playerInventory, PhantomCollectorEntity collector, CollectorSettings settings) {
        this(windowId, playerInventory, collector, settings, collector.blockPosition(), collector.level().dimension().location());
    }

    private PhantomCollectorMenu(int windowId, Inventory playerInventory, @Nullable PhantomCollectorEntity collector,
                                 @Nullable CollectorSettings settings, BlockPos pos, ResourceLocation dimension) {
        super(ModRegistries.PHANTOM_COLLECTOR_MENU.get(), windowId);
        this.collector = collector;
        this.settings = settings;
        this.pos = pos;
        this.dimension = dimension;
        if (settings != null && collector != null) {
            this.range = this.addDataSlot(readOnly(settings::range));
            this.skip = this.addDataSlot(readOnly(() -> settings.isSkip() ? 1 : 0));
            this.anchored = this.addDataSlot(readOnly(() -> collector.isAnchored() ? 1 : 0));
            this.xpLow = this.addDataSlot(readOnly(() -> settings.bankedXp() & 0xFFFF));
            this.xpHigh = this.addDataSlot(readOnly(() -> settings.bankedXp() >>> 16));
            collector.onMenuOpened();
        } else {
            this.range = this.addDataSlot(DataSlot.standalone());
            this.skip = this.addDataSlot(DataSlot.standalone());
            this.anchored = this.addDataSlot(DataSlot.standalone());
            this.xpLow = this.addDataSlot(DataSlot.standalone());
            this.xpHigh = this.addDataSlot(DataSlot.standalone());
            this.range.set(CollectorSettings.DEFAULT_RANGE);
        }

        Container filter = settings != null ? settings.filter() : new ItemFilter();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new GhostSlot(filter, col + row * 3,
                        CollectorLayout.FILTER_X + col * 18, CollectorLayout.FILTER_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        CollectorLayout.INV_X + col * 18, CollectorLayout.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, CollectorLayout.INV_X + col * 18, CollectorLayout.HOTBAR_Y));
        }
    }

    private static DataSlot readOnly(java.util.function.IntSupplier value) {
        return new DataSlot() {
            @Override
            public int get() {
                return value.getAsInt();
            }

            @Override
            public void set(int v) {
                // The server's value comes from the settings and the pet.
            }
        };
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf, PhantomCollectorEntity collector) {
        buf.writeBlockPos(collector.blockPosition());
        buf.writeResourceLocation(collector.level().dimension().location());
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public ResourceLocation getDimension() {
        return this.dimension;
    }

    public int getRange() {
        return this.range.get();
    }

    public boolean isSkip() {
        return this.skip.get() != 0;
    }

    public boolean isAnchored() {
        return this.anchored.get() != 0;
    }

    public int getBankedXp() {
        return (this.xpLow.get() & 0xFFFF) | (this.xpHigh.get() & 0xFFFF) << 16;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.collector == null || this.settings == null || !(player instanceof ServerPlayer owner)) {
            return false;
        }
        switch (id) {
            case BUTTON_RANGE_DOWN -> this.settings.adjustRange(-1);
            case BUTTON_RANGE_UP -> this.settings.adjustRange(1);
            case BUTTON_SKIP -> this.settings.toggleSkip();
            case BUTTON_TAKE_XP -> {
                int xp = this.settings.takeXp();
                if (xp > 0) {
                    owner.giveExperiencePoints(xp);
                    owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 0.9F + owner.getRandom().nextFloat() * 0.3F);
                }
            }
            case BUTTON_STAY -> this.collector.toggleStay(owner);
            case BUTTON_DISMISS -> {
                // Closes this menu first, like the charm's dismiss.
                PetManager.dismiss(PetKind.COLLECTOR, owner, false);
                owner.displayClientMessage(Component.translatable(PetKind.COLLECTOR.message("dismissed")), true);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Filter entries are ghosts: set from the cursor or cleared, never real items moving. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!GhostSlot.click(this, slotId, clickType)) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    /** Shift-click swaps between the inventory and the hotbar (the filter only takes ghosts). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < INV_START) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < INV_END
                ? this.moveItemStackTo(stack, INV_END, HOTBAR_END, false)
                : this.moveItemStackTo(stack, INV_START, INV_END, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.collector == null || this.collector.canBeUsedBy(player); // client side: the server decides
    }

    @Override
    public void removed(Player player) {
        if (this.collector != null) {
            this.collector.onMenuClosed();
        }
        super.removed(player);
    }
}
