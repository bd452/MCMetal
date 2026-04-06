package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Phase 5 bridge for RenderTarget lifecycle semantics (create/bind/resize/destroy).
 */
public final class MetalRenderTargetBridge {
    interface RenderTargetBackend {
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

        void destroyTexture(long handle);

        void bindRenderTarget(long colorHandle, long depthHandle, int width, int height);
    }

    private static final int DEFAULT_COLOR_INTERNAL_FORMAT = MetalTextureFormatMapper.GL_RGBA8;
    private static final int DEFAULT_COLOR_FORMAT = MetalTextureFormatMapper.GL_RGBA;
    private static final int DEFAULT_COLOR_TYPE = MetalTextureFormatMapper.GL_UNSIGNED_BYTE;
    private static final int DEFAULT_DEPTH_INTERNAL_FORMAT = MetalTextureFormatMapper.GL_DEPTH24_STENCIL8;
    private static final int DEFAULT_DEPTH_FORMAT = MetalTextureFormatMapper.GL_DEPTH_STENCIL;
    private static final int DEFAULT_DEPTH_TYPE = MetalTextureFormatMapper.GL_UNSIGNED_INT_24_8;

    private static final Map<Object, NativeRenderTargetRecord> RENDER_TARGETS =
        Collections.synchronizedMap(new IdentityHashMap<>());

    private static volatile RenderTargetBackend renderTargetBackend = new JniRenderTargetBackend();
    private static volatile Boolean bridgeActiveOverrideForTests;

    private static volatile long boundColorHandle;
    private static volatile long boundDepthHandle;
    private static volatile int boundWidth;
    private static volatile int boundHeight;

    private MetalRenderTargetBridge() {
    }

    public static void onRenderTargetCreated(Object renderTargetIdentity, int width, int height, boolean useDepth) {
        if (!isBridgeActive()) {
            return;
        }
        createOrResize(renderTargetIdentity, width, height, useDepth);
    }

    public static void onRenderTargetResized(Object renderTargetIdentity, int width, int height, boolean useDepth) {
        if (!isBridgeActive()) {
            return;
        }
        createOrResize(renderTargetIdentity, width, height, useDepth);
    }

    public static void onRenderTargetBound(Object renderTargetIdentity) {
        if (!isBridgeActive()) {
            return;
        }
        NativeRenderTargetRecord record = RENDER_TARGETS.get(renderTargetIdentity);
        if (record == null) {
            return;
        }
        bindRecord(record);
    }

    public static void onRenderTargetDeleted(Object renderTargetIdentity) {
        if (!isBridgeActive()) {
            return;
        }
        NativeRenderTargetRecord record = RENDER_TARGETS.remove(renderTargetIdentity);
        if (record == null) {
            return;
        }

        if (record.colorHandle > 0L) {
            renderTargetBackend.destroyTexture(record.colorHandle);
        }
        if (record.depthHandle > 0L) {
            renderTargetBackend.destroyTexture(record.depthHandle);
        }

        if (boundColorHandle == record.colorHandle && boundDepthHandle == record.depthHandle) {
            clearBoundState();
        }
    }

    static void setRenderTargetBackendForTests(RenderTargetBackend backend) {
        renderTargetBackend = backend;
    }

    static void setBridgeActiveForTests(boolean active) {
        bridgeActiveOverrideForTests = active;
    }

    static void clearBridgeActiveOverrideForTests() {
        bridgeActiveOverrideForTests = null;
    }

    static void resetForTests() {
        RENDER_TARGETS.clear();
        renderTargetBackend = new JniRenderTargetBackend();
        bridgeActiveOverrideForTests = null;
        clearBoundState();
    }

    @Nullable
    static NativeHandleSnapshot snapshotForTests(Object renderTargetIdentity) {
        NativeRenderTargetRecord record = RENDER_TARGETS.get(renderTargetIdentity);
        if (record == null) {
            return null;
        }
        return new NativeHandleSnapshot(record.colorHandle, record.depthHandle, record.width, record.height, record.useDepth);
    }

    static BoundStateSnapshot boundStateForTests() {
        return new BoundStateSnapshot(boundColorHandle, boundDepthHandle, boundWidth, boundHeight);
    }

    private static void createOrResize(Object renderTargetIdentity, int width, int height, boolean useDepth) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("RenderTarget dimensions must be positive.");
        }

        NativeRenderTargetRecord existing = RENDER_TARGETS.get(renderTargetIdentity);
        if (existing != null
            && existing.width == width
            && existing.height == height
            && existing.useDepth == useDepth) {
            return;
        }

        NativeRenderTargetRecord next = allocateRecord(width, height, useDepth);
        NativeRenderTargetRecord replaced = RENDER_TARGETS.put(renderTargetIdentity, next);
        if (replaced != null) {
            if (replaced.colorHandle > 0L) {
                renderTargetBackend.destroyTexture(replaced.colorHandle);
            }
            if (replaced.depthHandle > 0L) {
                renderTargetBackend.destroyTexture(replaced.depthHandle);
            }
        }

        if (existing != null
            && boundColorHandle == existing.colorHandle
            && boundDepthHandle == existing.depthHandle) {
            bindRecord(next);
        }
    }

    private static NativeRenderTargetRecord allocateRecord(int width, int height, boolean useDepth) {
        long colorHandle = renderTargetBackend.createTexture(
            DEFAULT_COLOR_INTERNAL_FORMAT,
            DEFAULT_COLOR_FORMAT,
            DEFAULT_COLOR_TYPE,
            width,
            height,
            false,
            true,
            null
        );
        if (colorHandle <= 0L) {
            throw new IllegalStateException("Failed to allocate native color attachment for RenderTarget.");
        }

        long depthHandle = 0L;
        if (useDepth) {
            depthHandle = renderTargetBackend.createTexture(
                DEFAULT_DEPTH_INTERNAL_FORMAT,
                DEFAULT_DEPTH_FORMAT,
                DEFAULT_DEPTH_TYPE,
                width,
                height,
                false,
                true,
                null
            );
            if (depthHandle <= 0L) {
                renderTargetBackend.destroyTexture(colorHandle);
                throw new IllegalStateException("Failed to allocate native depth attachment for RenderTarget.");
            }
        }

        return new NativeRenderTargetRecord(colorHandle, depthHandle, width, height, useDepth);
    }

    private static void bindRecord(NativeRenderTargetRecord record) {
        if (boundColorHandle == record.colorHandle
            && boundDepthHandle == record.depthHandle
            && boundWidth == record.width
            && boundHeight == record.height) {
            return;
        }

        renderTargetBackend.bindRenderTarget(
            record.colorHandle,
            record.depthHandle,
            record.width,
            record.height
        );
        boundColorHandle = record.colorHandle;
        boundDepthHandle = record.depthHandle;
        boundWidth = record.width;
        boundHeight = record.height;
    }

    private static void clearBoundState() {
        boundColorHandle = 0L;
        boundDepthHandle = 0L;
        boundWidth = 0;
        boundHeight = 0;
    }

    private static boolean isBridgeActive() {
        Boolean bridgeActiveOverride = bridgeActiveOverrideForTests;
        if (bridgeActiveOverride != null) {
            return bridgeActiveOverride;
        }
        return HostPlatform.isMacOs() && MetalPhaseOneBridge.isInitialized();
    }

    static final class NativeHandleSnapshot {
        final long colorHandle;
        final long depthHandle;
        final int width;
        final int height;
        final boolean useDepth;

        NativeHandleSnapshot(long colorHandle, long depthHandle, int width, int height, boolean useDepth) {
            this.colorHandle = colorHandle;
            this.depthHandle = depthHandle;
            this.width = width;
            this.height = height;
            this.useDepth = useDepth;
        }
    }

    static final class BoundStateSnapshot {
        final long colorHandle;
        final long depthHandle;
        final int width;
        final int height;

        BoundStateSnapshot(long colorHandle, long depthHandle, int width, int height) {
            this.colorHandle = colorHandle;
            this.depthHandle = depthHandle;
            this.width = width;
            this.height = height;
        }
    }

    private static final class NativeRenderTargetRecord {
        private final long colorHandle;
        private final long depthHandle;
        private final int width;
        private final int height;
        private final boolean useDepth;

        private NativeRenderTargetRecord(long colorHandle, long depthHandle, int width, int height, boolean useDepth) {
            this.colorHandle = colorHandle;
            this.depthHandle = depthHandle;
            this.width = width;
            this.height = height;
            this.useDepth = useDepth;
        }
    }

    private static final class JniRenderTargetBackend implements RenderTargetBackend {
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
        public void destroyTexture(long handle) {
            MetalTextureBridge.destroyTexture(handle);
        }

        @Override
        public void bindRenderTarget(long colorHandle, long depthHandle, int width, int height) {
            // Render-pass descriptor generation lands in the subsequent Phase 5 item.
        }
    }
}
