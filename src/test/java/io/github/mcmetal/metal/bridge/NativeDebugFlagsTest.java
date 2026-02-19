package io.github.mcmetal.metal.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeDebugFlagsTest {
    private static final String VALIDATION_KEY = "mcmetal.debug.validation";
    private static final String LABELS_KEY = "mcmetal.debug.labels";

    private String originalValidationValue;
    private String originalLabelsValue;

    @BeforeEach
    void captureSystemProperties() {
        originalValidationValue = System.getProperty(VALIDATION_KEY);
        originalLabelsValue = System.getProperty(LABELS_KEY);
    }

    @AfterEach
    void restoreSystemProperties() {
        restoreProperty(VALIDATION_KEY, originalValidationValue);
        restoreProperty(LABELS_KEY, originalLabelsValue);
    }

    @Test
    void returnsNoFlagsByDefault() {
        System.clearProperty(VALIDATION_KEY);
        System.clearProperty(LABELS_KEY);

        assertEquals(0, NativeDebugFlags.fromSystemProperties());
    }

    @Test
    void returnsCombinedFlagsWhenEnabled() {
        System.setProperty(VALIDATION_KEY, "true");
        System.setProperty(LABELS_KEY, "true");

        int expectedFlags = NativeDebugFlags.VALIDATION | NativeDebugFlags.LABELS;
        assertEquals(expectedFlags, NativeDebugFlags.fromSystemProperties());
    }

    @Test
    void returnsOnlyValidationFlagWhenLabelsDisabled() {
        System.setProperty(VALIDATION_KEY, "true");
        System.setProperty(LABELS_KEY, "false");

        assertEquals(NativeDebugFlags.VALIDATION, NativeDebugFlags.fromSystemProperties());
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
