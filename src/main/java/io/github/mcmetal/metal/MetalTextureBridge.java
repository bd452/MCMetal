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

        int generateMipmaps(long handle);

        int destroyTexture(long handle);

        int configureTextureSampler(
            long textureHandle,
            int minFilter,
            int magFilter,
            int wrapU,
            int wrapV,
            int maxAnisotropy
        );
    }

    public static final int USAGE_SAMPLED = 1 << 0;
    public static final int USAGE_RENDER_TARGET = 1 << 1;
    public static final int USAGE_SHADER_WRITE = 1 << 2;

    static final int GL_NEAREST = 0x2600;
    static final int GL_LINEAR = 0x2601;
    static final int GL_NEAREST_MIPMAP_NEAREST = 0x2700;
    static final int GL_LINEAR_MIPMAP_NEAREST = 0x2701;
    static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;
    static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    static final int GL_REPEAT = 0x2901;
    static final int GL_CLAMP = 0x2900;
    static final int GL_CLAMP_TO_EDGE = 0x812F;
    static final int GL_MIRRORED_REPEAT = 0x8370;
    private static final int MAX_SUPPORTED_ANISOTROPY = 16;

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
        int usageFlags = computeUsageFlags(mapped, renderTarget);

        ByteBuffer payload = initialData == null ? null : initialData.duplicate();
        int payloadLength = payload == null ? 0 : payload.remaining();
        if (payloadLength > 0) {
            long minimumBytes = requiredByteCount(width, height, mapped.bytesPerPixel());
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

        TEXTURES.put(handle, new TextureRecord(mapped, width, height, mipLevels, null));
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
        long requiredBytes = requiredByteCount(rowStrideBytes, height, 1);
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

    public static void generateMipmaps(long handle) {
        if (handle <= 0L) {
            throw new IllegalArgumentException("Texture handle must be positive.");
        }

        TextureRecord record = TEXTURES.get(handle);
        if (record == null) {
            throw new IllegalArgumentException("Unknown native texture handle: " + handle);
        }
        if (record.mipLevels() <= 1) {
            return;
        }

        int status = nativeTextureBackend.generateMipmaps(handle);
        requireSuccess("nativeGenerateTextureMipmaps", status);
    }

    public static void configureTextureSampler(
        long textureHandle,
        int minFilter,
        int magFilter,
        int wrapU,
        int wrapV,
        int maxAnisotropy
    ) {
        if (textureHandle <= 0L) {
            throw new IllegalArgumentException("Texture handle must be positive.");
        }
        if (maxAnisotropy <= 0) {
            throw new IllegalArgumentException("maxAnisotropy must be at least 1.");
        }

        TextureRecord record = TEXTURES.get(textureHandle);
        if (record == null) {
            throw new IllegalArgumentException("Unknown native texture handle: " + textureHandle);
        }

        SamplerConfig requestedConfig = normalizeSamplerConfig(record, minFilter, magFilter, wrapU, wrapV, maxAnisotropy);
        if (requestedConfig.equals(record.samplerConfig())) {
            return;
        }

        int status = nativeTextureBackend.configureTextureSampler(
            textureHandle,
            requestedConfig.minFilter(),
            requestedConfig.magFilter(),
            requestedConfig.wrapU(),
            requestedConfig.wrapV(),
            requestedConfig.maxAnisotropy()
        );
        requireSuccess("nativeConfigureTextureSampler", status);
        TEXTURES.put(
            textureHandle,
            new TextureRecord(
                record.mappedFormat(),
                record.width(),
                record.height(),
                record.mipLevels(),
                requestedConfig
            )
        );
    }

    public static void destroyTexture(long handle) {
        if (handle <= 0L) {
            return;
        }

        TextureRecord existingRecord = TEXTURES.get(handle);
        if (existingRecord == null) {
            return;
        }

        int status = nativeTextureBackend.destroyTexture(handle);
        requireSuccess("nativeDestroyTexture", status);
        TEXTURES.remove(handle);
    }

    static void setNativeTextureBackendForTests(NativeTextureBackend backend) {
        nativeTextureBackend = backend;
    }

    static void resetForTests() {
        TEXTURES.clear();
        nativeTextureBackend = new JniNativeTextureBackend();
    }

    private static SamplerConfig normalizeSamplerConfig(
        TextureRecord record,
        int minFilter,
        int magFilter,
        int wrapU,
        int wrapV,
        int maxAnisotropy
    ) {
        int normalizedMinFilter = normalizeMinFilter(minFilter, record.mipLevels() > 1);
        int normalizedMagFilter = normalizeMagFilter(magFilter);
        int normalizedWrapU = normalizeWrap(wrapU);
        int normalizedWrapV = normalizeWrap(wrapV);
        int normalizedMaxAnisotropy = Math.min(maxAnisotropy, MAX_SUPPORTED_ANISOTROPY);
        return new SamplerConfig(
            normalizedMinFilter,
            normalizedMagFilter,
            normalizedWrapU,
            normalizedWrapV,
            normalizedMaxAnisotropy
        );
    }

    private static int normalizeMinFilter(int minFilter, boolean hasMipmaps) {
        int normalized = switch (minFilter) {
            case GL_NEAREST,
                GL_LINEAR,
                GL_NEAREST_MIPMAP_NEAREST,
                GL_LINEAR_MIPMAP_NEAREST,
                GL_NEAREST_MIPMAP_LINEAR,
                GL_LINEAR_MIPMAP_LINEAR -> minFilter;
            default -> throw new IllegalArgumentException("Unsupported texture min filter: " + minFilter);
        };

        if (hasMipmaps) {
            return normalized;
        }

        return switch (normalized) {
            case GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST_MIPMAP_LINEAR -> GL_NEAREST;
            case GL_LINEAR_MIPMAP_NEAREST, GL_LINEAR_MIPMAP_LINEAR -> GL_LINEAR;
            default -> normalized;
        };
    }

    private static int normalizeMagFilter(int magFilter) {
        return switch (magFilter) {
            case GL_NEAREST, GL_LINEAR -> magFilter;
            default -> throw new IllegalArgumentException("Unsupported texture mag filter: " + magFilter);
        };
    }

    private static int normalizeWrap(int wrapMode) {
        return switch (wrapMode) {
            case GL_REPEAT -> GL_REPEAT;
            case GL_CLAMP, GL_CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE;
            case GL_MIRRORED_REPEAT -> GL_MIRRORED_REPEAT;
            default -> throw new IllegalArgumentException("Unsupported texture wrap mode: " + wrapMode);
        };
    }

    private static int computeMipLevels(int width, int height) {
        int largestDimension = Math.max(width, height);
        return 32 - Integer.numberOfLeadingZeros(largestDimension);
    }

    private static int levelDimension(int baseDimension, int mipLevel) {
        return Math.max(1, baseDimension >> mipLevel);
    }

    private static int computeUsageFlags(MetalTextureFormatMapper.MappedTextureFormat mapped, boolean renderTarget) {
        int usageFlags = USAGE_SAMPLED;
        if (renderTarget || mapped.depthStencil()) {
            usageFlags |= USAGE_RENDER_TARGET;
        }
        return usageFlags;
    }

    private static long requiredByteCount(int a, int b, int c) {
        long bytes = (long) a * (long) b * (long) c;
        if (bytes < 0 || bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Texture payload size exceeds supported direct buffer range.");
        }
        return bytes;
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
        int mipLevels,
        @Nullable SamplerConfig samplerConfig
    ) {
    }

    private record SamplerConfig(
        int minFilter,
        int magFilter,
        int wrapU,
        int wrapV,
        int maxAnisotropy
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

        @Override
        public int generateMipmaps(long handle) {
            return NativeApi.nativeGenerateTextureMipmaps(handle);
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
            return NativeApi.nativeConfigureTextureSampler(
                textureHandle,
                minFilter,
                magFilter,
                wrapU,
                wrapV,
                maxAnisotropy
            );
        }
    }
}
