package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.NativeStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetalShaderProgramBridgeTest {
    private final RecordingBackend backend = new RecordingBackend();

    @BeforeEach
    void setUp() {
        MetalShaderProgramBridge.resetForTests();
        MetalShaderProgramBridge.setNativeShaderBackendForTests(backend);
    }

    @AfterEach
    void tearDown() {
        MetalShaderProgramBridge.resetForTests();
    }

    @Test
    void createsAndCompilesProgramWhenBothStagesAreReady() {
        MetalShaderProgramBridge.onTranslatedStage("rendertype_solid", "VERTEX", "vertex float4 main0() { return 0; }");
        MetalShaderProgramBridge.onTranslatedStage("rendertype_solid", "FRAGMENT", "fragment float4 main0() { return 0; }");

        assertEquals(1, backend.createCalls);
        assertEquals(1, backend.compileCalls);
    }

    @Test
    void closesAndDestroysNativeProgramHandle() {
        MetalShaderProgramBridge.onTranslatedStage("rendertype_cutout", "VERTEX", "vertex float4 main0() { return 0; }");
        MetalShaderProgramBridge.onTranslatedStage("rendertype_cutout", "FRAGMENT", "fragment float4 main0() { return 0; }");

        MetalShaderProgramBridge.onProgramClosed("rendertype_cutout");

        assertEquals(1, backend.destroyCalls);
    }

    @Test
    void registersUniformsAndUpdatesByStableHandle() {
        MetalShaderProgramBridge.onReflectionBindingMap(
            "rendertype_entity",
            new ShaderBindingMap(
                java.util.List.of(new ShaderBindingMap.UniformBinding("ColorModulator", 0, 0)),
                java.util.List.of(),
                java.util.List.of()
            )
        );
        MetalShaderProgramBridge.onTranslatedStage("rendertype_entity", "VERTEX", "vertex float4 main0() { return 0; }");
        MetalShaderProgramBridge.onTranslatedStage("rendertype_entity", "FRAGMENT", "fragment float4 main0() { return 0; }");

        MetalShaderProgramBridge.setUniformFloat4("rendertype_entity", "ColorModulator", 1.0F, 0.5F, 0.25F, 1.0F);
        MetalShaderProgramBridge.setUniformFloat4("rendertype_entity", "ColorModulator", 0.9F, 0.4F, 0.2F, 1.0F);

        assertEquals(1, backend.registerUniformCalls);
        assertEquals(2, backend.updateUniformFloat4Calls);
    }

    private static final class RecordingBackend implements MetalShaderProgramBridge.NativeShaderBackend {
        private long nextHandle = 1L;
        private int createCalls;
        private int compileCalls;
        private int registerUniformCalls;
        private int updateUniformFloat4Calls;
        private int destroyCalls;

        @Override
        public long createShaderProgram(String programName, String vertexMslSource, String fragmentMslSource) {
            createCalls++;
            return nextHandle++;
        }

        @Override
        public int compileShaderPipeline(long programHandle, long vertexDescriptorHandle) {
            compileCalls++;
            return NativeStatus.OK;
        }

        @Override
        public long registerUniform(long programHandle, String uniformName, int set, int binding) {
            registerUniformCalls++;
            return nextHandle++;
        }

        @Override
        public int updateUniformFloat4(long uniformHandle, float x, float y, float z, float w) {
            updateUniformFloat4Calls++;
            return NativeStatus.OK;
        }

        @Override
        public int destroyShaderProgram(long programHandle) {
            destroyCalls++;
            return NativeStatus.OK;
        }
    }
}
