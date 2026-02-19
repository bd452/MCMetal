package io.github.mcmetal.metal;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetalVertexDescriptorMapperTest {
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_BYTE = 0x1400;

    @Test
    void mapsPositionAndColorElementsWithExpectedSemantics() {
        MetalVertexDescriptorMapper.NativeVertexDescriptor descriptor =
            MetalVertexDescriptorMapper.map(VertexFormats.POSITION_COLOR);

        assertEquals(VertexFormats.POSITION_COLOR.getVertexSizeByte(), descriptor.strideBytes());
        assertEquals(2, descriptor.attributeCount());

        int[] first = element(descriptor.packedElements(), 0);
        int[] second = element(descriptor.packedElements(), 1);

        assertEquals(0, first[0]);
        assertEquals(MetalVertexDescriptorMapper.USAGE_POSITION, first[1]);
        assertEquals(GL_FLOAT, first[2]);
        assertEquals(3, first[3]);
        assertEquals(0, first[4]);
        assertEquals(0, first[5]);

        assertEquals(1, second[0]);
        assertEquals(MetalVertexDescriptorMapper.USAGE_COLOR, second[1]);
        assertEquals(GL_UNSIGNED_BYTE, second[2]);
        assertEquals(4, second[3]);
        assertEquals(12, second[4]);
        assertEquals(1, second[5]);
    }

    @Test
    void marksNormalAttributesAsNormalized() {
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
        MetalVertexDescriptorMapper.NativeVertexDescriptor descriptor = MetalVertexDescriptorMapper.map(format);

        int normalAttributeIndex = findUsage(descriptor.packedElements(), MetalVertexDescriptorMapper.USAGE_NORMAL);
        int[] normal = element(descriptor.packedElements(), normalAttributeIndex);

        assertEquals(GL_BYTE, normal[2]);
        assertEquals(3, normal[3]);
        assertEquals(1, normal[5]);
    }

    @Test
    void emitsSequentialAttributeIndices() {
        MetalVertexDescriptorMapper.NativeVertexDescriptor descriptor =
            MetalVertexDescriptorMapper.map(VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i < descriptor.attributeCount(); i++) {
            assertEquals(i, element(descriptor.packedElements(), i)[0]);
        }
    }

    private static int findUsage(ByteBuffer payload, int expectedUsage) {
        int attributeCount = payload.remaining() / (Integer.BYTES * 7);
        for (int i = 0; i < attributeCount; i++) {
            if (element(payload, i)[1] == expectedUsage) {
                return i;
            }
        }
        throw new AssertionError("Expected usage " + expectedUsage + " not found.");
    }

    private static int[] element(ByteBuffer payload, int attributeIndex) {
        ByteBuffer copy = payload.duplicate().order(ByteOrder.nativeOrder());
        int baseIntIndex = attributeIndex * 7;
        int[] values = new int[7];
        for (int i = 0; i < values.length; i++) {
            values[i] = copy.getInt((baseIntIndex + i) * Integer.BYTES);
        }
        return values;
    }
}
