package io.github.mcmetal.metal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Disk cache for phase-4 shader transpilation artifacts.
 */
public class ShaderDiskCache {
    record CachedShaderArtifacts(byte[] spirvBinary, String mslSource, ShaderBindingMap bindingMap) {
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final String CACHE_DIR_PROPERTY = "mcmetal.phase4.cacheDir";
    private static final String CACHE_NAMESPACE = "shader-pipeline-v1";
    private static final Path DEFAULT_CACHE_DIR = Path.of(System.getProperty("user.home"), ".mcmetal", "cache");

    @Nullable
    CachedShaderArtifacts load(String shaderName, String shaderStage, String glslSource) {
        Path entryDirectory = entryDirectory(shaderName, shaderStage, glslSource);
        if (!Files.isDirectory(entryDirectory)) {
            return null;
        }

        try {
            byte[] spirvBinary = Files.readAllBytes(entryDirectory.resolve("shader.spv"));
            String mslSource = Files.readString(entryDirectory.resolve("shader.msl"), StandardCharsets.UTF_8);
            String reflectionJson = Files.readString(entryDirectory.resolve("reflection.json"), StandardCharsets.UTF_8);
            ShaderBindingMap bindingMap = GSON.fromJson(reflectionJson, ShaderBindingMap.class);
            if (bindingMap == null) {
                return null;
            }
            return new CachedShaderArtifacts(spirvBinary, mslSource, bindingMap);
        } catch (IOException ignored) {
            return null;
        }
    }

    void store(
        String shaderName,
        String shaderStage,
        String glslSource,
        byte[] spirvBinary,
        String mslSource,
        ShaderBindingMap bindingMap
    ) {
        Path entryDirectory = entryDirectory(shaderName, shaderStage, glslSource);
        Path tempDirectory = entryDirectory.resolveSibling(entryDirectory.getFileName() + ".tmp");
        try {
            Files.createDirectories(tempDirectory);
            Files.write(tempDirectory.resolve("shader.spv"), spirvBinary);
            Files.writeString(tempDirectory.resolve("shader.msl"), mslSource, StandardCharsets.UTF_8);
            Files.writeString(tempDirectory.resolve("reflection.json"), GSON.toJson(bindingMap), StandardCharsets.UTF_8);
            Files.move(tempDirectory, entryDirectory, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            // Best-effort caching; runtime shader path still succeeds without cache writes.
        } finally {
            try {
                if (Files.isDirectory(tempDirectory)) {
                    Files.walk(tempDirectory)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignoredDelete) {
                                // Best-effort cleanup.
                            }
                        });
                }
            } catch (IOException ignoredCleanup) {
                // Best-effort cleanup.
            }
        }
    }

    private static Path entryDirectory(String shaderName, String shaderStage, String glslSource) {
        Path baseDirectory = Path.of(System.getProperty(CACHE_DIR_PROPERTY, DEFAULT_CACHE_DIR.toString()));
        String cacheKey = sanitize(shaderName) + "__" + sanitize(shaderStage) + "__" + sha256(glslSource);
        return baseDirectory.resolve(CACHE_NAMESPACE).resolve(cacheKey);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }
}
