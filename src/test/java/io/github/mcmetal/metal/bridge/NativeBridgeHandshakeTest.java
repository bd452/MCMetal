package io.github.mcmetal.metal.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeBridgeHandshakeTest {
    @Test
    void acceptsMatchingVersion() {
        assertDoesNotThrow(() -> NativeBridgeHandshake.validateVersion("0.1.0", "0.1.0"));
    }

    @Test
    void rejectsMismatchedVersion() {
        assertThrows(
            NativeBridgeException.class,
            () -> NativeBridgeHandshake.validateVersion("0.1.0", "0.2.0")
        );
    }

    @Test
    void rejectsBlankVersionValues() {
        assertThrows(
            NativeBridgeException.class,
            () -> NativeBridgeHandshake.validateVersion(" ", "0.1.0")
        );
        assertThrows(
            NativeBridgeException.class,
            () -> NativeBridgeHandshake.validateVersion("0.1.0", " ")
        );
    }
}
