package io.github.mcmetal.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpirvToMslTranslatorTest {
    @AfterEach
    void tearDown() {
        SpirvToMslTranslator.resetProcessExecutorForTests();
    }

    @Test
    void translatesSpirvIntoMslUsingConfiguredTool() {
        SpirvToMslTranslator translator = new SpirvToMslTranslator();
        SpirvToMslTranslator.setProcessExecutorForTests(command -> {
            assertTrue(command.contains("--msl"));
            Path outputPath = Path.of(command.get(command.indexOf("--output") + 1));
            Files.writeString(outputPath, "fragment float4 main0() { return float4(1.0); }");
            return new SpirvToMslTranslator.ProcessOutput(0, "", "");
        });

        String msl = translator.translate(
            "rendertype_solid",
            new byte[] {(byte) 0x03, (byte) 0x02, (byte) 0x23, (byte) 0x07, 0x00, 0x06, 0x01, 0x00}
        );

        assertTrue(msl.contains("fragment"));
    }

    @Test
    void failsWhenTranslatorProcessFails() {
        SpirvToMslTranslator translator = new SpirvToMslTranslator();
        SpirvToMslTranslator.setProcessExecutorForTests(command ->
            new SpirvToMslTranslator.ProcessOutput(1, "", "translation error")
        );

        RuntimeException exception = assertThrows(
            SpirvToMslTranslator.TranslationException.class,
            () -> translator.translate("broken_shader", new byte[] {1, 2, 3, 4})
        );

        assertTrue(exception.getMessage().contains("translation error"));
    }

    @Test
    void failsWhenTranslatorReturnsBlankMsl() {
        SpirvToMslTranslator translator = new SpirvToMslTranslator();
        SpirvToMslTranslator.setProcessExecutorForTests(command -> {
            Path outputPath = Path.of(command.get(command.indexOf("--output") + 1));
            Files.writeString(outputPath, " ");
            return new SpirvToMslTranslator.ProcessOutput(0, "", "");
        });

        RuntimeException exception = assertThrows(
            SpirvToMslTranslator.TranslationException.class,
            () -> translator.translate("empty_shader", new byte[] {1, 2, 3, 4})
        );

        assertTrue(exception.getMessage().contains("empty"));
    }
}
