package com.phantomstorage.client;

import com.mojang.blaze3d.platform.Window;
import com.phantomstorage.PhantomStorage;
import com.phantomstorage.item.PhantomCharmItem;
import com.phantomstorage.menu.PhantomLayout;
import com.phantomstorage.network.CharmModePayload;
import com.phantomstorage.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = PhantomStorage.MODID, dist = Dist.CLIENT)
public final class PhantomStorageClient {
    public PhantomStorageClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, PhantomClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(PhantomStorageClient::onRegisterRenderers);
        modBus.addListener(PhantomStorageClient::onRegisterScreens);
        modBus.addListener(PhantomStorageClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(PhantomStorageClient::onScroll);
        PhantomLayout.setClientSelector(() -> {
            Window window = Minecraft.getInstance().getWindow();
            return PhantomLayout.choose(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        });
    }

    /** The charm's model swaps to the tank art in tank mode (overrides in models/item/phantom_charm.json). */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModRegistries.PHANTOM_CHARM.get(), PhantomStorage.id("mode"),
                (stack, level, entity, seed) -> PhantomCharmItem.getMode(stack).ordinal()));
    }

    /** Sneak + scroll with the charm in the main hand cycles its mode instead of the hotbar. */
    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        double delta = event.getScrollDeltaY();
        if (player != null && delta != 0.0 && player.isShiftKeyDown()
                && player.getMainHandItem().getItem() instanceof PhantomCharmItem) {
            PacketDistributor.sendToServer(new CharmModePayload(delta < 0.0));
            event.setCanceled(true);
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.PHANTOM_CHEST.get(), PhantomChestRenderer::new);
        event.registerEntityRenderer(ModRegistries.PHANTOM_TANK.get(), PhantomTankRenderer::new);
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.PHANTOM_CHEST_MENU.get(), PhantomChestScreen::new);
        event.register(ModRegistries.PHANTOM_TANK_MENU.get(), PhantomTankScreen::new);
    }
}
