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

    public static native void nativeShutdown();
}
