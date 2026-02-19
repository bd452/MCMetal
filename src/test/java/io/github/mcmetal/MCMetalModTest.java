package io.github.mcmetal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MCMetalModTest {
    @Test
    void modIdIsStable() {
        assertEquals("mcmetal", MCMetalMod.MOD_ID);
    }
}
