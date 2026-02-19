package io.github.mcmetal.metal.bridge;

/**
 * Java declarations for MCMetal's JNI bridge surface.
 */
public final class NativeApi {
    public static final String LIBRARY_BASE_NAME = "minecraft_metal";

    private NativeApi() {
    }

    public static native String nativeGetBridgeVersion();

    public static native int nativeInitialize(long cocoaWindowHandle, int width, int height, int debugFlags);

    public static native int nativeResize(int width, int height, float scaleFactor, boolean fullscreen);

    public static native int nativeRenderDemoFrame(float red, float green, float blue, float alpha);

    public static native int nativeSetBlendEnabled(boolean enabled);

    public static native int nativeSetBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    public static native int nativeSetBlendEquation(int rgbEquation, int alphaEquation);

    public static native int nativeSetDepthState(boolean depthTestEnabled, boolean depthWriteEnabled, int depthCompareFunction);

    public static native int nativeSetStencilState(
        boolean stencilEnabled,
        int stencilFunction,
        int stencilReference,
        int stencilCompareMask,
        int stencilWriteMask,
        int stencilSFail,
        int stencilDpFail,
        int stencilDpPass
    );

    public static native int nativeSetCullState(boolean cullEnabled, int cullMode);

    public static native int nativeSetScissorState(boolean scissorEnabled, int x, int y, int width, int height);

    public static native int nativeSetViewportState(int x, int y, int width, int height, float minDepth, float maxDepth);

    public static native int nativeDrawIndexed(int mode, int count, int indexType);

    public static native void nativeShutdown();
}
