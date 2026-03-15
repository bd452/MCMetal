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
    }
}
