package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;
import io.github.mcmetal.mixin.NativeImagePointerAccessor;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Phase 5 bridge for AbstractTexture/NativeImage upload flows.
 */
public final class MetalTextureUploadBridge {
    interface TextureBackend {
        long createTexture(
            int internalFormat,
            int format,
            int type,
            int width,
            int height,
            boolean mipmapped,
            boolean renderTarget,
            @Nullable ByteBuffer initialData
        );

        void updateTextureRegion(
            long handle,
            int mipLevel,
            int x,
            int y,
            int width,
            int height,
            int rowStrideBytes,
            ByteBuffer data
        );

        void destroyTexture(long handle);
    }

    interface BoundTextureReader {
        int currentBoundTexture2d();
    }

    private static final Map<Integer, Long> NATIVE_TEXTURES_BY_GL_ID = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Object, Integer> GL_IDS_BY_TEXTURE_IDENTITY = Collections.synchronizedMap(new IdentityHashMap<>());

    private static volatile TextureBackend textureBackend = new JniTextureBackend();
    private static volatile BoundTextureReader boundTextureReader = new OpenGlBoundTextureReader();
    private static volatile Boolean bridgeActiveOverrideForTests;

    private MetalTextureUploadBridge() {
    }

    public static void onTextureBound(AbstractTexture texture) {
        if (!isBridgeActive()) {
            return;
        }
        trackTextureBinding(texture, texture.getGlId());
    }

    public static void onTextureGlIdCleared(AbstractTexture texture, int previousGlId) {
        if (!isBridgeActive()) {
            return;
        }
        releaseTextureBinding(texture, previousGlId);
    }

    public static void onTextureClosed(AbstractTexture texture, int glId) {
        if (!isBridgeActive()) {
            return;
        }
        releaseTextureBinding(texture, glId);
    }

    public static void onNativeImageUpload(
        NativeImage image,
        int level,
        int xOffset,
        int yOffset,
        int skipPixels,
        int skipRows,
        int width,
        int height
    ) {
        if (!isBridgeActive()) {
            return;
        }

        int boundGlTextureId = boundTextureReader.currentBoundTexture2d();
        if (boundGlTextureId <= 0) {
            return;
        }

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        long nativePointer = ((NativeImagePointerAccessor) (Object) image).mcmetal$getPointer();
        if (nativePointer == 0L) {
            return;
        }

        int channelCount = Math.max(1, image.getFormat().getChannelCount());
        int fullByteCount = checkedByteCount(imageWidth, imageHeight, channelCount);
        ByteBuffer imageData = MemoryUtil.memByteBuffer(nativePointer, fullByteCount);

        uploadImageRegion(
            boundGlTextureId,
            imageWidth,
            imageHeight,
            channelCount,
            imageData,
            level,
            xOffset,
            yOffset,
            skipPixels,
            skipRows,
            width,
            height
        );
    }

    static void onTextureBoundForTests(Object textureIdentity, int glId) {
        trackTextureBinding(textureIdentity, glId);
    }

    static void onTextureClosedForTests(Object textureIdentity, int glId) {
        releaseTextureBinding(textureIdentity, glId);
    }

    static void onImageUploadForTests(
        int boundGlTextureId,
        int imageWidth,
        int imageHeight,
        int channelCount,
        ByteBuffer imageData,
        int level,
        int xOffset,
        int yOffset,
        int skipPixels,
        int skipRows,
        int width,
        int height
    ) {
        uploadImageRegion(
            boundGlTextureId,
            imageWidth,
            imageHeight,
            channelCount,
            imageData,
            level,
            xOffset,
            yOffset,
            skipPixels,
            skipRows,
            width,
            height
        );
    }

    static void setTextureBackendForTests(TextureBackend backend) {
        textureBackend = backend;
    }

    static void setBridgeActiveForTests(boolean active) {
        bridgeActiveOverrideForTests = active;
    }

    static void clearBridgeActiveOverrideForTests() {
        bridgeActiveOverrideForTests = null;
    }

    static void resetForTests() {
        NATIVE_TEXTURES_BY_GL_ID.clear();
        GL_IDS_BY_TEXTURE_IDENTITY.clear();
        textureBackend = new JniTextureBackend();
        boundTextureReader = new OpenGlBoundTextureReader();
        bridgeActiveOverrideForTests = null;
    }

    private static void uploadImageRegion(
        int boundGlTextureId,
        int imageWidth,
        int imageHeight,
        int channelCount,
        ByteBuffer imageData,
        int level,
        int xOffset,
        int yOffset,
        int skipPixels,
        int skipRows,
        int width,
        int height
    ) {
        if (boundGlTextureId <= 0 || imageWidth <= 0 || imageHeight <= 0 || channelCount <= 0 || level < 0) {
            return;
        }
        if (xOffset < 0 || yOffset < 0 || skipPixels < 0 || skipRows < 0 || width <= 0 || height <= 0) {
            return;
        }
        if (skipPixels + width > imageWidth || skipRows + height > imageHeight) {
            return;
        }

        int rowStrideBytes = checkedMultiply(imageWidth, channelCount);
        int fullByteCount = checkedByteCount(imageWidth, imageHeight, channelCount);
        if (imageData.remaining() < fullByteCount) {
            return;
        }

        long textureHandle = NATIVE_TEXTURES_BY_GL_ID.getOrDefault(boundGlTextureId, 0L);
        if (textureHandle <= 0L) {
            TextureFormatTuple tuple = mapTextureFormatTuple(channelCount);
            if (tuple == null) {
                return;
            }

            ByteBuffer initialPayload = sliceBuffer(imageData, 0, fullByteCount);
            boolean mipmapped = level > 0;
            textureHandle = textureBackend.createTexture(
                tuple.internalFormat(),
                tuple.format(),
                tuple.type(),
                imageWidth,
                imageHeight,
                mipmapped,
                false,
                initialPayload
            );
            if (textureHandle <= 0L) {
                return;
            }
            NATIVE_TEXTURES_BY_GL_ID.put(boundGlTextureId, textureHandle);
        }

        int sourceByteOffset = checkedMultiply(checkedAdd(checkedMultiply(skipRows, imageWidth), skipPixels), channelCount);
        int requiredBytes = checkedMultiply(rowStrideBytes, height);
        if (sourceByteOffset > fullByteCount || sourceByteOffset + requiredBytes > fullByteCount) {
            return;
        }

        ByteBuffer updatePayload = sliceBuffer(imageData, sourceByteOffset, requiredBytes);
        textureBackend.updateTextureRegion(
            textureHandle,
            level,
            xOffset,
            yOffset,
            width,
            height,
            rowStrideBytes,
            updatePayload
        );
    }

    private static void trackTextureBinding(Object textureIdentity, int glId) {
        if (glId <= 0) {
            return;
        }

        Integer previousGlId = GL_IDS_BY_TEXTURE_IDENTITY.put(textureIdentity, glId);
        if (previousGlId == null || previousGlId == glId) {
            return;
        }

        Long handle = NATIVE_TEXTURES_BY_GL_ID.remove(previousGlId);
        if (handle != null && handle > 0L) {
            NATIVE_TEXTURES_BY_GL_ID.put(glId, handle);
        }
    }

    private static void releaseTextureBinding(Object textureIdentity, int glId) {
        Integer mappedGlId = GL_IDS_BY_TEXTURE_IDENTITY.remove(textureIdentity);
        releaseGlTexture(glId);
        if (mappedGlId != null && mappedGlId != glId) {
            releaseGlTexture(mappedGlId);
        }
    }

    private static void releaseGlTexture(int glId) {
        if (glId <= 0) {
            return;
        }
        Long nativeHandle = NATIVE_TEXTURES_BY_GL_ID.remove(glId);
        if (nativeHandle == null || nativeHandle <= 0L) {
            return;
        }
        textureBackend.destroyTexture(nativeHandle);
    }

    private static int checkedMultiply(int left, int right) {
        long value = (long) left * (long) right;
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Texture byte size exceeds supported direct buffer range.");
        }
        return (int) value;
    }

    private static int checkedAdd(int left, int right) {
        long value = (long) left + (long) right;
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Texture byte offset exceeds supported direct buffer range.");
        }
        return (int) value;
    }

    private static int checkedByteCount(int width, int height, int bytesPerPixel) {
        return checkedMultiply(checkedMultiply(width, height), bytesPerPixel);
    }

    private static ByteBuffer sliceBuffer(ByteBuffer source, int offsetBytes, int lengthBytes) {
        ByteBuffer duplicate = source.duplicate();
        duplicate.position(offsetBytes);
        duplicate.limit(offsetBytes + lengthBytes);
        return duplicate.slice();
    }

    @Nullable
    private static TextureFormatTuple mapTextureFormatTuple(int channelCount) {
        return switch (channelCount) {
            case 1 -> new TextureFormatTuple(
                MetalTextureFormatMapper.GL_R8,
                MetalTextureFormatMapper.GL_RED,
                MetalTextureFormatMapper.GL_UNSIGNED_BYTE
            );
            case 2 -> new TextureFormatTuple(
                MetalTextureFormatMapper.GL_RG8,
                MetalTextureFormatMapper.GL_RG,
                MetalTextureFormatMapper.GL_UNSIGNED_BYTE
            );
            case 4 -> new TextureFormatTuple(
                MetalTextureFormatMapper.GL_RGBA8,
                MetalTextureFormatMapper.GL_RGBA,
                MetalTextureFormatMapper.GL_UNSIGNED_BYTE
            );
            default -> null;
        };
    }

    private static boolean isBridgeActive() {
        Boolean bridgeActiveOverride = bridgeActiveOverrideForTests;
        if (bridgeActiveOverride != null) {
            return bridgeActiveOverride;
        }
        return HostPlatform.isMacOs() && MetalPhaseOneBridge.isInitialized();
    }

    private record TextureFormatTuple(int internalFormat, int format, int type) {
    }

    private static final class OpenGlBoundTextureReader implements BoundTextureReader {
        @Override
        public int currentBoundTexture2d() {
            return GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        }
    }

    private static final class JniTextureBackend implements TextureBackend {
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
            return MetalTextureBridge.createTexture(
                internalFormat,
                format,
                type,
                width,
                height,
                mipmapped,
                renderTarget,
                initialData
            );
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
            MetalTextureBridge.updateTextureRegion(handle, mipLevel, x, y, width, height, rowStrideBytes, data);
        }

        @Override
        public void destroyTexture(long handle) {
            MetalTextureBridge.destroyTexture(handle);
        }
    }
}
