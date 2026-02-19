package io.github.mcmetal.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class VanillaShaderCorpusValidationTest {
    private static final byte[] VALID_SPIRV =
        new byte[] {(byte) 0x03, (byte) 0x02, (byte) 0x23, (byte) 0x07, 0x00, 0x06, 0x01, 0x00};

    @BeforeEach
    void setUp() {
        MetalShaderLifecycleBridge.resetForTests();
        MetalShaderProgramBridge.resetForTests();
        MetalShaderLifecycleBridge.setBridgeActiveForTests(true);
        MetalShaderLifecycleBridge.setSpirvCompilerForTests(new DeterministicCompiler());
        MetalShaderLifecycleBridge.setReflectionExtractorForTests(new DeterministicReflectionExtractor());
        MetalShaderLifecycleBridge.setMslTranslatorForTests(new DeterministicTranslator());
        MetalShaderLifecycleBridge.setShaderDiskCacheForTests(new EmptyDiskCache());
        MetalShaderProgramBridge.setNativeShaderBackendForTests(new NoopShaderBackend());
    }

    @AfterEach
    void tearDown() {
        MetalShaderLifecycleBridge.resetForTests();
        MetalShaderProgramBridge.resetForTests();
    }

    @Test
    void validatesRepresentativeVanillaShaderCorpus() {
        List<String> shaderNames = List.of(
            "rendertype_solid",
            "rendertype_cutout",
            "rendertype_cutout_mipped",
            "rendertype_translucent",
            "rendertype_entity_solid",
            "rendertype_entity_cutout",
            "rendertype_entity_translucent",
            "rendertype_lines",
            "position_color",
            "position_tex_color"
        );

        for (String shaderName : shaderNames) {
            MetalShaderLifecycleBridge.onShaderStageCompileSource(
                shaderName,
                "VERTEX",
                new ByteArrayInputStream(sampleVertexGlsl(shaderName).getBytes(StandardCharsets.UTF_8))
            );
            MetalShaderLifecycleBridge.onShaderStageCompileSource(
                shaderName,
                "FRAGMENT",
                new ByteArrayInputStream(sampleFragmentGlsl(shaderName).getBytes(StandardCharsets.UTF_8))
            );

            ShaderBindingMap bindingMap = MetalShaderLifecycleBridge.getReflectionBindingMapForTests(shaderName);
            assertNotNull(bindingMap);
            assertNotNull(MetalShaderLifecycleBridge.getShaderDiagnosticsForTests(shaderName, "VERTEX"));
            assertNotNull(MetalShaderLifecycleBridge.getShaderDiagnosticsForTests(shaderName, "FRAGMENT"));
        }
    }

    private static String sampleVertexGlsl(String shaderName) {
        return "#version 150\n"
            + "// " + shaderName + " vertex\n"
            + "in vec3 Position;\n"
            + "uniform mat4 ModelViewMat;\n"
            + "void main() {\n"
            + "  gl_Position = ModelViewMat * vec4(Position, 1.0);\n"
            + "}\n";
    }

    private static String sampleFragmentGlsl(String shaderName) {
        return "#version 150\n"
            + "// " + shaderName + " fragment\n"
            + "uniform vec4 ColorModulator;\n"
            + "out vec4 fragColor;\n"
            + "void main() {\n"
            + "  fragColor = ColorModulator;\n"
            + "}\n";
    }

    private static final class DeterministicCompiler extends GlslToSpirvCompiler {
        @Override
        byte[] compile(String shaderName, ShaderStage stage, String glslSource) {
            return VALID_SPIRV;
        }
    }

    private static final class DeterministicReflectionExtractor extends SpirvReflectionExtractor {
        @Override
        ShaderBindingMap extract(String shaderName, byte[] spirvBinary) {
            return new ShaderBindingMap(
                List.of(new ShaderBindingMap.UniformBinding("ColorModulator", 0, 0)),
                List.of(),
                List.of()
            );
        }
    }

    private static final class DeterministicTranslator extends SpirvToMslTranslator {
        @Override
        String translate(String shaderName, byte[] spirvBinary) {
            return "vertex float4 main0() { return float4(0.0); }";
        }
    }

    private static final class EmptyDiskCache extends ShaderDiskCache {
        @Override
        CachedShaderArtifacts load(String shaderName, String shaderStage, String glslSource) {
            return null;
        }
    }

    private static final class NoopShaderBackend implements MetalShaderProgramBridge.NativeShaderBackend {
        private long nextHandle = 1L;

        @Override
        public long createShaderProgram(String programName, String vertexMslSource, String fragmentMslSource) {
            return nextHandle++;
        }

        @Override
        public int compileShaderPipeline(long programHandle, long vertexDescriptorHandle) {
            return 0;
        }

        @Override
        public long registerUniform(long programHandle, String uniformName, int set, int binding) {
            return nextHandle++;
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
