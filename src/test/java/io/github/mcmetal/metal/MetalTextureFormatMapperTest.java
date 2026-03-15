package io.github.mcmetal.metal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetalTextureFormatMapperTest {
    @Test
    void mapsSizedRgba8FormatToNativeRgba8() {
        MetalTextureFormatMapper.MappedTextureFormat mapped = MetalTextureFormatMapper.mapOrThrow(
            MetalTextureFormatMapper.GL_RGBA8,
            MetalTextureFormatMapper.GL_RGBA,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE
        );

        assertEquals(MetalTextureFormatMapper.NATIVE_TEXTURE_FORMAT_RGBA8_UNORM, mapped.nativePixelFormat());
        assertEquals(4, mapped.bytesPerPixel());
        assertTrue(!mapped.depthStencil());
    }

    @Test
    void mapsUnsizedBgraTupleToNativeBgra8() {
        MetalTextureFormatMapper.MappedTextureFormat mapped = MetalTextureFormatMapper.mapOrThrow(
            0,
            MetalTextureFormatMapper.GL_BGRA,
            MetalTextureFormatMapper.GL_UNSIGNED_INT_8_8_8_8_REV
        );

        assertEquals(MetalTextureFormatMapper.NATIVE_TEXTURE_FORMAT_BGRA8_UNORM, mapped.nativePixelFormat());
        assertEquals(4, mapped.bytesPerPixel());
    }

    @Test
    void mapsDepthStencilFormatToNativeDepthStencilPixelFormat() {
        MetalTextureFormatMapper.MappedTextureFormat mapped = MetalTextureFormatMapper.mapOrThrow(
            MetalTextureFormatMapper.GL_DEPTH24_STENCIL8,
            MetalTextureFormatMapper.GL_DEPTH_STENCIL,
            MetalTextureFormatMapper.GL_UNSIGNED_INT_24_8
        );

        assertEquals(MetalTextureFormatMapper.NATIVE_TEXTURE_FORMAT_DEPTH24_STENCIL8, mapped.nativePixelFormat());
        assertEquals(4, mapped.bytesPerPixel());
        assertTrue(mapped.depthStencil());
    }

    @Test
    void mapsSrgbInternalFormatToNativeRgbaSrgb() {
        MetalTextureFormatMapper.MappedTextureFormat mapped = MetalTextureFormatMapper.mapOrThrow(
            MetalTextureFormatMapper.GL_SRGB8_ALPHA8,
            MetalTextureFormatMapper.GL_RGBA,
            MetalTextureFormatMapper.GL_UNSIGNED_BYTE
        );

        assertEquals(MetalTextureFormatMapper.NATIVE_TEXTURE_FORMAT_RGBA8_UNORM_SRGB, mapped.nativePixelFormat());
        assertEquals(4, mapped.bytesPerPixel());
        assertTrue(mapped.srgb());
    }

    @Test
    void rejectsUnsupportedTextureFormatTuple() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MetalTextureFormatMapper.mapOrThrow(
                MetalTextureFormatMapper.GL_RGBA,
                MetalTextureFormatMapper.GL_RGBA,
                MetalTextureFormatMapper.GL_UNSIGNED_INT_24_8
            )
        );
    }
}
