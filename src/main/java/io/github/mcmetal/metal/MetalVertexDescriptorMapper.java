package io.github.mcmetal.metal;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Maps Blaze3D vertex formats to a packed native descriptor payload.
 */
public final class MetalVertexDescriptorMapper {
    public static final int USAGE_POSITION = 0;
    public static final int USAGE_NORMAL = 1;
    public static final int USAGE_COLOR = 2;
    public static final int USAGE_UV = 3;
    public static final int USAGE_GENERIC = 4;

    private static final int PACKED_INTS_PER_ATTRIBUTE = 7;

    private MetalVertexDescriptorMapper() {
    }

    public static NativeVertexDescriptor map(VertexFormat format) {
        List<VertexFormatElement> elements = format.getElements();
        ByteBuffer packedElements = ByteBuffer
            .allocateDirect(elements.size() * PACKED_INTS_PER_ATTRIBUTE * Integer.BYTES)
            .order(ByteOrder.nativeOrder());

        for (int attributeIndex = 0; attributeIndex < elements.size(); attributeIndex++) {
            VertexFormatElement element = elements.get(attributeIndex);
            packedElements.putInt(attributeIndex);
            packedElements.putInt(mapUsage(element.usage()));
            packedElements.putInt(element.type().getGlType());
            packedElements.putInt(element.count());
            packedElements.putInt(format.getOffset(element));
            packedElements.putInt(isNormalized(element) ? 1 : 0);
            packedElements.putInt(element.uvIndex());
        }

        packedElements.flip();
        ByteBuffer payload = packedElements.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        payload.position(0);
        return new NativeVertexDescriptor(
            format.getVertexSizeByte(),
            elements.size(),
            payload,
            payload.remaining()
        );
    }

    private static int mapUsage(VertexFormatElement.Usage usage) {
        return switch (usage) {
            case POSITION -> USAGE_POSITION;
            case NORMAL -> USAGE_NORMAL;
            case COLOR -> USAGE_COLOR;
            case UV -> USAGE_UV;
            case GENERIC -> USAGE_GENERIC;
        };
    }

    private static boolean isNormalized(VertexFormatElement element) {
        return switch (element.usage()) {
            case COLOR, NORMAL -> true;
            case POSITION, UV, GENERIC -> false;
        };
    }

    public record NativeVertexDescriptor(
        int strideBytes,
        int attributeCount,
        ByteBuffer packedElements,
        int byteLength
    ) {
    }
}
