package io.github.mcmetal.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetalShaderLifecycleBridgeTest {
    private final List<String> events = new ArrayList<>();
    private final List<Long> sequenceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MetalShaderLifecycleBridge.resetForTests();
        MetalShaderProgramBridge.resetForTests();
        MetalShaderProgramBridge.setNativeShaderBackendForTests(new NoopShaderBackend());
        MetalShaderLifecycleBridge.setShaderDiskCacheForTests(new RecordingDiskCache());
        MetalShaderLifecycleBridge.setBridgeActiveForTests(true);
        MetalShaderLifecycleBridge.setEventSinkForTests((event, shaderName, shaderStage, sequenceId) -> {
            events.add(event + "|" + shaderName + "|" + shaderStage);
            sequenceIds.add(sequenceId);
        });
    }

    @AfterEach
    void tearDown() {
        MetalShaderLifecycleBridge.resetForTests();
        MetalShaderProgramBridge.resetForTests();
    }

    @Test
    void recordsProgramLifecycleInOrder() {
        MetalShaderLifecycleBridge.onShaderProgramLoadStart("rendertype_solid");
        MetalShaderLifecycleBridge.onShaderProgramLoadComplete("rendertype_solid");
        MetalShaderLifecycleBridge.onShaderProgramClose("rendertype_solid");

        assertEquals(
            List.of(
                "shader_program_load_start|rendertype_solid|program",
                "shader_program_load_complete|rendertype_solid|program",
                "shader_program_close|rendertype_solid|program"
            ),
            events
        );
        assertStrictlyIncreasing(sequenceIds);
    }

    @Test
    void recordsStageCompileAndReleaseEvents() {
        MetalShaderLifecycleBridge.onShaderStageCompileStart("rendertype_cutout", "VERTEX");
        MetalShaderLifecycleBridge.onShaderStageCompileComplete("rendertype_cutout", "VERTEX");
        MetalShaderLifecycleBridge.onShaderStageRelease("rendertype_cutout", "VERTEX");

        assertEquals(
            List.of(
                "shader_stage_compile_start|rendertype_cutout|VERTEX",
                "shader_stage_compile_complete|rendertype_cutout|VERTEX",
                "shader_stage_release|rendertype_cutout|VERTEX"
            ),
            events
        );
        assertStrictlyIncreasing(sequenceIds);
    }

    @Test
    void doesNotEmitEventsWhenBridgeIsInactive() {
        MetalShaderLifecycleBridge.setBridgeActiveForTests(false);

        MetalShaderLifecycleBridge.onShaderProgramLoadStart("rendertype_translucent");
        MetalShaderLifecycleBridge.onShaderStageCompileStart("rendertype_translucent", "FRAGMENT");

        assertTrue(events.isEmpty());
        assertTrue(sequenceIds.isEmpty());
    }

    @Test
    void compilesSpirvFromStageSourceWithoutConsumingStream() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingReflectionExtractor reflectionExtractor = new RecordingReflectionExtractor();
        RecordingTranslator translator = new RecordingTranslator();
        MetalShaderLifecycleBridge.setSpirvCompilerForTests(compiler);
        MetalShaderLifecycleBridge.setReflectionExtractorForTests(reflectionExtractor);
        MetalShaderLifecycleBridge.setMslTranslatorForTests(translator);
        ByteArrayInputStream stream = new ByteArrayInputStream(
            "#version 150\nvoid main() { gl_Position = vec4(0.0); }".getBytes(StandardCharsets.UTF_8)
        );

        MetalShaderLifecycleBridge.onShaderStageCompileSource("rendertype_solid", "VERTEX", stream);

        assertEquals("rendertype_solid", compiler.lastShaderName);
        assertEquals(GlslToSpirvCompiler.ShaderStage.VERTEX, compiler.lastStage);
        assertTrue(compiler.lastSource.contains("gl_Position"));
        assertEquals("rendertype_solid", reflectionExtractor.lastShaderName);
        ShaderBindingMap cachedBindingMap = MetalShaderLifecycleBridge.getReflectionBindingMapForTests("rendertype_solid");
        assertNotNull(cachedBindingMap);
        assertEquals(1, cachedBindingMap.uniforms().size());
        assertEquals("rendertype_solid", translator.lastShaderName);
        assertTrue(translator.lastSpirvBinary.length > 0);
        MetalShaderLifecycleBridge.ShaderDiagnosticsRecord diagnostics =
            MetalShaderLifecycleBridge.getShaderDiagnosticsForTests("rendertype_solid", "VERTEX");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.glslInput().contains("gl_Position"));
        assertTrue(diagnostics.translatedMsl().contains("main0"));

        String replay = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(replay.contains("gl_Position"));
        assertTrue(events.contains("shader_stage_spirv_compile_complete|rendertype_solid|VERTEX"));
        assertTrue(events.contains("shader_stage_reflection_complete|rendertype_solid|VERTEX"));
        assertTrue(events.contains("shader_stage_msl_translate_complete|rendertype_solid|VERTEX"));
    }

    @Test
    void skipsSpirvCompileWhenShaderStageIsNotSupported() {
        MetalShaderLifecycleBridge.onShaderStageCompileSource(
            "rendertype_weather",
            "GEOMETRY",
            new ByteArrayInputStream("void main() {}".getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(events.contains("shader_stage_spirv_compile_skipped|rendertype_weather|GEOMETRY"));
    }

    @Test
    void emitsTranslationFailureEventWhenMslTranslationFails() {
        MetalShaderLifecycleBridge.setSpirvCompilerForTests(new RecordingCompiler());
        MetalShaderLifecycleBridge.setReflectionExtractorForTests(new RecordingReflectionExtractor());
        MetalShaderLifecycleBridge.setMslTranslatorForTests(new FailingTranslator());

        MetalShaderLifecycleBridge.onShaderStageCompileSource(
            "rendertype_lines",
            "FRAGMENT",
            new ByteArrayInputStream("void main() {}".getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(events.contains("shader_stage_msl_translate_failed|rendertype_lines|FRAGMENT"));
        MetalShaderLifecycleBridge.ShaderDiagnosticsRecord diagnostics =
            MetalShaderLifecycleBridge.getShaderDiagnosticsForTests("rendertype_lines", "FRAGMENT");
        assertNotNull(diagnostics);
        assertNotNull(diagnostics.lastError());
    }

    @Test
    void emitsReflectionFailureEventWhenReflectionFails() {
        MetalShaderLifecycleBridge.setSpirvCompilerForTests(new RecordingCompiler());
        MetalShaderLifecycleBridge.setReflectionExtractorForTests(new FailingReflectionExtractor());

        MetalShaderLifecycleBridge.onShaderStageCompileSource(
            "rendertype_armor",
            "VERTEX",
            new ByteArrayInputStream("void main() {}".getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(events.contains("shader_stage_reflection_failed|rendertype_armor|VERTEX"));
    }

    @Test
    void usesDiskCacheWhenArtifactsAreAvailable() {
        RecordingCompiler compiler = new RecordingCompiler();
        RecordingDiskCache diskCache = new RecordingDiskCache();
        diskCache.cachedArtifacts = new ShaderDiskCache.CachedShaderArtifacts(
            new byte[] {(byte) 0x03, (byte) 0x02, (byte) 0x23, (byte) 0x07, 0x00, 0x06, 0x01, 0x00},
            "vertex float4 main0() { return 0; }",
            new ShaderBindingMap(
                List.of(new ShaderBindingMap.UniformBinding("Globals", 0, 0)),
                List.of(),
                List.of()
            )
        );
        MetalShaderLifecycleBridge.setSpirvCompilerForTests(compiler);
        MetalShaderLifecycleBridge.setShaderDiskCacheForTests(diskCache);

        MetalShaderLifecycleBridge.onShaderStageCompileSource(
            "rendertype_cached",
            "VERTEX",
            new ByteArrayInputStream("void main() {}".getBytes(StandardCharsets.UTF_8))
        );

        assertTrue(events.contains("shader_stage_disk_cache_hit|rendertype_cached|VERTEX"));
        assertNull(compiler.lastShaderName);
    }

    private static void assertStrictlyIncreasing(List<Long> values) {
        for (int i = 1; i < values.size(); i++) {
            long previous = values.get(i - 1);
            long current = values.get(i);
            assertTrue(current > previous, "Expected strictly increasing sequence IDs");
        }
    }

    private static final class RecordingCompiler extends GlslToSpirvCompiler {
        private String lastShaderName;
        private ShaderStage lastStage;
        private String lastSource;

        @Override
        byte[] compile(String shaderName, ShaderStage stage, String glslSource) {
            this.lastShaderName = shaderName;
            this.lastStage = stage;
            this.lastSource = glslSource;
            return new byte[] {(byte) 0x03, (byte) 0x02, (byte) 0x23, (byte) 0x07, 0x00, 0x06, 0x01, 0x00};
        }
    }

    private static final class RecordingTranslator extends SpirvToMslTranslator {
        private String lastShaderName;
        private byte[] lastSpirvBinary;

        @Override
        String translate(String shaderName, byte[] spirvBinary) {
            this.lastShaderName = shaderName;
            this.lastSpirvBinary = spirvBinary;
            return "fragment float4 main0() { return float4(1.0); }";
        }
    }

    private static final class FailingTranslator extends SpirvToMslTranslator {
        @Override
        String translate(String shaderName, byte[] spirvBinary) {
            throw new SpirvToMslTranslator.TranslationException("test failure");
        }
    }

    private static final class RecordingReflectionExtractor extends SpirvReflectionExtractor {
        private String lastShaderName;

        @Override
        ShaderBindingMap extract(String shaderName, byte[] spirvBinary) {
            this.lastShaderName = shaderName;
            return new ShaderBindingMap(
                List.of(new ShaderBindingMap.UniformBinding("Globals", 0, 0)),
                List.of(new ShaderBindingMap.TextureBinding("Diffuse", 0, 1)),
                List.of(new ShaderBindingMap.SamplerBinding("Linear", 0, 2))
            );
        }
    }

    private static final class FailingReflectionExtractor extends SpirvReflectionExtractor {
        @Override
        ShaderBindingMap extract(String shaderName, byte[] spirvBinary) {
            throw new SpirvReflectionExtractor.ReflectionException("reflection failure");
        }
    }

    private static final class RecordingDiskCache extends ShaderDiskCache {
        private CachedShaderArtifacts cachedArtifacts;

        @Override
        CachedShaderArtifacts load(String shaderName, String shaderStage, String glslSource) {
            return cachedArtifacts;
        }

        @Override
        void store(
            String shaderName,
            String shaderStage,
            String glslSource,
            byte[] spirvBinary,
            String mslSource,
            ShaderBindingMap bindingMap
        ) {
        }
    }

    private static final class NoopShaderBackend implements MetalShaderProgramBridge.NativeShaderBackend {
        @Override
        public long createShaderProgram(String programName, String vertexMslSource, String fragmentMslSource) {
            return 1L;
        }

        @Override
        public int compileShaderPipeline(long programHandle, long vertexDescriptorHandle) {
            return 0;
        }

        @Override
        public long registerUniform(long programHandle, String uniformName, int set, int binding) {
            return 1L;
        }

        @Override
        public int updateUniformFloat4(long uniformHandle, float x, float y, float z, float w) {
            return 0;
        }

        @Override
        public int destroyShaderProgram(long programHandle) {
            return 0;
        }
    }
}
