package io.github.mcmetal.metal.bridge;

/**
 * Bit flags consumed by the native Metal bring-up path.
 */
public final class NativeDebugFlags {
    public static final int VALIDATION = 1 << 0;
    public static final int LABELS = 1 << 1;

    private NativeDebugFlags() {
    }

    public static int fromSystemProperties() {
        int flags = 0;
        if (Boolean.getBoolean("mcmetal.debug.validation")) {
            flags |= VALIDATION;
        }
        if (Boolean.getBoolean("mcmetal.debug.labels")) {
            flags |= LABELS;
        }
        return flags;
    }
}
