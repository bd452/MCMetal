package io.github.mcmetal;

import io.github.mcmetal.metal.MetalBootstrap;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MCMetalMod implements ModInitializer {
    public static final String MOD_ID = "mcmetal";
    private static final Logger LOGGER = LoggerFactory.getLogger(MCMetalMod.class);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}", MOD_ID);
        MetalBootstrap.initialize();
    }
}
