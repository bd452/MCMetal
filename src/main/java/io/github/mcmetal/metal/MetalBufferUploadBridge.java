package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;
import io.github.mcmetal.metal.bridge.NativeApi;
import io.github.mcmetal.metal.bridge.NativeBridgeException;
import io.github.mcmetal.metal.bridge.NativeStatus;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Phase 3 bridge for BufferBuilder/VertexBuffer upload flows.
 */
public final class MetalBufferUploadBridge {
    interface NativeBufferBackend {
        long createBuffer(int usage, int size, @Nullable ByteBuffer initialData, int initialDataLength);

        int updateBuffer(long handle, int offset, ByteBuffer data, int dataLength);

        int destroyBuffer(long handle);
    }

    enum BufferUsage {
        STATIC(0),
        DYNAMIC(1);

        private final int nativeValue;

        BufferUsage(int nativeValue) {
            this.nativeValue = nativeValue;
        }
    }

    static final class UploadSnapshot {
        final ByteBuffer vertexData;
        @Nullable
        final ByteBuffer indexData;
        final VertexFormat format;
        final int modeGl;
        final int vertexCount;
        final int indexCount;
        final int indexTypeGl;

        UploadSnapshot(
            ByteBuffer vertexData,
            @Nullable ByteBuffer indexData,
            VertexFormat format,
            int modeGl,
            int vertexCount,
            int indexCount,
            int indexTypeGl
        ) {
            this.vertexData = vertexData;
            this.indexData = indexData;
            this.format = format;
            this.modeGl = modeGl;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            this.indexTypeGl = indexTypeGl;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MetalBufferUploadBridge.class);
    private static final boolean DEBUG_BUFFER_LOGS = Boolean.getBoolean("mcmetal.phase3.debugBufferBridge");
    private static final Map<Object, UploadSnapshot> SNAPSHOT_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, NativeBufferRecord> BUFFER_RECORDS = Collections.synchronizedMap(new IdentityHashMap<>());

    private static volatile NativeBufferBackend nativeBufferBackend = new JniNativeBufferBackend();
    private static volatile Boolean bridgeActiveOverrideForTests;

    private MetalBufferUploadBridge() {
    }

    public static void onBufferBuilderEnd(@Nullable BuiltBuffer builtBuffer) {
        if (!isBridgeActive() || builtBuffer == null) {
            return;
        }
        UploadSnapshot snapshot = snapshotFromBuiltBuffer(builtBuffer);
        if (snapshot == null) {
            return;
        }
        SNAPSHOT_CACHE.put(builtBuffer, snapshot);
        if (DEBUG_BUFFER_LOGS) {
            LOGGER.debug(
                "event=metal_phase3 phase=buffer_builder_end vertex_bytes={} index_bytes={} mode={} vertex_count={} index_count={}",
                snapshot.vertexData.remaining(),
                snapshot.indexData == null ? 0 : snapshot.indexData.remaining(),
                snapshot.modeGl,
                snapshot.vertexCount,
                snapshot.indexCount
            );
        }
    }

    public static void onVertexBufferUpload(VertexBuffer vertexBuffer, VertexBuffer.Usage usage, BuiltBuffer builtBuffer) {
        if (!isBridgeActive()) {
            return;
        }
        UploadSnapshot fallbackSnapshot = snapshotFromBuiltBuffer(builtBuffer);
        if (fallbackSnapshot == null) {
            return;
        }
        onVertexBufferUploadInternal(vertexBuffer, mapUsage(usage), builtBuffer, fallbackSnapshot);
    }

    public static void onVertexBufferClose(VertexBuffer vertexBuffer) {
        if (!isBridgeActive()) {
            return;
        }
        closeRecord(vertexBuffer);
    }

    static void onVertexBufferUploadForTests(
        Object vertexBufferIdentity,
        BufferUsage usage,
        Object snapshotKey,
        UploadSnapshot fallbackSnapshot
    ) {
        onVertexBufferUploadInternal(vertexBufferIdentity, usage, snapshotKey, fallbackSnapshot);
    }

    static UploadSnapshot createSnapshotForTests(
        ByteBuffer vertexData,
        @Nullable ByteBuffer indexData,
        VertexFormat format,
        VertexFormat.DrawMode drawMode,
        int vertexCount,
        int indexCount,
        VertexFormat.IndexType indexType
    ) {
        return new UploadSnapshot(
            toDirectCopy(vertexData),
            toDirectCopy(indexData),
            format,
            drawMode.glMode,
            vertexCount,
            indexCount,
            indexType.glType
        );
    }

    static void rememberSnapshotForTests(Object snapshotKey, UploadSnapshot snapshot) {
        SNAPSHOT_CACHE.put(snapshotKey, snapshot);
    }

    static void setNativeBufferBackendForTests(NativeBufferBackend backend) {
        nativeBufferBackend = backend;
    }

    static void setBridgeActiveForTests(boolean active) {
        bridgeActiveOverrideForTests = active;
    }

    static void onVertexBufferCloseForTests(Object vertexBufferIdentity) {
        closeRecord(vertexBufferIdentity);
    }

    static void clearBridgeActiveOverrideForTests() {
        bridgeActiveOverrideForTests = null;
    }

    static void resetForTests() {
        SNAPSHOT_CACHE.clear();
        BUFFER_RECORDS.clear();
        nativeBufferBackend = new JniNativeBufferBackend();
        bridgeActiveOverrideForTests = null;
    }

    private static void onVertexBufferUploadInternal(
        Object vertexBufferIdentity,
        BufferUsage usage,
        Object snapshotKey,
        UploadSnapshot fallbackSnapshot
    ) {
        UploadSnapshot snapshot = SNAPSHOT_CACHE.remove(snapshotKey);
        if (snapshot == null) {
            snapshot = fallbackSnapshot;
        }
        if (snapshot.vertexData.remaining() <= 0) {
            return;
        }

        NativeBufferRecord record = BUFFER_RECORDS.computeIfAbsent(vertexBufferIdentity, key -> new NativeBufferRecord());
        record.vertexAllocation = uploadAllocation(record.vertexAllocation, usage, snapshot.vertexData, "vertex");

        if (snapshot.indexData != null && snapshot.indexData.remaining() > 0) {
            record.indexAllocation = uploadAllocation(record.indexAllocation, usage, snapshot.indexData, "index");
        } else {
            destroyAllocation(record.indexAllocation, "nativeDestroyBuffer(index)");
            record.indexAllocation = new BufferAllocation();
        }

        record.lastSnapshot = snapshot;
        if (DEBUG_BUFFER_LOGS) {
            LOGGER.debug(
                "event=metal_phase3 phase=vertex_upload usage={} vertex_handle={} vertex_bytes={} index_handle={} index_bytes={} mode={} vertex_count={} index_count={} index_type={}",
                usage,
                record.vertexAllocation.handle,
                snapshot.vertexData.remaining(),
                record.indexAllocation.handle,
                snapshot.indexData == null ? 0 : snapshot.indexData.remaining(),
                snapshot.modeGl,
                snapshot.vertexCount,
                snapshot.indexCount,
                snapshot.indexTypeGl
            );
        }
    }

    private static BufferAllocation uploadAllocation(
        BufferAllocation current,
        BufferUsage usage,
        ByteBuffer data,
        String label
    ) {
        ByteBuffer payload = data.duplicate();
        int requiredBytes = payload.remaining();
        if (requiredBytes <= 0) {
            return current;
        }

        if (current.handle == 0L || current.capacityBytes < requiredBytes) {
            long previousHandle = current.handle;
            long newHandle = nativeBufferBackend.createBuffer(
                usage.nativeValue,
                requiredBytes,
                payload,
                requiredBytes
            );
            if (newHandle <= 0L) {
                throw new NativeBridgeException(
                    "Native operation nativeCreateBuffer failed for " + label + " upload (size=" + requiredBytes + ")."
                );
            }
            current.handle = newHandle;
            current.capacityBytes = requiredBytes;
            if (previousHandle != 0L) {
                requireSuccess("nativeDestroyBuffer(reallocate:" + label + ")", nativeBufferBackend.destroyBuffer(previousHandle));
            }
            return current;
        }

        int updateStatus = nativeBufferBackend.updateBuffer(current.handle, 0, payload, requiredBytes);
        requireSuccess("nativeUpdateBuffer(" + label + ")", updateStatus);
        return current;
    }

    private static void closeRecord(Object vertexBufferIdentity) {
        NativeBufferRecord record = BUFFER_RECORDS.remove(vertexBufferIdentity);
        if (record == null) {
            return;
        }
        destroyAllocation(record.vertexAllocation, "nativeDestroyBuffer(vertex)");
        destroyAllocation(record.indexAllocation, "nativeDestroyBuffer(index)");
    }

    private static void destroyAllocation(BufferAllocation allocation, String operation) {
        if (allocation.handle == 0L) {
            return;
        }
        requireSuccess(operation, nativeBufferBackend.destroyBuffer(allocation.handle));
        allocation.handle = 0L;
        allocation.capacityBytes = 0;
    }

    @Nullable
    private static UploadSnapshot snapshotFromBuiltBuffer(BuiltBuffer builtBuffer) {
        BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
        ByteBuffer vertexData = toDirectCopy(builtBuffer.getBuffer());
        if (vertexData == null || vertexData.remaining() <= 0) {
            return null;
        }
        ByteBuffer indexData = toDirectCopy(builtBuffer.getSortedBuffer());
        return new UploadSnapshot(
            vertexData,
            indexData,
            drawParameters.format(),
            drawParameters.mode().glMode,
            drawParameters.vertexCount(),
            drawParameters.indexCount(),
            drawParameters.indexType().glType
        );
    }

    @Nullable
    private static ByteBuffer toDirectCopy(@Nullable ByteBuffer source) {
        if (source == null) {
            return null;
        }
        ByteBuffer duplicate = source.duplicate();
        ByteBuffer copy = ByteBuffer.allocateDirect(duplicate.remaining()).order(source.order());
        copy.put(duplicate);
        copy.flip();
        return copy;
    }

    private static BufferUsage mapUsage(VertexBuffer.Usage usage) {
        return switch (usage) {
            case STATIC -> BufferUsage.STATIC;
            case DYNAMIC -> BufferUsage.DYNAMIC;
        };
    }

    private static boolean isBridgeActive() {
        Boolean bridgeActiveOverride = bridgeActiveOverrideForTests;
        if (bridgeActiveOverride != null) {
            return bridgeActiveOverride;
        }
        return HostPlatform.isMacOs() && MetalPhaseOneBridge.isInitialized();
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

    private static final class NativeBufferRecord {
        private BufferAllocation vertexAllocation = new BufferAllocation();
        private BufferAllocation indexAllocation = new BufferAllocation();
        private UploadSnapshot lastSnapshot;
    }

    private static final class BufferAllocation {
        private long handle;
        private int capacityBytes;
    }

    private static final class JniNativeBufferBackend implements NativeBufferBackend {
        @Override
        public long createBuffer(int usage, int size, @Nullable ByteBuffer initialData, int initialDataLength) {
            return NativeApi.nativeCreateBuffer(usage, size, initialData, initialDataLength);
        }

        @Override
        public int updateBuffer(long handle, int offset, ByteBuffer data, int dataLength) {
            return NativeApi.nativeUpdateBuffer(handle, offset, data, dataLength);
        }

        @Override
        public int destroyBuffer(long handle) {
            return NativeApi.nativeDestroyBuffer(handle);
        }
    }
}
