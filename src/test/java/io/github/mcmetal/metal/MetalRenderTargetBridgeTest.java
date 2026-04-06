package io.github.mcmetal.metal;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetalRenderTargetBridgeTest {
    private final RecordingBackend backend = new RecordingBackend();

    @BeforeEach
    void setUp() {
        MetalRenderTargetBridge.resetForTests();
        MetalRenderTargetBridge.setBridgeActiveForTests(true);
        MetalRenderTargetBridge.setRenderTargetBackendForTests(backend);
    }

    @AfterEach
    void tearDown() {
        MetalRenderTargetBridge.resetForTests();
    }

    @Test
    void createAllocatesColorAndDepthAttachments() {
        Object target = new Object();

        MetalRenderTargetBridge.onRenderTargetCreated(target, 320, 180, true);

        assertEquals(2, backend.createCalls);
        assertEquals(MetalTextureFormatMapper.GL_RGBA8, backend.firstCreateInternalFormat);
        assertEquals(MetalTextureFormatMapper.GL_DEPTH24_STENCIL8, backend.secondCreateInternalFormat);
        assertEquals(320, backend.secondCreateWidth);
        assertEquals(180, backend.secondCreateHeight);
        assertEquals(1, backend.renderTargetCreateCalls);
        assertEquals(2, backend.renderTargetCreateCallsIncludingDepth);
        MetalRenderTargetBridge.NativeHandleSnapshot snapshot = MetalRenderTargetBridge.snapshotForTests(target);
        assertNotNull(snapshot);
        assertEquals(1L, snapshot.colorHandle);
        assertEquals(2L, snapshot.depthHandle);
        assertEquals(320, snapshot.width);
        assertEquals(180, snapshot.height);
    }

    @Test
    void bindCachesStateAndAvoidsDuplicateBackendBind() {
        Object target = new Object();
        MetalRenderTargetBridge.onRenderTargetCreated(target, 256, 256, false);

        MetalRenderTargetBridge.onRenderTargetBound(target);
        MetalRenderTargetBridge.onRenderTargetBound(target);

        assertEquals(1, backend.bindCalls);
        assertEquals(1L, backend.lastBoundColorHandle);
        assertEquals(0L, backend.lastBoundDepthHandle);
        assertEquals(256, backend.lastBoundWidth);
        assertEquals(256, backend.lastBoundHeight);

        MetalRenderTargetBridge.BoundStateSnapshot boundState = MetalRenderTargetBridge.boundStateForTests();
        assertEquals(1L, boundState.colorHandle);
        assertEquals(0L, boundState.depthHandle);
    }

    @Test
    void resizeReallocatesAttachmentsAndRebindsWhenCurrentlyBound() {
        Object target = new Object();
        MetalRenderTargetBridge.onRenderTargetCreated(target, 200, 120, true);
        MetalRenderTargetBridge.onRenderTargetBound(target);

        MetalRenderTargetBridge.onRenderTargetResized(target, 400, 240, true);

        assertEquals(4, backend.createCalls);
        assertEquals(2, backend.destroyCalls);
        assertEquals(2, backend.bindCalls);
        assertEquals(3L, backend.lastBoundColorHandle);
        assertEquals(4L, backend.lastBoundDepthHandle);
        assertEquals(400, backend.lastBoundWidth);
        assertEquals(240, backend.lastBoundHeight);

        MetalRenderTargetBridge.NativeHandleSnapshot snapshot = MetalRenderTargetBridge.snapshotForTests(target);
        assertNotNull(snapshot);
        assertEquals(3L, snapshot.colorHandle);
        assertEquals(4L, snapshot.depthHandle);
    }

    @Test
    void resizeWithUnchangedDimensionsIsNoOp() {
        Object target = new Object();
        MetalRenderTargetBridge.onRenderTargetCreated(target, 128, 64, false);

        MetalRenderTargetBridge.onRenderTargetResized(target, 128, 64, false);

        assertEquals(1, backend.createCalls);
        assertEquals(0, backend.destroyCalls);
    }

    @Test
    void deleteReleasesAttachmentsAndClearsBoundState() {
        Object target = new Object();
        MetalRenderTargetBridge.onRenderTargetCreated(target, 64, 64, true);
        MetalRenderTargetBridge.onRenderTargetBound(target);

        MetalRenderTargetBridge.onRenderTargetDeleted(target);

        assertEquals(2, backend.destroyCalls);
        assertNull(MetalRenderTargetBridge.snapshotForTests(target));
        MetalRenderTargetBridge.BoundStateSnapshot boundState = MetalRenderTargetBridge.boundStateForTests();
        assertEquals(0L, boundState.colorHandle);
        assertEquals(0L, boundState.depthHandle);
        assertEquals(0, boundState.width);
        assertEquals(0, boundState.height);
    }

    @Test
    void createRejectsInvalidDimensions() {
        Object target = new Object();
        assertThrows(
            IllegalArgumentException.class,
            () -> MetalRenderTargetBridge.onRenderTargetCreated(target, 0, 64, false)
        );
        assertEquals(0, backend.createCalls);
    }

    private static final class RecordingBackend implements MetalRenderTargetBridge.RenderTargetBackend {
        private long nextHandle = 1L;
        private int createCalls;
        private int destroyCalls;
        private int bindCalls;
        private int firstCreateInternalFormat;
        private int secondCreateInternalFormat;
        private int secondCreateWidth;
        private int secondCreateHeight;
        private int renderTargetCreateCalls;
        private int renderTargetCreateCallsIncludingDepth;
        private long lastBoundColorHandle;
        private long lastBoundDepthHandle;
        private int lastBoundWidth;
        private int lastBoundHeight;

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
            if (createCalls == 1) {
                firstCreateInternalFormat = internalFormat;
            } else if (createCalls == 2) {
                secondCreateInternalFormat = internalFormat;
                secondCreateWidth = width;
                secondCreateHeight = height;
            }
            if (renderTarget) {
                renderTargetCreateCallsIncludingDepth++;
                if (internalFormat == MetalTextureFormatMapper.GL_RGBA8) {
                    renderTargetCreateCalls++;
                }
            }
            return nextHandle++;
        }

        @Override
        public void destroyTexture(long handle) {
            destroyCalls++;
        }

        @Override
        public void bindRenderTarget(long colorHandle, long depthHandle, int width, int height) {
            bindCalls++;
            lastBoundColorHandle = colorHandle;
            lastBoundDepthHandle = depthHandle;
            lastBoundWidth = width;
            lastBoundHeight = height;
        }
    }
}
