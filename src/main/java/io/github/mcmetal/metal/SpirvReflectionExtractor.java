package io.github.mcmetal.metal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extracts uniform/texture/sampler bindings from SPIR-V via spirv-cross reflection JSON.
 */
public class SpirvReflectionExtractor {
    @FunctionalInterface
    interface ProcessExecutor {
        ProcessOutput run(List<String> command) throws IOException, InterruptedException;
    }

    record ProcessOutput(int exitCode, String stdout, String stderr) {
    }

    static final class ReflectionException extends RuntimeException {
        ReflectionException(String message) {
            super(message);
        }
    }

    private static final String TOOL_PATH_PROPERTY = "mcmetal.phase4.spirvcross.path";
    private static final String DEFAULT_TOOL_PATH = "spirv-cross";
    private static volatile ProcessExecutor processExecutor = SpirvReflectionExtractor::runProcess;

    ShaderBindingMap extract(String shaderName, byte[] spirvBinary) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("mcmetal-reflect");
            Path spirvPath = tempDir.resolve(shaderName + ".spv");
            Files.write(spirvPath, spirvBinary);

            List<String> command = new ArrayList<>();
            command.add(System.getProperty(TOOL_PATH_PROPERTY, DEFAULT_TOOL_PATH));
            command.add(spirvPath.toString());
            command.add("--reflect");

            ProcessOutput output = processExecutor.run(command);
            if (output.exitCode != 0) {
                throw new ReflectionException(
                    "SPIR-V reflection failed for '" + shaderName + "' (exitCode="
                        + output.exitCode + "): " + output.stderr
                );
            }

            return parseReflectionJson(output.stdout);
        } catch (IOException e) {
            throw new ReflectionException("Failed to reflect shader '" + shaderName + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReflectionException("Interrupted while reflecting shader '" + shaderName + "'.");
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    static void setProcessExecutorForTests(ProcessExecutor executor) {
        processExecutor = executor;
    }

    static void resetProcessExecutorForTests() {
        processExecutor = SpirvReflectionExtractor::runProcess;
    }

    private static ShaderBindingMap parseReflectionJson(String reflectionJson) {
        JsonObject root = JsonParser.parseString(reflectionJson).getAsJsonObject();
        List<ShaderBindingMap.UniformBinding> uniforms = readBindings(root, "ubos", ShaderBindingMap.UniformBinding::new);
        List<ShaderBindingMap.TextureBinding> textures = readBindings(root, "separate_images", ShaderBindingMap.TextureBinding::new);
        List<ShaderBindingMap.SamplerBinding> samplers = readBindings(root, "separate_samplers", ShaderBindingMap.SamplerBinding::new);
        return new ShaderBindingMap(List.copyOf(uniforms), List.copyOf(textures), List.copyOf(samplers));
    }

    private interface BindingFactory<T> {
        T create(String name, int set, int binding);
    }

    private static <T> List<T> readBindings(JsonObject root, String key, BindingFactory<T> factory) {
        JsonArray resources = root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
        List<T> bindings = new ArrayList<>(resources.size());
        for (JsonElement resourceElement : resources) {
            if (!resourceElement.isJsonObject()) {
                continue;
            }
            JsonObject resource = resourceElement.getAsJsonObject();
            String name = resource.has("name") ? resource.get("name").getAsString() : "";
            int set = resource.has("set") ? resource.get("set").getAsInt() : 0;
            int binding = resource.has("binding") ? resource.get("binding").getAsInt() : -1;
            bindings.add(factory.create(name, set, binding));
        }
        return bindings;
    }

    private static ProcessOutput runProcess(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();
        return new ProcessOutput(exitCode, stdout, stderr);
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort temporary directory cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best-effort temporary directory cleanup.
        }
    }
}
