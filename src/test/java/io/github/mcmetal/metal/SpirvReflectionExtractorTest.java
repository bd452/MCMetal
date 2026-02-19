package io.github.mcmetal.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpirvReflectionExtractorTest {
    @AfterEach
    void tearDown() {
        SpirvReflectionExtractor.resetProcessExecutorForTests();
    }

    @Test
    void parsesUniformTextureAndSamplerBindings() {
        SpirvReflectionExtractor extractor = new SpirvReflectionExtractor();
        SpirvReflectionExtractor.setProcessExecutorForTests(command ->
            new SpirvReflectionExtractor.ProcessOutput(
                0,
                """
                {
                  "ubos": [{"name":"Globals","set":0,"binding":0}],
                  "separate_images": [{"name":"DiffuseSampler","set":0,"binding":1}],
                  "separate_samplers": [{"name":"LinearClamp","set":0,"binding":2}]
                }
                """,
                ""
            )
        );

        ShaderBindingMap bindingMap = extractor.extract("rendertype_solid", new byte[] {1, 2, 3, 4});

        assertEquals(1, bindingMap.uniforms().size());
        assertEquals("Globals", bindingMap.uniforms().get(0).name());
        assertEquals(1, bindingMap.textures().size());
        assertEquals("DiffuseSampler", bindingMap.textures().get(0).name());
        assertEquals(1, bindingMap.samplers().size());
        assertEquals("LinearClamp", bindingMap.samplers().get(0).name());
    }

    @Test
    void reportsFailureWhenReflectionCommandFails() {
        SpirvReflectionExtractor extractor = new SpirvReflectionExtractor();
        SpirvReflectionExtractor.setProcessExecutorForTests(command ->
            new SpirvReflectionExtractor.ProcessOutput(1, "", "reflection error")
        );

        RuntimeException exception = assertThrows(
            SpirvReflectionExtractor.ReflectionException.class,
            () -> extractor.extract("broken_shader", new byte[] {1, 2, 3, 4})
        );
        assertTrue(exception.getMessage().contains("reflection error"));
    }
}
