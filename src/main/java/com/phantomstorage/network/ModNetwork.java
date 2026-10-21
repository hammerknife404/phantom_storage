package com.phantomstorage.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** All custom payloads. Bump the version whenever one changes shape. */
public final class ModNetwork {
    private static final String VERSION = "4";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(TankSyncPayload.TYPE, TankSyncPayload.STREAM_CODEC, TankSyncPayload::handle);
        registrar.playToServer(CharmModePayload.TYPE, CharmModePayload.STREAM_CODEC, CharmModePayload::handle);
    }
}
