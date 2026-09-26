package com.phantomstorage;

import com.mojang.logging.LogUtils;
import com.phantomstorage.entity.PhantomChestEntity;
import com.phantomstorage.registry.ModRegistries;
import com.phantomstorage.summon.PhantomChestEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(PhantomStorage.MODID)
public final class PhantomStorage {
    public static final String MODID = "phantomstorage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PhantomStorage(IEventBus modBus) {
        ModRegistries.register(modBus);
        modBus.addListener(PhantomStorage::onEntityAttributes);
        modBus.addListener(PhantomStorage::onCreativeTabs);
        PhantomChestEvents.register(NeoForge.EVENT_BUS);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistries.PHANTOM_CHEST.get(), PhantomChestEntity.createAttributes().build());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistries.PHANTOM_CHARM.get());
        }
    }
}
