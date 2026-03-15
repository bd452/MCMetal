package io.github.mcmetal.metal;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetalTextureUploadBridgeTest {
    private final RecordingBackend backend = new RecordingBackend();

    @BeforeEach
    void setUp() {
        MetalTextureUploadBridge.resetForTests();
        MetalTextureUploadBridge.setTextureBackendForTests(backend);
    }

    @AfterEach
    void tearDown() {
        MetalTextureUploadBridge.resetForTests();
    }

    @Test
    void firstUploadCreatesNativeTextureAndSubmitsRegionUpdate() {
        ByteBuffer imageData = buffer(8 * 4 * 4);

        MetalTextureUploadBridge.onImageUploadForTests(
            42,
            8,
            4,
            4,
            imageData,
            0,
            2,
            1,
            1,
            1,
            3,
            2
        );

        assertEquals(1, backend.createCalls);
        assertEquals(1, backend.updateCalls);
        assertEquals(1, backend.samplerConfigureCalls);
        assertEquals(MetalTextureFormatMapper.GL_RGBA8, backend.lastCreateInternalFormat);
        assertEquals(MetalTextureFormatMapper.GL_RGBA, backend.lastCreateFormat);
        assertEquals(MetalTextureFormatMapper.GL_UNSIGNED_BYTE, backend.lastCreateType);
        assertEquals(8 * 4 * 4, backend.lastCreateInitialDataLength);
        assertEquals(MetalTextureBridge.GL_NEAREST, backend.lastSamplerMinFilter);
        assertEquals(MetalTextureBridge.GL_NEAREST, backend.lastSamplerMagFilter);
        assertEquals(MetalTextureBridge.GL_REPEAT, backend.lastSamplerWrapU);
        assertEquals(MetalTextureBridge.GL_REPEAT, backend.lastSamplerWrapV);
        assertEquals(8 * 4, backend.lastUpdateRowStrideBytes);
        assertEquals(2, backend.lastUpdateX);
        assertEquals(1, backend.lastUpdateY);
        assertEquals(3, backend.lastUpdateWidth);
        assertEquals(2, backend.lastUpdateHeight);
        assertEquals((byte) 36, backend.lastUpdateFirstByte);
    }

    @Test
    void subsequentUploadOnSameGlIdReusesExistingNativeHandle() {
        ByteBuffer imageData = buffer(4 * 4 * 4);

        MetalTextureUploadBridge.onImageUploadForTests(
            9,
            4,
            4,
            4,
            imageData,
            0,
            0,
            0,
            0,
            0,
            4,
            4
        );
        long firstHandle = backend.lastUpdateHandle;

        MetalTextureUploadBridge.onImageUploadForTests(
            9,
            4,
            4,
            4,
            imageData,
            0,
            1,
            1,
            0,
            0,
            2,
            2
        );

        assertEquals(1, backend.createCalls);
        assertEquals(2, backend.updateCalls);
        assertEquals(2, backend.samplerConfigureCalls);
        assertEquals(firstHandle, backend.lastUpdateHandle);
    }

    @Test
    void uploadWithBlurClampAndMipmapHintsConfiguresLinearClampedSampler() {
        ByteBuffer imageData = buffer(4 * 4 * 4);

        MetalTextureUploadBridge.onImageUploadForTests(
            77,
            4,
            4,
            4,
            imageData,
            0,
            0,
            0,
            0,
            0,
            4,
            4,
            true,
            true,
            true
        );

        assertEquals(1, backend.samplerConfigureCalls);
        assertEquals(MetalTextureBridge.GL_LINEAR_MIPMAP_LINEAR, backend.lastSamplerMinFilter);
        assertEquals(MetalTextureBridge.GL_LINEAR, backend.lastSamplerMagFilter);
        assertEquals(MetalTextureBridge.GL_CLAMP_TO_EDGE, backend.lastSamplerWrapU);
        assertEquals(MetalTextureBridge.GL_CLAMP_TO_EDGE, backend.lastSamplerWrapV);
    }

    @Test
    void closingTextureReleasesNativeHandleOnlyOnce() {
        Object textureIdentity = new Object();
        ByteBuffer imageData = buffer(4 * 4 * 4);

        MetalTextureUploadBridge.onTextureBoundForTests(textureIdentity, 17);
        MetalTextureUploadBridge.onImageUploadForTests(
            17,
            4,
            4,
            4,
            imageData,
            0,
            0,
            0,
            0,
            0,
            4,
            4
        );

        MetalTextureUploadBridge.onTextureClosedForTests(textureIdentity, 17);
        MetalTextureUploadBridge.onTextureClosedForTests(textureIdentity, 17);

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

    private static final class RecordingBackend implements MetalTextureUploadBridge.TextureBackend {
        private long nextHandle = 1L;
        private int createCalls;
        private int updateCalls;
        private int destroyCalls;
        private int lastCreateInternalFormat;
        private int lastCreateFormat;
        private int lastCreateType;
        private int lastCreateInitialDataLength;
        private long lastUpdateHandle;
        private int lastUpdateX;
        private int lastUpdateY;
        private int lastUpdateWidth;
        private int lastUpdateHeight;
        private int lastUpdateRowStrideBytes;
        private byte lastUpdateFirstByte;
        private int samplerConfigureCalls;
        private int lastSamplerMinFilter;
        private int lastSamplerMagFilter;
        private int lastSamplerWrapU;
        private int lastSamplerWrapV;

        @Override
        public long createTexture(
            int internalFormat,
            int format,
            int type,
            int width,
            int height,
            boolean mipmapped,
            boolean renderTarget,
            @Nullable ByteBuffer initialData
        ) {
            createCalls++;
            lastCreateInternalFormat = internalFormat;
            lastCreateFormat = format;
            lastCreateType = type;
            lastCreateInitialDataLength = initialData == null ? 0 : initialData.remaining();
            return nextHandle++;
        }

        @Override
        public void updateTextureRegion(
            long handle,
            int mipLevel,
            int x,
            int y,
            int width,
            int height,
            int rowStrideBytes,
            ByteBuffer data
        ) {
            updateCalls++;
            lastUpdateHandle = handle;
            lastUpdateX = x;
            lastUpdateY = y;
            lastUpdateWidth = width;
            lastUpdateHeight = height;
            lastUpdateRowStrideBytes = rowStrideBytes;

            ByteBuffer payload = data.duplicate();
            lastUpdateFirstByte = payload.get(0);
        }

        @Override
        public void configureTextureSampler(
            long textureHandle,
            int minFilter,
            int magFilter,
            int wrapU,
            int wrapV,
            int maxAnisotropy
        ) {
            samplerConfigureCalls++;
            lastSamplerMinFilter = minFilter;
            lastSamplerMagFilter = magFilter;
            lastSamplerWrapU = wrapU;
            lastSamplerWrapV = wrapV;
        }

        @Override
        public void destroyTexture(long handle) {
            destroyCalls++;
        }
    }
}
