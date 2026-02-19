package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 4 bridge for ShaderProgram / ShaderStage lifecycle interception.
 */
public final class MetalShaderLifecycleBridge {
    interface ShaderLifecycleEventSink {
        void onEvent(String event, String shaderName, String shaderStage, long sequenceId);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MetalShaderLifecycleBridge.class);
    private static final boolean DEBUG_SHADER_LOGS = Boolean.getBoolean("mcmetal.phase4.debugShaderLifecycle");
    private static final boolean SPIRV_COMPILATION_ENABLED = !Boolean.getBoolean("mcmetal.phase4.disableSpirvCompilation");
    private static final boolean SPIRV_REFLECTION_ENABLED = !Boolean.getBoolean("mcmetal.phase4.disableSpirvReflection");
    private static final boolean MSL_TRANSLATION_ENABLED = !Boolean.getBoolean("mcmetal.phase4.disableMslTranslation");
    private static final boolean DISK_CACHE_ENABLED = !Boolean.getBoolean("mcmetal.phase4.disableShaderDiskCache");
    private static final int SHADER_INPUT_MARK_LIMIT_BYTES = 512 * 1024;
    private static final AtomicLong EVENT_SEQUENCE = new AtomicLong(1L);

    private static volatile @Nullable ShaderLifecycleEventSink eventSink;
    private static volatile @Nullable Boolean bridgeActiveOverrideForTests;
    private static volatile GlslToSpirvCompiler spirvCompiler = new GlslToSpirvCompiler();
    private static volatile SpirvToMslTranslator mslTranslator = new SpirvToMslTranslator();
    private static volatile SpirvReflectionExtractor reflectionExtractor = new SpirvReflectionExtractor();
    private static volatile ShaderDiskCache shaderDiskCache = new ShaderDiskCache();
    private static final Map<String, ShaderBindingMap> REFLECTION_BINDING_MAPS = new ConcurrentHashMap<>();
    private static final Map<String, ShaderDiagnosticsRecord> SHADER_DIAGNOSTICS = new ConcurrentHashMap<>();

    record ShaderDiagnosticsRecord(
        String shaderName,
        String shaderStage,
        String glslInput,
        @Nullable String translatedMsl,
        @Nullable String lastError
    ) {
    }

    private MetalShaderLifecycleBridge() {
    }

    public static void onShaderProgramLoadStart(String shaderName) {
        emit("shader_program_load_start", shaderName, "program");
    }

    public static void onShaderProgramLoadComplete(String shaderName) {
        emit("shader_program_load_complete", shaderName, "program");
    }

    public static void onShaderProgramClose(String shaderName) {
        MetalShaderProgramBridge.onProgramClosed(shaderName);
        emit("shader_program_close", shaderName, "program");
    }

    public static void onShaderStageCompileStart(String shaderName, String shaderStage) {
        emit("shader_stage_compile_start", shaderName, shaderStage);
    }

    public static void onShaderStageCompileSource(String shaderName, String shaderStage, InputStream shaderSourceStream) {
        if (!isBridgeActive() || !SPIRV_COMPILATION_ENABLED) {
            return;
        }

        GlslToSpirvCompiler.ShaderStage stage = GlslToSpirvCompiler.ShaderStage.fromName(shaderStage);
        if (stage == null) {
            emit("shader_stage_spirv_compile_skipped", shaderName, shaderStage);
            return;
        }

        if (!shaderSourceStream.markSupported()) {
            emit("shader_stage_spirv_compile_skipped", shaderName, shaderStage);
            return;
        }

        try {
            shaderSourceStream.mark(SHADER_INPUT_MARK_LIMIT_BYTES);
            String glslSource = new String(shaderSourceStream.readAllBytes(), StandardCharsets.UTF_8);
            shaderSourceStream.reset();
            SHADER_DIAGNOSTICS.put(
                shaderName + ":" + shaderStage,
                new ShaderDiagnosticsRecord(shaderName, shaderStage, glslSource, null, null)
            );
            emit("shader_stage_diagnostics_glsl_captured", shaderName, shaderStage);

            ShaderDiskCache.CachedShaderArtifacts cachedArtifacts = DISK_CACHE_ENABLED
                ? shaderDiskCache.load(shaderName, shaderStage, glslSource)
                : null;

            byte[] spirvBinary;
            ShaderBindingMap bindingMap = null;
            String mslSource = null;
            if (cachedArtifacts != null) {
                spirvBinary = cachedArtifacts.spirvBinary();
                bindingMap = cachedArtifacts.bindingMap();
                mslSource = cachedArtifacts.mslSource();
                emit("shader_stage_disk_cache_hit", shaderName, shaderStage);
            } else {
                spirvBinary = spirvCompiler.compile(shaderName, stage, glslSource);
                emit("shader_stage_spirv_compile_complete", shaderName, shaderStage);

                if (SPIRV_REFLECTION_ENABLED) {
                    bindingMap = reflectionExtractor.extract(shaderName, spirvBinary);
                    emit("shader_stage_reflection_complete", shaderName, shaderStage);
                }

                if (MSL_TRANSLATION_ENABLED) {
                    mslSource = mslTranslator.translate(shaderName, spirvBinary);
                    emit("shader_stage_msl_translate_complete", shaderName, shaderStage);
                }

                if (DISK_CACHE_ENABLED && bindingMap != null && mslSource != null) {
                    shaderDiskCache.store(shaderName, shaderStage, glslSource, spirvBinary, mslSource, bindingMap);
                }
            }

            if (bindingMap != null) {
                REFLECTION_BINDING_MAPS.put(shaderName, bindingMap);
                MetalShaderProgramBridge.onReflectionBindingMap(shaderName, bindingMap);
            }

            if (mslSource != null) {
                SHADER_DIAGNOSTICS.put(
                    shaderName + ":" + shaderStage,
                    new ShaderDiagnosticsRecord(shaderName, shaderStage, glslSource, mslSource, null)
                );
                emit("shader_stage_diagnostics_msl_captured", shaderName, shaderStage);
                MetalShaderProgramBridge.onTranslatedStage(shaderName, shaderStage, mslSource);
            }
        } catch (IOException | GlslToSpirvCompiler.CompilationException exception) {
            LOGGER.warn(
                "event=metal_phase4 phase=spirv_compile status=failed shader_name={} shader_stage={} message={}",
                shaderName,
                shaderStage,
                exception.getMessage()
            );
            SHADER_DIAGNOSTICS.put(
                shaderName + ":" + shaderStage,
                new ShaderDiagnosticsRecord(shaderName, shaderStage, "", null, exception.getMessage())
            );
            emit("shader_stage_diagnostics_compile_failed", shaderName, shaderStage);
            emit("shader_stage_spirv_compile_failed", shaderName, shaderStage);
        } catch (SpirvReflectionExtractor.ReflectionException exception) {
            LOGGER.warn(
                "event=metal_phase4 phase=spirv_reflect status=failed shader_name={} shader_stage={} message={}",
                shaderName,
                shaderStage,
                exception.getMessage()
            );
            SHADER_DIAGNOSTICS.put(
                shaderName + ":" + shaderStage,
                new ShaderDiagnosticsRecord(shaderName, shaderStage, "", null, exception.getMessage())
            );
            emit("shader_stage_diagnostics_compile_failed", shaderName, shaderStage);
            emit("shader_stage_reflection_failed", shaderName, shaderStage);
        } catch (SpirvToMslTranslator.TranslationException exception) {
            LOGGER.warn(
                "event=metal_phase4 phase=msl_translate status=failed shader_name={} shader_stage={} message={}",
                shaderName,
                shaderStage,
                exception.getMessage()
            );
            SHADER_DIAGNOSTICS.put(
                shaderName + ":" + shaderStage,
                new ShaderDiagnosticsRecord(shaderName, shaderStage, "", null, exception.getMessage())
            );
            emit("shader_stage_diagnostics_compile_failed", shaderName, shaderStage);
            emit("shader_stage_msl_translate_failed", shaderName, shaderStage);
        }
    }

    public static void onShaderStageCompileComplete(String shaderName, String shaderStage) {
        emit("shader_stage_compile_complete", shaderName, shaderStage);
    }

    public static void onShaderStageRelease(String shaderName, String shaderStage) {
        emit("shader_stage_release", shaderName, shaderStage);
    }

    static void setEventSinkForTests(@Nullable ShaderLifecycleEventSink sink) {
        eventSink = sink;
    }

    static void setBridgeActiveForTests(boolean active) {
        bridgeActiveOverrideForTests = active;
    }

    static void clearBridgeActiveOverrideForTests() {
        bridgeActiveOverrideForTests = null;
    }

    static void resetForTests() {
        eventSink = null;
        bridgeActiveOverrideForTests = null;
        spirvCompiler = new GlslToSpirvCompiler();
        mslTranslator = new SpirvToMslTranslator();
        reflectionExtractor = new SpirvReflectionExtractor();
        shaderDiskCache = new ShaderDiskCache();
        REFLECTION_BINDING_MAPS.clear();
        SHADER_DIAGNOSTICS.clear();
        MetalShaderProgramBridge.resetForTests();
        EVENT_SEQUENCE.set(1L);
    }

    static void setSpirvCompilerForTests(GlslToSpirvCompiler compiler) {
        spirvCompiler = compiler;
    }

    static void setMslTranslatorForTests(SpirvToMslTranslator translator) {
        mslTranslator = translator;
    }

    static void setReflectionExtractorForTests(SpirvReflectionExtractor extractor) {
        reflectionExtractor = extractor;
    }

    static void setShaderDiskCacheForTests(ShaderDiskCache cache) {
        shaderDiskCache = cache;
    }

    static @Nullable ShaderBindingMap getReflectionBindingMapForTests(String shaderName) {
        return REFLECTION_BINDING_MAPS.get(shaderName);
    }

    static @Nullable ShaderDiagnosticsRecord getShaderDiagnosticsForTests(String shaderName, String shaderStage) {
        return SHADER_DIAGNOSTICS.get(shaderName + ":" + shaderStage);
    }

    private static void emit(String event, String shaderName, String shaderStage) {
        if (!isBridgeActive()) {
            return;
        }

        long sequenceId = EVENT_SEQUENCE.getAndIncrement();
        if (DEBUG_SHADER_LOGS) {
            LOGGER.debug(
                "event=metal_phase4 phase=shader_lifecycle lifecycle_event={} shader_name={} shader_stage={} sequence={}",
                event,
                shaderName,
                shaderStage,
                sequenceId
            );
        }

        ShaderLifecycleEventSink sink = eventSink;
        if (sink != null) {
            sink.onEvent(event, shaderName, shaderStage, sequenceId);
        }
    }

    private static boolean isBridgeActive() {
        Boolean override = bridgeActiveOverrideForTests;
        if (override != null) {
            return override;
        }
        return HostPlatform.isMacOs() && MetalPhaseOneBridge.isInitialized();
    }
}
