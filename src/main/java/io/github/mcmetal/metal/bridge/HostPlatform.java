package io.github.mcmetal.metal.bridge;

import java.util.Locale;

public final class HostPlatform {
    private static final String OS_NAME = System.getProperty("os.name", "unknown");

    private HostPlatform() {
    }

    public static boolean isMacOs() {
        return OS_NAME.toLowerCase(Locale.ROOT).contains("mac");
    }
}
