package com.phantomstorage.registry;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomChestEntity;
import com.phantomstorage.item.PhantomCharmItem;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.storage.PhantomInventory;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistries {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PhantomStorage.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PhantomStorage.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PhantomStorage.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PhantomStorage.MODID);

    public static final DeferredItem<PhantomCharmItem> PHANTOM_CHARM = ITEMS.register("phantom_charm",
            () -> new PhantomCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // noSummon(): the chest can only come into existence through the charm, never /summon or spawners.
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomChestEntity>> PHANTOM_CHEST =
            ENTITY_TYPES.register("phantom_chest", () -> EntityType.Builder
                    .<PhantomChestEntity>of(PhantomChestEntity::new, MobCategory.MISC)
                    .sized(0.875F, 0.875F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("phantom_chest"));

    public static final DeferredHolder<MenuType<?>, MenuType<PhantomChestMenu>> PHANTOM_CHEST_MENU =
            MENU_TYPES.register("phantom_chest",
                    () -> IMenuTypeExtension.create((windowId, inventory, data) -> new PhantomChestMenu(windowId, inventory)));

    // The storage lives on the player (saved inside the player's own .dat, like the ender chest),
    // never on the entity. Summon/unsummon/dimension change can't lose or duplicate contents.
    public static final Supplier<AttachmentType<PhantomInventory>> STORAGE = ATTACHMENT_TYPES.register("storage",
            () -> AttachmentType.serializable(() -> new PhantomInventory()).copyOnDeath().build());

    private ModRegistries() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
    }
}
