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

    public static native void nativeShutdown();
}
