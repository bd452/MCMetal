package io.github.mcmetal.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShaderDiskCacheTest {
    private Path tempCacheDir;

    @AfterEach
    void tearDown() throws Exception {
        if (tempCacheDir != null) {
            try (var walk = Files.walk(tempCacheDir)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Best-effort cleanup.
                    }
                });
            }
        }
        System.clearProperty("mcmetal.phase4.cacheDir");
    }

    @Test
    void storesAndLoadsShaderArtifactsByStableContentHash() throws Exception {
        tempCacheDir = Files.createTempDirectory("mcmetal-cache-test");
        System.setProperty("mcmetal.phase4.cacheDir", tempCacheDir.toString());
        ShaderDiskCache cache = new ShaderDiskCache();
        ShaderBindingMap bindingMap = new ShaderBindingMap(
            java.util.List.of(new ShaderBindingMap.UniformBinding("Globals", 0, 0)),
            java.util.List.of(),
            java.util.List.of()
        );

        cache.store(
            "rendertype_solid",
            "VERTEX",
            "#version 150\nvoid main(){}",
            new byte[] {1, 2, 3, 4},
            "vertex float4 main0() { return 0; }",
            bindingMap
        );

        ShaderDiskCache.CachedShaderArtifacts cached = cache.load(
            "rendertype_solid",
            "VERTEX",
            "#version 150\nvoid main(){}"
        );

        assertNotNull(cached);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, cached.spirvBinary());
        assertEquals("vertex float4 main0() { return 0; }", cached.mslSource());
        assertEquals("Globals", cached.bindingMap().uniforms().get(0).name());
    }
}
