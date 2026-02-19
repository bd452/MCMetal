package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.NativeApi;
import io.github.mcmetal.metal.bridge.NativeBridgeException;
import io.github.mcmetal.metal.bridge.NativeStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 4 shader program bridge for native program creation and pipeline compilation.
 */
public final class MetalShaderProgramBridge {
    interface NativeShaderBackend {
        long createShaderProgram(String programName, String vertexMslSource, String fragmentMslSource);

        int compileShaderPipeline(long programHandle, long vertexDescriptorHandle);

        long registerUniform(long programHandle, String uniformName, int set, int binding);

        int updateUniformFloat4(long uniformHandle, float x, float y, float z, float w);

        int destroyShaderProgram(long programHandle);
    }

    private static final class PendingUniform {
        private final String name;
        private final int set;
        private final int binding;

        private PendingUniform(String name, int set, int binding) {
            this.name = name;
            this.set = set;
            this.binding = binding;
        }
    }

    private static final class ProgramRecord {
        @Nullable
        private String vertexMslSource;
        @Nullable
        private String fragmentMslSource;
        private long nativeProgramHandle;
        private final Map<String, Long> uniformHandlesByName = new HashMap<>();
        private final Map<String, PendingUniform> pendingUniformsByName = new HashMap<>();
    }

    private static final Map<String, ProgramRecord> PROGRAMS = new ConcurrentHashMap<>();
    private static volatile NativeShaderBackend nativeShaderBackend = new JniNativeShaderBackend();

    private MetalShaderProgramBridge() {
    }

    public static void onTranslatedStage(String shaderName, String shaderStage, String mslSource) {
        ProgramRecord record = PROGRAMS.computeIfAbsent(shaderName, ignored -> new ProgramRecord());
        if ("VERTEX".equals(shaderStage)) {
            record.vertexMslSource = mslSource;
        } else if ("FRAGMENT".equals(shaderStage)) {
            record.fragmentMslSource = mslSource;
        } else {
            return;
        }

        if (record.vertexMslSource == null || record.fragmentMslSource == null || record.nativeProgramHandle != 0L) {
            return;
        }

        long programHandle = nativeShaderBackend.createShaderProgram(
            shaderName,
            record.vertexMslSource,
            record.fragmentMslSource
        );
        if (programHandle <= 0L) {
            throw new NativeBridgeException("Native shader program creation failed for '" + shaderName + "'.");
        }

        int compileStatus = nativeShaderBackend.compileShaderPipeline(programHandle, 0L);
        if (!NativeStatus.isSuccess(compileStatus)) {
            throw new NativeBridgeException(
                "Native shader pipeline compilation failed for '" + shaderName + "' with status "
                    + NativeStatus.describe(compileStatus) + " (" + compileStatus + ")."
            );
        }

        record.nativeProgramHandle = programHandle;
        registerPendingUniforms(shaderName, record);
    }

    public static void onReflectionBindingMap(String shaderName, ShaderBindingMap bindingMap) {
        ProgramRecord record = PROGRAMS.computeIfAbsent(shaderName, ignored -> new ProgramRecord());
        for (ShaderBindingMap.UniformBinding uniformBinding : bindingMap.uniforms()) {
            record.pendingUniformsByName.put(
                uniformBinding.name(),
                new PendingUniform(uniformBinding.name(), uniformBinding.set(), uniformBinding.binding())
            );
        }

        if (record.nativeProgramHandle != 0L) {
            registerPendingUniforms(shaderName, record);
        }
    }

    public static void setUniformFloat4(String shaderName, String uniformName, float x, float y, float z, float w) {
        ProgramRecord record = PROGRAMS.get(shaderName);
        if (record == null) {
            return;
        }

        Long uniformHandle = record.uniformHandlesByName.get(uniformName);
        if (uniformHandle == null || uniformHandle == 0L) {
            PendingUniform pendingUniform = record.pendingUniformsByName.get(uniformName);
            if (pendingUniform != null && record.nativeProgramHandle != 0L) {
                uniformHandle = registerUniform(shaderName, record, pendingUniform);
            } else {
                return;
            }
        }

        int status = nativeShaderBackend.updateUniformFloat4(uniformHandle, x, y, z, w);
        if (!NativeStatus.isSuccess(status)) {
            throw new NativeBridgeException(
                "Native uniform update failed for '" + shaderName + ":" + uniformName + "' with status "
                    + NativeStatus.describe(status) + " (" + status + ")."
            );
        }
    }

    public static void onProgramClosed(String shaderName) {
        ProgramRecord record = PROGRAMS.remove(shaderName);
        if (record == null || record.nativeProgramHandle == 0L) {
            return;
        }

        int destroyStatus = nativeShaderBackend.destroyShaderProgram(record.nativeProgramHandle);
        if (!NativeStatus.isSuccess(destroyStatus)) {
            throw new NativeBridgeException(
                "Native shader program destruction failed for '" + shaderName + "' with status "
                    + NativeStatus.describe(destroyStatus) + " (" + destroyStatus + ")."
            );
        }
    }

    static void setNativeShaderBackendForTests(NativeShaderBackend backend) {
        nativeShaderBackend = backend;
    }

    static void resetForTests() {
        PROGRAMS.clear();
        nativeShaderBackend = new JniNativeShaderBackend();
    }

    private static final class JniNativeShaderBackend implements NativeShaderBackend {
        @Override
        public long createShaderProgram(String programName, String vertexMslSource, String fragmentMslSource) {
            return NativeApi.nativeCreateShaderProgram(programName, vertexMslSource, fragmentMslSource);
        }

        @Override
        public int compileShaderPipeline(long programHandle, long vertexDescriptorHandle) {
            return NativeApi.nativeCompileShaderPipeline(programHandle, vertexDescriptorHandle);
        }

        @Override
        public long registerUniform(long programHandle, String uniformName, int set, int binding) {
            return NativeApi.nativeRegisterUniform(programHandle, uniformName, set, binding);
        }

        @Override
        public int updateUniformFloat4(long uniformHandle, float x, float y, float z, float w) {
            return NativeApi.nativeUpdateUniformFloat4(uniformHandle, x, y, z, w);
        }

        @Override
        public int destroyShaderProgram(long programHandle) {
            return NativeApi.nativeDestroyShaderProgram(programHandle);
        }
    }

    private static void registerPendingUniforms(String shaderName, ProgramRecord record) {
        if (record.nativeProgramHandle == 0L || record.pendingUniformsByName.isEmpty()) {
            return;
        }

        for (PendingUniform pendingUniform : record.pendingUniformsByName.values()) {
            registerUniform(shaderName, record, pendingUniform);
        }
    }

    private static long registerUniform(String shaderName, ProgramRecord record, PendingUniform pendingUniform) {
        Long existingHandle = record.uniformHandlesByName.get(pendingUniform.name);
        if (existingHandle != null && existingHandle > 0L) {
            return existingHandle;
        }

        long uniformHandle = nativeShaderBackend.registerUniform(
            record.nativeProgramHandle,
            pendingUniform.name,
            pendingUniform.set,
            pendingUniform.binding
        );
        if (uniformHandle <= 0L) {
            throw new NativeBridgeException(
                "Native uniform registration failed for '" + shaderName + ":" + pendingUniform.name + "'."
            );
        }

        record.uniformHandlesByName.put(pendingUniform.name, uniformHandle);
        return uniformHandle;
    }
}
