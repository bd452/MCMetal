package io.github.mcmetal.metal.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class NativeBridgeMetadata {
    private static final String RESOURCE_PATH = "/mcmetal-native.properties";
    private static final String VERSION_KEY = "nativeBridgeVersion";
    private static final String EXPECTED_BRIDGE_VERSION = loadExpectedBridgeVersion();

    private NativeBridgeMetadata() {
    }

    public static String expectedBridgeVersion() {
        return EXPECTED_BRIDGE_VERSION;
    }

    private static String loadExpectedBridgeVersion() {
        Properties properties = new Properties();
        try (InputStream inputStream = NativeBridgeMetadata.class.getResourceAsStream(RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new NativeBridgeException("Missing native bridge metadata resource: " + RESOURCE_PATH);
            }
            properties.load(inputStream);
        } catch (IOException ioException) {
            throw new NativeBridgeException("Failed to read native bridge metadata from " + RESOURCE_PATH, ioException);
        }

        String version = properties.getProperty(VERSION_KEY);
        if (version == null || version.isBlank()) {
            throw new NativeBridgeException("Missing required metadata key '" + VERSION_KEY + "' in " + RESOURCE_PATH);
        }
        return version.trim();
    }
}
