package com.phantomstorage.client;

import com.mojang.blaze3d.platform.Window;
import com.phantomstorage.PhantomStorage;
import com.phantomstorage.menu.PhantomLayout;
import com.phantomstorage.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = PhantomStorage.MODID, dist = Dist.CLIENT)
public final class PhantomStorageClient {
    public PhantomStorageClient(IEventBus modBus) {
        modBus.addListener(PhantomStorageClient::onRegisterRenderers);
        modBus.addListener(PhantomStorageClient::onRegisterScreens);
        PhantomLayout.setClientSelector(() -> {
            Window window = Minecraft.getInstance().getWindow();
            return PhantomLayout.choose(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.PHANTOM_CHEST.get(), PhantomChestRenderer::new);
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.PHANTOM_CHEST_MENU.get(), PhantomChestScreen::new);
    }
}
