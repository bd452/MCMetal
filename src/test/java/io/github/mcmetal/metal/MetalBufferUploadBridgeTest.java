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

        assertEquals(2, backend.destroyCalls);
    }

    private static MetalBufferUploadBridge.UploadSnapshot snapshot(int vertexBytes, int indexBytes) {
        ByteBuffer vertex = buffer(vertexBytes, (byte) 7);
        ByteBuffer index = indexBytes > 0 ? buffer(indexBytes, (byte) 42) : null;
        return MetalBufferUploadBridge.createSnapshotForTests(
            vertex,
            index,
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            4,
            6,
            VertexFormat.IndexType.SHORT
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
    }
}
