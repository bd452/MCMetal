package io.github.mcmetal.metal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Translates SPIR-V binaries to MSL source via spirv-cross.
 */
public class SpirvToMslTranslator {
    @FunctionalInterface
    interface ProcessExecutor {
        ProcessOutput run(List<String> command) throws IOException, InterruptedException;
    }

    record ProcessOutput(int exitCode, String stdout, String stderr) {
    }

    static final class TranslationException extends RuntimeException {
        TranslationException(String message) {
            super(message);
        }
    }

    private static final String TOOL_PATH_PROPERTY = "mcmetal.phase4.spirvcross.path";
    private static final String DEFAULT_TOOL_PATH = "spirv-cross";
    private static volatile ProcessExecutor processExecutor = SpirvToMslTranslator::runProcess;

    String translate(String shaderName, byte[] spirvBinary) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("mcmetal-msl");
            Path spirvPath = tempDir.resolve(shaderName + ".spv");
            Path mslPath = tempDir.resolve(shaderName + ".metal");
            Files.write(spirvPath, spirvBinary);

            List<String> command = new ArrayList<>();
            command.add(System.getProperty(TOOL_PATH_PROPERTY, DEFAULT_TOOL_PATH));
            command.add(spirvPath.toString());
            command.add("--msl");
            command.add("--output");
            command.add(mslPath.toString());

            ProcessOutput output = processExecutor.run(command);
            if (output.exitCode != 0) {
                throw new TranslationException(
                    "SPIR-V->MSL translation failed for '" + shaderName + "' (exitCode="
                        + output.exitCode + "): " + output.stderr
                );
            }

            if (!Files.exists(mslPath)) {
                throw new TranslationException(
                    "spirv-cross reported success but produced no MSL output for '" + shaderName + "'."
                );
            }

            String mslSource = Files.readString(mslPath, StandardCharsets.UTF_8);
            if (mslSource.isBlank()) {
                throw new TranslationException(
                    "spirv-cross produced an empty MSL shader for '" + shaderName + "'."
                );
            }
            return mslSource;
        } catch (IOException e) {
            throw new TranslationException("Failed to translate shader '" + shaderName + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranslationException("Interrupted while translating shader '" + shaderName + "'.");
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
        processExecutor = SpirvToMslTranslator::runProcess;
    }

    private static ProcessOutput runProcess(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
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
