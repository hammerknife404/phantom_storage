package com.phantomstorage.compat.jei;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Optional JEI compat. JEI discovers this class via {@link JeiPlugin}; nothing in the mod references
 * it, so it is never loaded when JEI is absent. The transfer itself is executed and validated by
 * JEI's own server-side handler.
 */
@JeiPlugin
public final class PhantomStorageJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = PhantomStorage.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CraftingTransferInfo());
    }

    private static final class CraftingTransferInfo
            implements IRecipeTransferInfo<PhantomChestMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends PhantomChestMenu> getContainerClass() {
            return PhantomChestMenu.class;
        }

        @Override
        public Optional<MenuType<PhantomChestMenu>> getMenuType() {
            return Optional.of(ModRegistries.PHANTOM_CHEST_MENU.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public boolean canHandle(PhantomChestMenu container, RecipeHolder<CraftingRecipe> recipe) {
            return true;
        }

        @Override
        public List<Slot> getRecipeSlots(PhantomChestMenu container, RecipeHolder<CraftingRecipe> recipe) {
            return container.slots.subList(PhantomChestMenu.CRAFT_START, PhantomChestMenu.CRAFT_END);
        }

        /** Pull ingredients from the phantom storage as well as the player's inventory. */
        @Override
        public List<Slot> getInventorySlots(PhantomChestMenu container, RecipeHolder<CraftingRecipe> recipe) {
            List<Slot> slots = new ArrayList<>(container.slots.subList(PhantomChestMenu.STORAGE_START, PhantomChestMenu.STORAGE_END));
            slots.addAll(container.slots.subList(PhantomChestMenu.INV_START, PhantomChestMenu.HOTBAR_END));
            return slots;
        }
    }
}
