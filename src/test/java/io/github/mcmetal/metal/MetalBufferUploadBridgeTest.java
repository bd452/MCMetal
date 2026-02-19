package io.github.mcmetal.metal;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetalBufferUploadBridgeTest {
    private final RecordingNativeBackend backend = new RecordingNativeBackend();

    @BeforeEach
    void setUp() {
        MetalBufferUploadBridge.resetForTests();
        MetalBufferUploadBridge.setBridgeActiveForTests(true);
        MetalBufferUploadBridge.setNativeBufferBackendForTests(backend);
    }

    @AfterEach
    void tearDown() {
        MetalBufferUploadBridge.resetForTests();
    }

    @Test
    void initialUploadCreatesVertexAndIndexBuffers() {
        Object vertexBuffer = new Object();
        MetalBufferUploadBridge.UploadSnapshot snapshot = snapshot(64, 24);

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            new Object(),
            snapshot
        );

        assertEquals(2, backend.createCalls);
        assertEquals(List.of(64, 24), backend.createdSizes);
        assertEquals(1, backend.registerDescriptorCalls);
        assertEquals(0, backend.updateCalls);
        assertEquals(0, backend.destroyCalls);
    }

    @Test
    void repeatedUploadReusesExistingHandlesWhenCapacityIsSufficient() {
        Object vertexBuffer = new Object();
        MetalBufferUploadBridge.UploadSnapshot first = snapshot(96, 32);
        MetalBufferUploadBridge.UploadSnapshot second = snapshot(64, 16);

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.STATIC,
            new Object(),
            first
        );
        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.STATIC,
            new Object(),
            second
        );

        assertEquals(2, backend.createCalls);
        assertEquals(2, backend.updateCalls);
        assertEquals(1, backend.registerDescriptorCalls);
        assertEquals(0, backend.destroyCalls);
    }

    @Test
    void largerUploadReallocatesAndDestroysOldHandles() {
        Object vertexBuffer = new Object();
        MetalBufferUploadBridge.UploadSnapshot first = snapshot(32, 12);
        MetalBufferUploadBridge.UploadSnapshot second = snapshot(160, 48);

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            new Object(),
            first
        );
        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            new Object(),
            second
        );

        assertEquals(4, backend.createCalls);
        assertEquals(1, backend.registerDescriptorCalls);
        assertEquals(0, backend.destroyCalls);
        MetalBufferUploadBridge.onFrameSubmittedForTests();
        assertEquals(0, backend.destroyCalls);
        MetalBufferUploadBridge.onFrameSubmittedForTests();
        assertEquals(2, backend.destroyCalls);
    }

    @Test
    void bufferBuilderSnapshotCacheIsUsedBeforeFallbackExtraction() {
        Object vertexBuffer = new Object();
        Object snapshotKey = new Object();
        MetalBufferUploadBridge.UploadSnapshot cached = snapshot(80, 0);
        MetalBufferUploadBridge.UploadSnapshot fallback = snapshot(12, 0);
        MetalBufferUploadBridge.rememberSnapshotForTests(snapshotKey, cached);

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            snapshotKey,
            fallback
        );

        assertEquals(1, backend.createCalls);
        assertEquals(List.of(80), backend.createdSizes);
        assertEquals(1, backend.registerDescriptorCalls);
    }

    @Test
    void closeReleasesTrackedNativeAllocations() {
        Object vertexBuffer = new Object();
        MetalBufferUploadBridge.UploadSnapshot snapshot = snapshot(48, 16);

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            new Object(),
            snapshot
        );
        MetalBufferUploadBridge.onVertexBufferCloseForTests(vertexBuffer);

        assertEquals(0, backend.destroyCalls);
        MetalBufferUploadBridge.onFrameSubmittedForTests();
        assertEquals(0, backend.destroyCalls);
        MetalBufferUploadBridge.onFrameSubmittedForTests();
        assertEquals(2, backend.destroyCalls);
    }

    @Test
    void drawUsesNonIndexedPathWhenIndexCountMatchesVertexCount() {
        Object vertexBuffer = new Object();
        MetalBufferUploadBridge.UploadSnapshot snapshot = snapshot(
            48,
            0,
            VertexFormat.DrawMode.TRIANGLES,
            3,
            3,
            VertexFormat.IndexType.SHORT
        );

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            new Object(),
            snapshot
        );
        MetalBufferUploadBridge.onVertexBufferDrawForTests(vertexBuffer);

        assertEquals(1, backend.drawCalls);
        assertEquals(0, backend.drawIndexedCalls);
    }

    @Test
    void drawUsesIndexedPathWhenIndicesArePresent() {
        Object vertexBuffer = new Object();
        MetalBufferUploadBridge.UploadSnapshot snapshot = snapshot(
            64,
            24,
            VertexFormat.DrawMode.QUADS,
            4,
            6,
            VertexFormat.IndexType.SHORT
        );

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            new Object(),
            snapshot
        );
        MetalBufferUploadBridge.onVertexBufferDrawForTests(vertexBuffer);

        assertEquals(0, backend.drawCalls);
        assertEquals(1, backend.drawIndexedCalls);
    }

    @Test
    void drawRejectsUnsupportedIndexTypeForIndexedPath() {
        Object vertexBuffer = new Object();
        MetalBufferUploadBridge.UploadSnapshot snapshot = new MetalBufferUploadBridge.UploadSnapshot(
            buffer(32, (byte) 1),
            buffer(16, (byte) 2),
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS.glMode,
            4,
            6,
            0xDEAD
        );

        MetalBufferUploadBridge.onVertexBufferUploadForTests(
            vertexBuffer,
            MetalBufferUploadBridge.BufferUsage.DYNAMIC,
            new Object(),
            snapshot
        );

        assertThrows(
            RuntimeException.class,
            () -> MetalBufferUploadBridge.onVertexBufferDrawForTests(vertexBuffer)
        );
    }

    @Test
    void stressTestBufferChurnAndHighDrawCount() {
        List<Object> vertexBuffers = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            vertexBuffers.add(new Object());
        }

        int totalDraws = 0;
        for (int frame = 0; frame < 220; frame++) {
            for (int i = 0; i < vertexBuffers.size(); i++) {
                Object vertexBuffer = vertexBuffers.get(i);
                boolean indexed = ((frame + i) % 3) == 0;
                int vertexBytes = 32 + ((frame + i) % 12) * 24;
                int indexBytes = indexed ? 24 + ((frame + i) % 4) * 8 : 0;
                VertexFormat.DrawMode drawMode = indexed ? VertexFormat.DrawMode.QUADS : VertexFormat.DrawMode.TRIANGLES;
                int vertexCount = indexed ? 4 : 3;
                int indexCount = indexed ? 6 : 3;

                MetalBufferUploadBridge.UploadSnapshot snapshot = snapshot(
                    vertexBytes,
                    indexBytes,
                    drawMode,
                    vertexCount,
                    indexCount,
                    VertexFormat.IndexType.SHORT
                );
                MetalBufferUploadBridge.onVertexBufferUploadForTests(
                    vertexBuffer,
                    MetalBufferUploadBridge.BufferUsage.DYNAMIC,
                    new Object(),
                    snapshot
                );
                MetalBufferUploadBridge.onVertexBufferDrawForTests(vertexBuffer);
                totalDraws++;
            }
            MetalBufferUploadBridge.onFrameSubmittedForTests();
        }

        for (Object vertexBuffer : vertexBuffers) {
            MetalBufferUploadBridge.onVertexBufferCloseForTests(vertexBuffer);
        }
        MetalBufferUploadBridge.onFrameSubmittedForTests();
        MetalBufferUploadBridge.onFrameSubmittedForTests();
        MetalBufferUploadBridge.onFrameSubmittedForTests();

        assertTrue(backend.createCalls > 0);
        assertTrue(backend.updateCalls > 0);
        assertTrue(backend.drawCalls + backend.drawIndexedCalls >= totalDraws);
        assertTrue(backend.destroyCalls > 0);
    }

    private static MetalBufferUploadBridge.UploadSnapshot snapshot(int vertexBytes, int indexBytes) {
        return snapshot(
            vertexBytes,
            indexBytes,
            VertexFormat.DrawMode.QUADS,
            4,
            6,
            VertexFormat.IndexType.SHORT
        );
    }

    private static MetalBufferUploadBridge.UploadSnapshot snapshot(
        int vertexBytes,
        int indexBytes,
        VertexFormat.DrawMode drawMode,
        int vertexCount,
        int indexCount,
        VertexFormat.IndexType indexType
    ) {
        ByteBuffer vertex = buffer(vertexBytes, (byte) 7);
        ByteBuffer index = indexBytes > 0 ? buffer(indexBytes, (byte) 42) : null;
        return MetalBufferUploadBridge.createSnapshotForTests(
            vertex,
            index,
            VertexFormats.POSITION_COLOR,
            drawMode,
            vertexCount,
            indexCount,
            indexType
        );
    }

    private static ByteBuffer buffer(int size, byte seed) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size);
        for (int i = 0; i < size; i++) {
            byteBuffer.put((byte) (seed + i));
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    private static final class RecordingNativeBackend implements MetalBufferUploadBridge.NativeBufferBackend {
        private long nextHandle = 1;
        private int createCalls;
        private int updateCalls;
        private int destroyCalls;
        private int registerDescriptorCalls;
        private int drawCalls;
        private int drawIndexedCalls;
        private final List<Integer> createdSizes = new ArrayList<>();

        @Override
        public long createBuffer(int usage, int size, @Nullable ByteBuffer initialData, int initialDataLength) {
            createCalls++;
            createdSizes.add(size);
            return nextHandle++;
        }

        @Override
        public int updateBuffer(long handle, int offset, ByteBuffer data, int dataLength) {
            updateCalls++;
            return 0;
        }

        @Override
        public int destroyBuffer(long handle) {
            destroyCalls++;
            return 0;
        }

        @Override
        public long registerVertexDescriptor(int strideBytes, int attributeCount, ByteBuffer packedElements, int packedByteLength) {
            registerDescriptorCalls++;
            return nextHandle++;
        }

        @Override
        public int draw(int mode, int first, int count) {
            drawCalls++;
            return 0;
        }

        @Override
        public int drawIndexed(int mode, int count, int indexType) {
            drawIndexedCalls++;
            return 0;
        }
    }
}
