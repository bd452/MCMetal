package io.github.mcmetal.metal.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NativeBridgeMetadataTest {
    @Test
    void expectedVersionIsPresent() {
        String expectedVersion = NativeBridgeMetadata.expectedBridgeVersion();
        assertFalse(expectedVersion.isBlank());
        assertFalse(expectedVersion.contains("${"));
    }
}
