package io.github.mcmetal.metal.bridge;

public final class NativeBridgeHandshake {
    private NativeBridgeHandshake() {
    }

    public static String validateAgainstNative(String expectedVersion) {
        return validateVersion(expectedVersion, NativeApi.nativeGetBridgeVersion());
    }

    public static String validateVersion(String expectedVersion, String actualVersion) {
        String expected = normalize(expectedVersion, "expected");
        String actual = normalize(actualVersion, "actual");
        if (!expected.equals(actual)) {
            throw new NativeBridgeException(
                "Native bridge version mismatch: expected " + expected + " but found " + actual
            );
        }
        return actual;
    }

    private static String normalize(String value, String fieldName) {
        if (value == null) {
            throw new NativeBridgeException("Native bridge handshake failed: " + fieldName + " version is null.");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new NativeBridgeException("Native bridge handshake failed: " + fieldName + " version is blank.");
        }
        return normalized;
    }
}
