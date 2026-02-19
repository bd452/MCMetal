package io.github.mcmetal.metal;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compiles GLSL shader source into SPIR-V binaries using glslangValidator.
 */
public class GlslToSpirvCompiler {
    @FunctionalInterface
    interface ProcessExecutor {
        ProcessOutput run(List<String> command) throws IOException, InterruptedException;
    }

    record ProcessOutput(int exitCode, String stdout, String stderr) {
    }

    enum ShaderStage {
        VERTEX("vert"),
        FRAGMENT("frag");

        private final String glslangStage;

        ShaderStage(String glslangStage) {
            this.glslangStage = glslangStage;
        }

        String glslangStage() {
            return this.glslangStage;
        }

        static @Nullable ShaderStage fromName(String stageName) {
            return switch (stageName.toUpperCase()) {
                case "VERTEX" -> VERTEX;
                case "FRAGMENT" -> FRAGMENT;
                default -> null;
            };
        }
    }

    static final class CompilationException extends RuntimeException {
        CompilationException(String message) {
            super(message);
        }
    }

    private static final String TOOL_PATH_PROPERTY = "mcmetal.phase4.glslang.path";
    private static final String DEFAULT_TOOL_PATH = "glslangValidator";
    private static final int SPIRV_VERSION = 0x00010600;

    private static volatile ProcessExecutor processExecutor = GlslToSpirvCompiler::runProcess;

    byte[] compile(String shaderName, ShaderStage stage, String glslSource) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("mcmetal-spirv");
            Path sourcePath = tempDir.resolve(shaderName + "." + stage.glslangStage() + ".glsl");
            Path spirvPath = tempDir.resolve(shaderName + "." + stage.glslangStage() + ".spv");
            Files.writeString(sourcePath, glslSource, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(System.getProperty(TOOL_PATH_PROPERTY, DEFAULT_TOOL_PATH));
            command.add("-V");
            command.add("--target-env");
            command.add("spirv1.6");
            command.add("-S");
            command.add(stage.glslangStage());
            command.add("-o");
            command.add(spirvPath.toString());
            command.add(sourcePath.toString());

            ProcessOutput output = processExecutor.run(command);
            if (output.exitCode != 0) {
                throw new CompilationException(
                    "GLSL->SPIR-V compilation failed for '" + shaderName + "' (stage=" + stage
                        + ", exitCode=" + output.exitCode + "): " + output.stderr
                );
            }

            if (!Files.exists(spirvPath)) {
                throw new CompilationException(
                    "glslangValidator reported success but produced no SPIR-V output for '" + shaderName + "'."
                );
            }

            byte[] spirvBinary = Files.readAllBytes(spirvPath);
            if (!hasSpirvMagicHeader(spirvBinary)) {
                throw new CompilationException(
                    "Produced output is not a valid SPIR-V module for '" + shaderName + "'."
                );
            }
            return spirvBinary;
        } catch (IOException e) {
            throw new CompilationException("Failed to compile shader '" + shaderName + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompilationException("Interrupted while compiling shader '" + shaderName + "'.");
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
        processExecutor = GlslToSpirvCompiler::runProcess;
    }

    private static ProcessOutput runProcess(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ProcessOutput(exitCode, stdout, stderr);
    }

    private static boolean hasSpirvMagicHeader(byte[] bytes) {
        if (bytes.length < 8) {
            return false;
        }
        int magic = ((bytes[3] & 0xFF) << 24) | ((bytes[2] & 0xFF) << 16) | ((bytes[1] & 0xFF) << 8) | (bytes[0] & 0xFF);
        int version = ((bytes[7] & 0xFF) << 24) | ((bytes[6] & 0xFF) << 16) | ((bytes[5] & 0xFF) << 8) | (bytes[4] & 0xFF);
        return magic == 0x07230203 && version <= SPIRV_VERSION;
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
