package io.github.mcmetal;

import io.github.mcmetal.metal.MetalPhaseOneBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class MCMetalClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(MetalPhaseOneBridge::onClientStarted);
        ClientTickEvents.END_CLIENT_TICK.register(MetalPhaseOneBridge::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(MetalPhaseOneBridge::onClientStopping);
    }
}
