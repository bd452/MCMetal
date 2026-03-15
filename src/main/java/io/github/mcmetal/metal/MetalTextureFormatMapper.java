package io.github.mcmetal.metal;

/**
 * Maps OpenGL texture upload tuples to native texture pixel format identifiers.
 */
public final class MetalTextureFormatMapper {
    public static final int GL_RED = 0x1903;
    public static final int GL_RG = 0x8227;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_BGRA = 0x80E1;
    public static final int GL_DEPTH_STENCIL = 0x84F9;

    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_HALF_FLOAT = 0x140B;
    public static final int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;
    public static final int GL_UNSIGNED_INT_24_8 = 0x84FA;
    public static final int GL_FLOAT_32_UNSIGNED_INT_24_8_REV = 0x8DAD;

    public static final int GL_R8 = 0x8229;
    public static final int GL_RG8 = 0x822B;
    public static final int GL_RGBA8 = 0x8058;
    public static final int GL_SRGB8_ALPHA8 = 0x8C43;
    public static final int GL_RGBA16F = 0x881A;
    public static final int GL_RGBA32F = 0x8814;
    public static final int GL_DEPTH24_STENCIL8 = 0x88F0;
    public static final int GL_DEPTH32F_STENCIL8 = 0x8CAD;

    public static final int NATIVE_TEXTURE_FORMAT_R8_UNORM = 1;
    public static final int NATIVE_TEXTURE_FORMAT_RG8_UNORM = 2;
    public static final int NATIVE_TEXTURE_FORMAT_RGBA8_UNORM = 3;
    public static final int NATIVE_TEXTURE_FORMAT_BGRA8_UNORM = 4;
    public static final int NATIVE_TEXTURE_FORMAT_RGBA8_UNORM_SRGB = 5;
    public static final int NATIVE_TEXTURE_FORMAT_RGBA16_FLOAT = 6;
    public static final int NATIVE_TEXTURE_FORMAT_RGBA32_FLOAT = 7;
    public static final int NATIVE_TEXTURE_FORMAT_DEPTH24_STENCIL8 = 8;
    public static final int NATIVE_TEXTURE_FORMAT_DEPTH32F_STENCIL8 = 9;

    private MetalTextureFormatMapper() {
    }

    public static MappedTextureFormat mapOrThrow(int internalFormat, int format, int type) {
        MappedTextureFormat mapped = mapInternalFormat(internalFormat);
        if (mapped == null) {
            mapped = mapUnsized(format, type);
        }
        if (mapped == null) {
            throw new IllegalArgumentException(
                "Unsupported texture format tuple internalFormat=0x"
                    + Integer.toHexString(internalFormat)
                    + " format=0x"
                    + Integer.toHexString(format)
                    + " type=0x"
                    + Integer.toHexString(type)
            );
        }
        return mapped;
    }

    private static MappedTextureFormat mapInternalFormat(int internalFormat) {
        return switch (internalFormat) {
            case GL_R8 -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_R8_UNORM, 1, false, false);
            case GL_RG8 -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RG8_UNORM, 2, false, false);
            case GL_RGBA8 -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RGBA8_UNORM, 4, false, false);
            case GL_SRGB8_ALPHA8 -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RGBA8_UNORM_SRGB, 4, true, false);
            case GL_RGBA16F -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RGBA16_FLOAT, 8, false, false);
            case GL_RGBA32F -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RGBA32_FLOAT, 16, false, false);
            case GL_DEPTH24_STENCIL8 -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_DEPTH24_STENCIL8, 4, false, true);
            case GL_DEPTH32F_STENCIL8 -> new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_DEPTH32F_STENCIL8, 8, false, true);
            default -> null;
        };
    }

    private static MappedTextureFormat mapUnsized(int format, int type) {
        if (format == GL_RED && type == GL_UNSIGNED_BYTE) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_R8_UNORM, 1, false, false);
        }
        if (format == GL_RG && type == GL_UNSIGNED_BYTE) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RG8_UNORM, 2, false, false);
        }
        if (format == GL_RGBA && type == GL_UNSIGNED_BYTE) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RGBA8_UNORM, 4, false, false);
        }
        if (format == GL_BGRA && (type == GL_UNSIGNED_BYTE || type == GL_UNSIGNED_INT_8_8_8_8_REV)) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_BGRA8_UNORM, 4, false, false);
        }
        if (format == GL_RGBA && type == GL_HALF_FLOAT) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RGBA16_FLOAT, 8, false, false);
        }
        if (format == GL_RGBA && type == GL_FLOAT) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_RGBA32_FLOAT, 16, false, false);
        }
        if (format == GL_DEPTH_STENCIL && type == GL_UNSIGNED_INT_24_8) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_DEPTH24_STENCIL8, 4, false, true);
        }
        if (format == GL_DEPTH_STENCIL && type == GL_FLOAT_32_UNSIGNED_INT_24_8_REV) {
            return new MappedTextureFormat(NATIVE_TEXTURE_FORMAT_DEPTH32F_STENCIL8, 8, false, true);
        }
        return null;
    }

    public record MappedTextureFormat(
        int nativePixelFormat,
        int bytesPerPixel,
        boolean srgb,
        boolean depthStencil
    ) {
    }
}
