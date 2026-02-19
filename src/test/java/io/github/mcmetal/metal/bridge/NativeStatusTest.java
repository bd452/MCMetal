package io.github.mcmetal.metal.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStatusTest {
    @Test
    void describeReturnsStableKnownLabels() {
        assertEquals("OK", NativeStatus.describe(NativeStatus.OK));
        assertEquals("ALREADY_INITIALIZED", NativeStatus.describe(NativeStatus.ALREADY_INITIALIZED));
        assertEquals("INVALID_ARGUMENT", NativeStatus.describe(NativeStatus.INVALID_ARGUMENT));
        assertEquals("INITIALIZATION_FAILED", NativeStatus.describe(NativeStatus.INITIALIZATION_FAILED));
    }

    @Test
    void describeFallsBackToUnknown() {
        assertEquals("UNKNOWN", NativeStatus.describe(-999));
    }

    @Test
    void successIncludesAlreadyInitialized() {
        assertTrue(NativeStatus.isSuccess(NativeStatus.OK));
        assertTrue(NativeStatus.isSuccess(NativeStatus.ALREADY_INITIALIZED));
        assertFalse(NativeStatus.isSuccess(NativeStatus.INVALID_ARGUMENT));
        assertFalse(NativeStatus.isSuccess(NativeStatus.INITIALIZATION_FAILED));
    }
}
