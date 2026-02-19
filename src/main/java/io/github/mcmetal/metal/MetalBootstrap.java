package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;
import io.github.mcmetal.metal.bridge.NativeBridgeHandshake;
import io.github.mcmetal.metal.bridge.NativeBridgeMetadata;
import io.github.mcmetal.metal.bridge.NativeLibraryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class MetalBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetalBootstrap.class);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private MetalBootstrap() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            LOGGER.debug("event=metal_startup phase=skip reason=already_initialized");
            return;
        }

        String osName = System.getProperty("os.name", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        LOGGER.info("event=metal_startup phase=begin os_name={} os_arch={}", osName, osArch);

        if (!HostPlatform.isMacOs()) {
            LOGGER.info("event=metal_startup phase=skip reason=non_macos os_name={}", osName);
            return;
        }

        String expectedBridgeVersion = "unknown";
        try {
            expectedBridgeVersion = NativeBridgeMetadata.expectedBridgeVersion();
            NativeLibraryLoader.load();
            String actualBridgeVersion = NativeBridgeHandshake.validateAgainstNative(expectedBridgeVersion);
            LOGGER.info(
                "event=metal_startup phase=success expected_bridge_version={} actual_bridge_version={}",
                expectedBridgeVersion,
                actualBridgeVersion
            );
        } catch (RuntimeException runtimeException) {
            LOGGER.error(
                "event=metal_startup phase=failure expected_bridge_version={} error_type={} error_message={}",
                expectedBridgeVersion,
                runtimeException.getClass().getSimpleName(),
                runtimeException.getMessage(),
                runtimeException
            );
            throw runtimeException;
        }
    }
}
