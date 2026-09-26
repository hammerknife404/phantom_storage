package com.phantomstorage.compat.rei;

import com.phantomstorage.client.PhantomChestScreen;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomLayout;
import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.client.registry.transfer.simple.SimpleTransferHandler;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Optional REI compat, discovered by REI via {@link REIPluginClient}; never loaded without REI.
 * Mirrors the JEI plugin: "+" on a crafting recipe fills the grid (from storage and inventory),
 * and the screen's "+" icon opens REI's crafting recipes. REI's server side performs the move.
 */
@REIPluginClient
public final class PhantomStorageReiPlugin implements REIClientPlugin {
    /** Vanilla crafting category id (from REI's default plugin; referenced by id to avoid depending on it). */
    private static final CategoryIdentifier<Display> CRAFTING = CategoryIdentifier.of("minecraft", "plugins/crafting");

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerContainerClickArea((PhantomChestScreen screen) -> {
            PhantomLayout layout = screen.getMenu().getLayout();
            return new Rectangle(layout.recipesX(), layout.recipesY(), PhantomLayout.ICON_SIZE, PhantomLayout.ICON_SIZE);
        }, PhantomChestScreen.class, CRAFTING);
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(new SimpleTransferHandler() {
            @Override
            public ApplicabilityResult checkApplicable(Context context) {
                boolean applicable = context.getMenu() instanceof PhantomChestMenu
                        && CRAFTING.equals(context.getDisplay().getCategoryIdentifier())
                        && context.getContainerScreen() != null;
                return applicable ? ApplicabilityResult.createApplicable() : ApplicabilityResult.createNotApplicable();
            }

            @Override
            public Iterable<SlotAccessor> getInputSlots(Context context) {
                return slots(context.getMenu(), PhantomChestMenu.CRAFT_START, PhantomChestMenu.CRAFT_END);
            }

            /** Ingredients come from the phantom storage as well as the player's inventory. */
            @Override
            public Iterable<SlotAccessor> getInventorySlots(Context context) {
                List<SlotAccessor> slots = slots(context.getMenu(), PhantomChestMenu.STORAGE_START, PhantomChestMenu.STORAGE_END);
                slots.addAll(slots(context.getMenu(), PhantomChestMenu.INV_START, PhantomChestMenu.HOTBAR_END));
                return slots;
            }
        });
    }

    private static List<SlotAccessor> slots(AbstractContainerMenu menu, int start, int end) {
        List<SlotAccessor> slots = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            slots.add(SlotAccessor.fromSlot(menu.getSlot(i)));
        }
        return slots;
    }
}
