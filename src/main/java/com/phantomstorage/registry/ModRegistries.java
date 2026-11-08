package com.phantomstorage.registry;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomAnchorEntity;
import com.phantomstorage.entity.PhantomChestEntity;
import com.phantomstorage.entity.PhantomCollectorEntity;
import com.phantomstorage.entity.PhantomGatewayEntity;
import com.phantomstorage.entity.PhantomTankEntity;
import com.phantomstorage.item.PhantomCharmItem;
import com.phantomstorage.menu.PhantomAnchorMenu;
import com.phantomstorage.menu.PhantomChestMenu;
import com.phantomstorage.menu.PhantomCollectorMenu;
import com.phantomstorage.menu.PhantomTankMenu;
import com.phantomstorage.storage.AnchorSpot;
import com.phantomstorage.storage.CollectorSettings;
import com.phantomstorage.storage.PhantomInventory;
import com.phantomstorage.storage.PhantomTanks;
import com.phantomstorage.summon.CharmMode;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(PhantomStorage.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PhantomStorage.MODID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PhantomStorage.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PhantomStorage.MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PhantomStorage.MODID);

    /** What a Phantom Charm does: which pet it summons, or the saddle or gateway. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CharmMode>> CHARM_MODE =
            DATA_COMPONENTS.registerComponentType("charm_mode",
                    builder -> builder.persistent(CharmMode.CODEC).networkSynchronized(CharmMode.STREAM_CODEC));

    public static final DeferredItem<PhantomCharmItem> PHANTOM_CHARM = ITEMS.register("phantom_charm",
            () -> new PhantomCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
                    .component(CHARM_MODE.get(), CharmMode.CHEST)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("phantomstorage",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.phantomstorage"))
                    .icon(() -> new ItemStack(PHANTOM_CHARM.get()))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, output) -> output.accept(PHANTOM_CHARM.get()))
                    .build());

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

    public static final DeferredHolder<EntityType<?>, EntityType<PhantomTankEntity>> PHANTOM_TANK =
            ENTITY_TYPES.register("phantom_tank", () -> EntityType.Builder
                    .<PhantomTankEntity>of(PhantomTankEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("phantom_tank"));

    public static final DeferredHolder<EntityType<?>, EntityType<PhantomAnchorEntity>> PHANTOM_ANCHOR =
            ENTITY_TYPES.register("phantom_anchor", () -> EntityType.Builder
                    .<PhantomAnchorEntity>of(PhantomAnchorEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("phantom_anchor"));

    public static final DeferredHolder<EntityType<?>, EntityType<PhantomCollectorEntity>> PHANTOM_COLLECTOR =
            ENTITY_TYPES.register("phantom_collector", () -> EntityType.Builder
                    .<PhantomCollectorEntity>of(PhantomCollectorEntity::new, MobCategory.MISC)
                    .sized(0.625F, 0.625F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("phantom_collector"));

    /** The charm's gateway mode: a short-lived doorway, never saved; noSummon() like the pets. */
    public static final DeferredHolder<EntityType<?>, EntityType<PhantomGatewayEntity>> PHANTOM_GATEWAY =
            ENTITY_TYPES.register("phantom_gateway", () -> EntityType.Builder
                    .<PhantomGatewayEntity>of(PhantomGatewayEntity::new, MobCategory.MISC)
                    .sized(1.5F, 2.0F)
                    .fireImmune()
                    .noSummon()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("phantom_gateway"));

    public static final DeferredHolder<MenuType<?>, MenuType<PhantomChestMenu>> PHANTOM_CHEST_MENU =
            MENU_TYPES.register("phantom_chest",
                    () -> IMenuTypeExtension.create((windowId, inventory, data) -> new PhantomChestMenu(windowId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<PhantomTankMenu>> PHANTOM_TANK_MENU =
            MENU_TYPES.register("phantom_tank",
                    () -> IMenuTypeExtension.create((windowId, inventory, data) -> new PhantomTankMenu(windowId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<PhantomAnchorMenu>> PHANTOM_ANCHOR_MENU =
            MENU_TYPES.register("phantom_anchor", () -> IMenuTypeExtension.create(PhantomAnchorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<PhantomCollectorMenu>> PHANTOM_COLLECTOR_MENU =
            MENU_TYPES.register("phantom_collector", () -> IMenuTypeExtension.create(PhantomCollectorMenu::new));

    // The storage lives on the player (saved inside the player's own .dat, like the ender chest),
    // never on the entity. Summon/unsummon/dimension change can't lose or duplicate contents.
    public static final Supplier<AttachmentType<PhantomInventory>> STORAGE = ATTACHMENT_TYPES.register("storage",
            () -> AttachmentType.serializable(() -> new PhantomInventory()).copyOnDeath().build());
    public static final Supplier<AttachmentType<PhantomTanks>> TANKS = ATTACHMENT_TYPES.register("tanks",
            () -> AttachmentType.serializable(() -> new PhantomTanks()).copyOnDeath().build());
    /** Where the player's Phantom Anchor is anchored; its entity is re-created from this. */
    public static final Supplier<AttachmentType<AnchorSpot>> ANCHOR = ATTACHMENT_TYPES.register("anchor",
            () -> AttachmentType.serializable(() -> new AnchorSpot()).copyOnDeath().build());
    /** Where the player's Phantom Collector is anchored, likewise. */
    public static final Supplier<AttachmentType<AnchorSpot>> COLLECTOR_SPOT = ATTACHMENT_TYPES.register("collector_spot",
            () -> AttachmentType.serializable(() -> new AnchorSpot()).copyOnDeath().build());
    /** The Phantom Collector's range, filter and banked experience. */
    public static final Supplier<AttachmentType<CollectorSettings>> COLLECTOR = ATTACHMENT_TYPES.register("collector",
            () -> AttachmentType.serializable(() -> new CollectorSettings()).copyOnDeath().build());

    private ModRegistries() {}

    public static void register(IEventBus modBus) {
        // The Phantom Flask (2.4 dev builds) became the charm's tank mode.
        ITEMS.addAlias(PhantomStorage.id("phantom_flask"), PHANTOM_CHARM.getId());
        DATA_COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
