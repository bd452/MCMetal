package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.NativeApi;
import io.github.mcmetal.metal.bridge.NativeBridgeException;
import io.github.mcmetal.metal.bridge.NativeStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 5 bridge for native texture lifecycle and region updates.
 */
public final class MetalTextureBridge {
    interface NativeTextureBackend {
        long createTexture(
            int pixelFormat,
            int width,
            int height,
            int mipLevels,
            int usageFlags,
            @Nullable ByteBuffer initialData,
            int initialDataLength
        );

        int updateTexture(
            long handle,
            int mipLevel,
            int x,
            int y,
            int width,
            int height,
            ByteBuffer data,
            int dataLength,
            int rowStrideBytes
        );

        int destroyTexture(long handle);
    }

    public static final int USAGE_SAMPLED = 1 << 0;
    public static final int USAGE_RENDER_TARGET = 1 << 1;
    public static final int USAGE_SHADER_WRITE = 1 << 2;

    private static final Map<Long, TextureRecord> TEXTURES = new ConcurrentHashMap<>();
    private static volatile NativeTextureBackend nativeTextureBackend = new JniNativeTextureBackend();

    private MetalTextureBridge() {
    }

    public static long createTexture(
        int internalFormat,
        int format,
        int type,
        int width,
        int height,
        boolean mipmapped,
        boolean renderTarget,
        @Nullable ByteBuffer initialData
    ) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive.");
        }

        MetalTextureFormatMapper.MappedTextureFormat mapped = MetalTextureFormatMapper.mapOrThrow(internalFormat, format, type);
        int mipLevels = mipmapped ? computeMipLevels(width, height) : 1;
        int usageFlags = USAGE_SAMPLED | (renderTarget ? USAGE_RENDER_TARGET : 0);

        ByteBuffer payload = initialData == null ? null : initialData.duplicate();
        int payloadLength = payload == null ? 0 : payload.remaining();
        if (payloadLength > 0) {
            int minimumBytes = width * height * mapped.bytesPerPixel();
            if (payloadLength < minimumBytes) {
                throw new IllegalArgumentException(
                    "Initial texture upload underflow: expected at least " + minimumBytes + " bytes but got " + payloadLength + "."
                );
            }
        }

        long handle = nativeTextureBackend.createTexture(
            mapped.nativePixelFormat(),
            width,
            height,
            mipLevels,
            usageFlags,
            payload,
            payloadLength
        );
        if (handle <= 0L) {
            throw new NativeBridgeException("Native operation nativeCreateTexture failed.");
        }

        TEXTURES.put(handle, new TextureRecord(mapped, width, height, mipLevels));
        return handle;
    }

    public static void updateTextureRegion(
        long handle,
        int mipLevel,
        int x,
        int y,
        int width,
        int height,
        int rowStrideBytes,
        ByteBuffer data
    ) {
        if (handle <= 0L) {
            throw new IllegalArgumentException("Texture handle must be positive.");
        }
        if (data == null) {
            throw new IllegalArgumentException("Texture update data must be non-null.");
        }

        TextureRecord record = TEXTURES.get(handle);
        if (record == null) {
            throw new IllegalArgumentException("Unknown native texture handle: " + handle);
        }
        if (mipLevel < 0 || mipLevel >= record.mipLevels) {
            throw new IllegalArgumentException("Mipmap level out of range: " + mipLevel);
        }
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture update region must be positive and in-bounds.");
        }

        int levelWidth = levelDimension(record.width, mipLevel);
        int levelHeight = levelDimension(record.height, mipLevel);
        if (x + width > levelWidth || y + height > levelHeight) {
            throw new IllegalArgumentException("Texture update region exceeds mipmap level bounds.");
        }

        int minimumRowStride = width * record.mappedFormat.bytesPerPixel();
        if (rowStrideBytes < minimumRowStride) {
            throw new IllegalArgumentException("Texture update rowStrideBytes is too small for the pixel format.");
        }

        ByteBuffer payload = data.duplicate();
        int payloadLength = payload.remaining();
        int requiredBytes = rowStrideBytes * height;
        if (payloadLength < requiredBytes) {
            throw new IllegalArgumentException(
                "Texture update underflow: expected at least " + requiredBytes + " bytes but got " + payloadLength + "."
            );
        }

        int status = nativeTextureBackend.updateTexture(
            handle,
            mipLevel,
            x,
            y,
            width,
            height,
            payload,
            payloadLength,
            rowStrideBytes
        );
        requireSuccess("nativeUpdateTexture", status);
    }

    public static void destroyTexture(long handle) {
        if (handle <= 0L) {
            return;
        }

        TextureRecord removed = TEXTURES.remove(handle);
        if (removed == null) {
            return;
        }

        int status = nativeTextureBackend.destroyTexture(handle);
        requireSuccess("nativeDestroyTexture", status);
    }

    static void setNativeTextureBackendForTests(NativeTextureBackend backend) {
        nativeTextureBackend = backend;
    }

    static void resetForTests() {
        TEXTURES.clear();
        nativeTextureBackend = new JniNativeTextureBackend();
    }

    private static int computeMipLevels(int width, int height) {
        int largestDimension = Math.max(width, height);
        return 32 - Integer.numberOfLeadingZeros(largestDimension);
    }

    private static int levelDimension(int baseDimension, int mipLevel) {
        return Math.max(1, baseDimension >> mipLevel);
    }

    private static void requireSuccess(String operation, int statusCode) {
        if (NativeStatus.isSuccess(statusCode)) {
            return;
        }
        throw new NativeBridgeException(
            "Native operation " + operation + " failed with status "
                + NativeStatus.describe(statusCode) + " (" + statusCode + ")."
        );
    }

    private record TextureRecord(
        MetalTextureFormatMapper.MappedTextureFormat mappedFormat,
        int width,
        int height,
        int mipLevels
    ) {
    }

    private static final class JniNativeTextureBackend implements NativeTextureBackend {
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
            return NativeApi.nativeCreateTexture(
                pixelFormat,
                width,
                height,
                mipLevels,
                usageFlags,
                initialData,
                initialDataLength
            );
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
            return NativeApi.nativeUpdateTexture(
                handle,
                mipLevel,
                x,
                y,
                width,
                height,
                data,
                dataLength,
                rowStrideBytes
            );
        }

        @Override
        public int destroyTexture(long handle) {
            return NativeApi.nativeDestroyTexture(handle);
        }
    }
}
