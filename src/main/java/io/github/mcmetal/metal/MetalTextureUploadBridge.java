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

        void configureTextureSampler(
            long textureHandle,
            int minFilter,
            int magFilter,
            int wrapU,
            int wrapV,
            int maxAnisotropy
        );

        void generateMipmaps(long textureHandle);

        void destroyTexture(long handle);
    }

    interface BoundTextureReader {
        int currentBoundTexture2d();
    }

    private static final Map<Integer, NativeTextureBinding> NATIVE_TEXTURES_BY_GL_ID =
        Collections.synchronizedMap(new HashMap<>());
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
        onNativeImageUpload(
            image,
            level,
            xOffset,
            yOffset,
            skipPixels,
            skipRows,
            width,
            height,
            false,
            false,
            level > 0
        );
    }

    public static void onNativeImageUpload(
        NativeImage image,
        int level,
        int xOffset,
        int yOffset,
        int skipPixels,
        int skipRows,
        int width,
        int height,
        boolean blur,
        boolean clamp,
        boolean mipmap
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
            height,
            blur,
            clamp,
            mipmap
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
        onImageUploadForTests(
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
            height,
            false,
            false,
            level > 0
        );
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
        int height,
        boolean blur,
        boolean clamp,
        boolean mipmap
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
            height,
            blur,
            clamp,
            mipmap
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
        int height,
        boolean blur,
        boolean clamp,
        boolean mipmap
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

        boolean mipmappedUpload = mipmap || level > 0;
        NativeTextureBinding existingBinding = NATIVE_TEXTURES_BY_GL_ID.get(boundGlTextureId);
        if (shouldRecreateNativeTexture(existingBinding, imageWidth, imageHeight, channelCount, mipmappedUpload)) {
            if (existingBinding != null) {
                textureBackend.destroyTexture(existingBinding.handle());
            }

            TextureFormatTuple tuple = mapTextureFormatTuple(channelCount);
            if (tuple == null) {
                return;
            }

            ByteBuffer initialPayload = sliceBuffer(imageData, 0, fullByteCount);
            long textureHandle = textureBackend.createTexture(
                tuple.internalFormat(),
                tuple.format(),
                tuple.type(),
                imageWidth,
                imageHeight,
                mipmappedUpload,
                false,
                initialPayload
            );
            if (textureHandle <= 0L) {
                return;
            }
            existingBinding = new NativeTextureBinding(textureHandle, imageWidth, imageHeight, channelCount, mipmappedUpload);
            NATIVE_TEXTURES_BY_GL_ID.put(boundGlTextureId, existingBinding);
        }

        if (existingBinding == null) {
            return;
        }

        int sourceByteOffset = checkedMultiply(checkedAdd(checkedMultiply(skipRows, imageWidth), skipPixels), channelCount);
        int requiredBytes = checkedMultiply(rowStrideBytes, height);
        if (sourceByteOffset > fullByteCount || sourceByteOffset + requiredBytes > fullByteCount) {
            return;
        }

        ByteBuffer updatePayload = sliceBuffer(imageData, sourceByteOffset, requiredBytes);
        configureSamplerForUpload(existingBinding.handle(), blur, clamp, existingBinding.mipmapped());
        textureBackend.updateTextureRegion(
            existingBinding.handle(),
            level,
            xOffset,
            yOffset,
            width,
            height,
            rowStrideBytes,
            updatePayload
        );
        if (existingBinding.mipmapped() && level == 0) {
            textureBackend.generateMipmaps(existingBinding.handle());
        }
    }

    private static void configureSamplerForUpload(long textureHandle, boolean blur, boolean clamp, boolean mipmap) {
        int minFilter;
        if (blur) {
            minFilter = mipmap ? MetalTextureBridge.GL_LINEAR_MIPMAP_LINEAR : MetalTextureBridge.GL_LINEAR;
        } else {
            minFilter = mipmap ? MetalTextureBridge.GL_NEAREST_MIPMAP_NEAREST : MetalTextureBridge.GL_NEAREST;
        }
        int magFilter = blur ? MetalTextureBridge.GL_LINEAR : MetalTextureBridge.GL_NEAREST;
        int wrap = clamp ? MetalTextureBridge.GL_CLAMP_TO_EDGE : MetalTextureBridge.GL_REPEAT;
        textureBackend.configureTextureSampler(
            textureHandle,
            minFilter,
            magFilter,
            wrap,
            wrap,
            1
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

        NativeTextureBinding binding = NATIVE_TEXTURES_BY_GL_ID.remove(previousGlId);
        if (binding != null && binding.handle() > 0L) {
            NATIVE_TEXTURES_BY_GL_ID.put(glId, binding);
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
        NativeTextureBinding binding = NATIVE_TEXTURES_BY_GL_ID.remove(glId);
        if (binding == null || binding.handle() <= 0L) {
            return;
        }
        textureBackend.destroyTexture(binding.handle());
    }

    private static boolean shouldRecreateNativeTexture(
        @Nullable NativeTextureBinding existingBinding,
        int imageWidth,
        int imageHeight,
        int channelCount,
        boolean mipmappedUpload
    ) {
        if (existingBinding == null || existingBinding.handle() <= 0L) {
            return true;
        }
        if (existingBinding.width() != imageWidth || existingBinding.height() != imageHeight) {
            return true;
        }
        if (existingBinding.channelCount() != channelCount) {
            return true;
        }
        return mipmappedUpload && !existingBinding.mipmapped();
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

    private record NativeTextureBinding(
        long handle,
        int width,
        int height,
        int channelCount,
        boolean mipmapped
    ) {
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
        public void configureTextureSampler(
            long textureHandle,
            int minFilter,
            int magFilter,
            int wrapU,
            int wrapV,
            int maxAnisotropy
        ) {
            MetalTextureBridge.configureTextureSampler(
                textureHandle,
                minFilter,
                magFilter,
                wrapU,
                wrapV,
                maxAnisotropy
            );
        }

        @Override
        public void generateMipmaps(long textureHandle) {
            MetalTextureBridge.generateMipmaps(textureHandle);
        }

        @Override
        public void destroyTexture(long handle) {
            MetalTextureBridge.destroyTexture(handle);
        }
    }
}
