package io.github.mcmetal.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlslToSpirvCompilerTest {
    private static final byte[] VALID_SPIRV =
        new byte[] {(byte) 0x03, (byte) 0x02, (byte) 0x23, (byte) 0x07, 0x00, 0x06, 0x01, 0x00};

    @AfterEach
    void tearDown() {
        GlslToSpirvCompiler.resetProcessExecutorForTests();
    }

    @Test
    void compilesWithConfiguredToolchainAndReturnsSpirvBytes() {
        GlslToSpirvCompiler compiler = new GlslToSpirvCompiler();
        GlslToSpirvCompiler.setProcessExecutorForTests(command -> {
            assertTrue(command.contains("-V"));
            assertTrue(command.contains("spirv1.6"));

            Path outputPath = Path.of(command.get(command.indexOf("-o") + 1));
            Files.write(outputPath, VALID_SPIRV);
            return new GlslToSpirvCompiler.ProcessOutput(0, "", "");
        });

        byte[] output = compiler.compile(
            "rendertype_solid",
            GlslToSpirvCompiler.ShaderStage.VERTEX,
            "#version 150\nvoid main() { gl_Position = vec4(0.0); }"
        );

        assertArrayEquals(VALID_SPIRV, output);
    }

    @Test
    void reportsCompilationFailureWhenCompilerProcessFails() {
        GlslToSpirvCompiler compiler = new GlslToSpirvCompiler();
        GlslToSpirvCompiler.setProcessExecutorForTests(command ->
            new GlslToSpirvCompiler.ProcessOutput(1, "", "syntax error")
        );

        RuntimeException exception = assertThrows(
            GlslToSpirvCompiler.CompilationException.class,
            () -> compiler.compile(
                "broken_shader",
                GlslToSpirvCompiler.ShaderStage.FRAGMENT,
                "broken"
            )
        );

        assertTrue(exception.getMessage().contains("broken_shader"));
        assertTrue(exception.getMessage().contains("syntax error"));
    }

    @Test
    void rejectsOutputsWithoutSpirvMagicHeader() {
        GlslToSpirvCompiler compiler = new GlslToSpirvCompiler();
        GlslToSpirvCompiler.setProcessExecutorForTests(command -> {
            Path outputPath = Path.of(command.get(command.indexOf("-o") + 1));
            Files.write(outputPath, new byte[] {(byte) 0x00, (byte) 0x01});
            return new GlslToSpirvCompiler.ProcessOutput(0, "", "");
        });

        assertThrows(
            GlslToSpirvCompiler.CompilationException.class,
            () -> compiler.compile(
                "invalid_spirv",
                GlslToSpirvCompiler.ShaderStage.VERTEX,
                "#version 150\nvoid main() { gl_Position = vec4(1.0); }"
            )
        );
    }
}
