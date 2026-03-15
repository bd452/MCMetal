package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.NativeStatus;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetalTextureBridgeTest {
    private final RecordingBackend backend = new RecordingBackend();

    @BeforeEach
    void setUp() {
        MetalTextureBridge.resetForTests();
        MetalTextureBridge.setNativeTextureBackendForTests(backend);
    }

    @AfterEach
    void tearDown() {
        MetalTextureBridge.resetForTests();
    }

    @Test
    void createTextureMapsFormatAndComputesMipmapCount() {
        long handle = MetalTextureBridge.createTexture(
            MetalTextureFormatMapper.GL_RGBA8,
            MetalTextureFormatMapper.GL_RGBA,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE,
            64,
            16,
            true,
            true,
            buffer(64 * 16 * 4)
        );

        assertEquals(1L, handle);
        assertEquals(1, backend.createCalls);
        assertEquals(MetalTextureFormatMapper.NATIVE_TEXTURE_FORMAT_RGBA8_UNORM, backend.lastCreatePixelFormat);
        assertEquals(7, backend.lastCreateMipLevels);
        assertEquals(MetalTextureBridge.USAGE_SAMPLED | MetalTextureBridge.USAGE_RENDER_TARGET, backend.lastCreateUsageFlags);
        assertEquals(64 * 16 * 4, backend.lastCreateDataLength);
    }

    @Test
    void updateTextureRegionValidatesBoundsAndSubmitsUpdate() {
        long handle = MetalTextureBridge.createTexture(
            MetalTextureFormatMapper.GL_RG8,
            MetalTextureFormatMapper.GL_RG,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE,
            32,
            32,
            false,
            false,
            null
        );

        MetalTextureBridge.updateTextureRegion(
            handle,
            0,
            4,
            8,
            8,
            4,
            16,
            buffer(16 * 4)
        );

        assertEquals(1, backend.updateCalls);
        assertEquals(16, backend.lastUpdateRowStrideBytes);
    }

    @Test
    void createTextureMarksDepthStencilTexturesAsRenderTargets() {
        long handle = MetalTextureBridge.createTexture(
            MetalTextureFormatMapper.GL_DEPTH24_STENCIL8,
            MetalTextureFormatMapper.GL_DEPTH_STENCIL,
            MetalTextureFormatMapper.GL_UNSIGNED_INT_24_8,
            32,
            32,
            false,
            false,
            null
        );

        assertEquals(1L, handle);
        assertEquals(1, backend.createCalls);
        assertEquals(MetalTextureFormatMapper.NATIVE_TEXTURE_FORMAT_DEPTH24_STENCIL8, backend.lastCreatePixelFormat);
        assertEquals(MetalTextureBridge.USAGE_SAMPLED | MetalTextureBridge.USAGE_RENDER_TARGET, backend.lastCreateUsageFlags);
    }

    @Test
    void createTextureRejectsUndersizedInitialUploadPayload() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MetalTextureBridge.createTexture(
                MetalTextureFormatMapper.GL_RGBA8,
                MetalTextureFormatMapper.GL_RGBA,
                MetalTextureFormatMapper.GL_UNSIGNED_BYTE,
                16,
                16,
                false,
                false,
                buffer(8)
            )
        );
    }

    @Test
    void destroyTextureReleasesNativeHandleAtMostOnce() {
        long handle = MetalTextureBridge.createTexture(
            MetalTextureFormatMapper.GL_R8,
            MetalTextureFormatMapper.GL_RED,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE,
            8,
            8,
            false,
            false,
            null
        );

        MetalTextureBridge.destroyTexture(handle);
        MetalTextureBridge.destroyTexture(handle);

        assertEquals(1, backend.destroyCalls);
    }

    @Test
    void configureTextureSamplerNormalizesAndCachesEquivalentDescriptors() {
        long handle = MetalTextureBridge.createTexture(
            MetalTextureFormatMapper.GL_RGBA8,
            MetalTextureFormatMapper.GL_RGBA,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE,
            16,
            16,
            false,
            false,
            null
        );

        MetalTextureBridge.configureTextureSampler(
            handle,
            MetalTextureBridge.GL_LINEAR_MIPMAP_LINEAR,
            MetalTextureBridge.GL_LINEAR,
            MetalTextureBridge.GL_CLAMP,
            MetalTextureBridge.GL_CLAMP_TO_EDGE,
            64
        );
        MetalTextureBridge.configureTextureSampler(
            handle,
            MetalTextureBridge.GL_LINEAR,
            MetalTextureBridge.GL_LINEAR,
            MetalTextureBridge.GL_CLAMP_TO_EDGE,
            MetalTextureBridge.GL_CLAMP_TO_EDGE,
            16
        );

        assertEquals(1, backend.configureCalls);
        assertEquals(MetalTextureBridge.GL_LINEAR, backend.lastSamplerMinFilter);
        assertEquals(MetalTextureBridge.GL_LINEAR, backend.lastSamplerMagFilter);
        assertEquals(MetalTextureBridge.GL_CLAMP_TO_EDGE, backend.lastSamplerWrapU);
        assertEquals(MetalTextureBridge.GL_CLAMP_TO_EDGE, backend.lastSamplerWrapV);
        assertEquals(16, backend.lastSamplerMaxAnisotropy);
    }

    @Test
    void configureTextureSamplerRejectsUnknownHandle() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MetalTextureBridge.configureTextureSampler(
                999L,
                MetalTextureBridge.GL_NEAREST,
                MetalTextureBridge.GL_NEAREST,
                MetalTextureBridge.GL_REPEAT,
                MetalTextureBridge.GL_REPEAT,
                1
            )
        );
    }

    @Test
    void generateMipmapsSubmitsNativeCommandForMipmappedTexture() {
        long handle = MetalTextureBridge.createTexture(
            MetalTextureFormatMapper.GL_RGBA8,
            MetalTextureFormatMapper.GL_RGBA,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE,
            32,
            16,
            true,
            false,
            null
        );

        MetalTextureBridge.generateMipmaps(handle);

        assertEquals(1, backend.generateMipmapsCalls);
        assertEquals(handle, backend.lastGeneratedMipmapsHandle);
    }

    @Test
    void generateMipmapsNoOpsForSingleLevelTexture() {
        long handle = MetalTextureBridge.createTexture(
            MetalTextureFormatMapper.GL_RGBA8,
            MetalTextureFormatMapper.GL_RGBA,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE,
            8,
            8,
            false,
            false,
            null
        );

        MetalTextureBridge.generateMipmaps(handle);

        assertEquals(0, backend.generateMipmapsCalls);
    }

    private static ByteBuffer buffer(int size) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        for (int i = 0; i < size; i++) {
            buffer.put((byte) (i & 0xFF));
        }
        buffer.flip();
        return buffer;
    }

    private static final class RecordingBackend implements MetalTextureBridge.NativeTextureBackend {
        private long nextHandle = 1L;
        private int createCalls;
        private int updateCalls;
        private int destroyCalls;
        private int lastCreatePixelFormat;
        private int lastCreateMipLevels;
        private int lastCreateUsageFlags;
        private int lastCreateDataLength;
        private int lastUpdateRowStrideBytes;
        private int configureCalls;
        private int lastSamplerMinFilter;
        private int lastSamplerMagFilter;
        private int lastSamplerWrapU;
        private int lastSamplerWrapV;
        private int lastSamplerMaxAnisotropy;
        private int generateMipmapsCalls;
        private long lastGeneratedMipmapsHandle;

        @Override
        public long createTexture(
            int pixelFormat,
            int width,
            int height,
            int mipLevels,
            int usageFlags,
            @Nullable ByteBuffer initialData,
            int initialDataLength
        ) {
            createCalls++;
            lastCreatePixelFormat = pixelFormat;
            lastCreateMipLevels = mipLevels;
            lastCreateUsageFlags = usageFlags;
            lastCreateDataLength = initialDataLength;
            return nextHandle++;
        }

        @Override
        public int updateTexture(
            long handle,
            int mipLevel,
            int x,
            int y,
            int width,
            int height,
            ByteBuffer data,
            int dataLength,
            int rowStrideBytes
        ) {
            updateCalls++;
            lastUpdateRowStrideBytes = rowStrideBytes;
            return NativeStatus.OK;
        }

        @Override
        public int destroyTexture(long handle) {
            destroyCalls++;
            return NativeStatus.OK;
        }

        @Override
        public int generateMipmaps(long handle) {
            generateMipmapsCalls++;
            lastGeneratedMipmapsHandle = handle;
            return NativeStatus.OK;
        }

        @Override
        public int configureTextureSampler(
            long textureHandle,
            int minFilter,
            int magFilter,
            int wrapU,
            int wrapV,
            int maxAnisotropy
        ) {
            configureCalls++;
            lastSamplerMinFilter = minFilter;
            lastSamplerMagFilter = magFilter;
            lastSamplerWrapU = wrapU;
            lastSamplerWrapV = wrapV;
            lastSamplerMaxAnisotropy = maxAnisotropy;
            return NativeStatus.OK;
        }
    }
}
