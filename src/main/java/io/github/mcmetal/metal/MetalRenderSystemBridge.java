package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;

/**
 * Thin Java-side bridge used by RenderSystem mixins.
 *
 * <p>Phase 2 progressively wires these callbacks to the native state tracker.
 */
public final class MetalRenderSystemBridge {
    private static volatile boolean blendEnabled;
    private static volatile int blendSrcRgb = 1;
    private static volatile int blendDstRgb;
    private static volatile int blendSrcAlpha = 1;
    private static volatile int blendDstAlpha;
    private static volatile int blendEquationRgb = 0x8006;
    private static volatile int blendEquationAlpha = 0x8006;

    private static volatile boolean depthTestEnabled;
    private static volatile boolean depthWriteMask = true;
    private static volatile int depthCompareFunction = 0x0203;

    private static volatile boolean stencilEnabled;
    private static volatile int stencilFunction = 0x0207;
    private static volatile int stencilReference;
    private static volatile int stencilCompareMask = 0xFF;
    private static volatile int stencilWriteMask = 0xFF;
    private static volatile int stencilSFail = 0x1E00;
    private static volatile int stencilDpFail = 0x1E00;
    private static volatile int stencilDpPass = 0x1E00;

    private static volatile boolean cullEnabled = true;
    private static volatile int cullMode = 0x0405;

    private static volatile boolean scissorEnabled;
    private static volatile int scissorX;
    private static volatile int scissorY;
    private static volatile int scissorWidth = 1;
    private static volatile int scissorHeight = 1;

    private static volatile int viewportX;
    private static volatile int viewportY;
    private static volatile int viewportWidth = 1;
    private static volatile int viewportHeight = 1;

    private MetalRenderSystemBridge() {
    }

    public static void onEnableBlend() {
        if (!isBridgeActive()) {
            return;
        }
        blendEnabled = true;
    }

    public static void onDisableBlend() {
        if (!isBridgeActive()) {
            return;
        }
        blendEnabled = false;
    }

    public static void onBlendFunc(int srcFactor, int dstFactor) {
        if (!isBridgeActive()) {
            return;
        }
        blendSrcRgb = srcFactor;
        blendDstRgb = dstFactor;
        blendSrcAlpha = srcFactor;
        blendDstAlpha = dstFactor;
    }

    public static void onBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (!isBridgeActive()) {
            return;
        }
        blendSrcRgb = srcRgb;
        blendDstRgb = dstRgb;
        blendSrcAlpha = srcAlpha;
        blendDstAlpha = dstAlpha;
    }

    public static void onBlendEquation(int mode) {
        if (!isBridgeActive()) {
            return;
        }
        blendEquationRgb = mode;
        blendEquationAlpha = mode;
    }

    public static void onEnableDepthTest() {
        if (!isBridgeActive()) {
            return;
        }
        depthTestEnabled = true;
    }

    public static void onDisableDepthTest() {
        if (!isBridgeActive()) {
            return;
        }
        depthTestEnabled = false;
    }

    public static void onDepthFunc(int function) {
        if (!isBridgeActive()) {
            return;
        }
        depthCompareFunction = function;
    }

    public static void onDepthMask(boolean mask) {
        if (!isBridgeActive()) {
            return;
        }
        depthWriteMask = mask;
    }

    public static void onEnableCull() {
        if (!isBridgeActive()) {
            return;
        }
        cullEnabled = true;
    }

    public static void onDisableCull() {
        if (!isBridgeActive()) {
            return;
        }
        cullEnabled = false;
    }

    public static void onEnableScissor(int x, int y, int width, int height) {
        if (!isBridgeActive()) {
            return;
        }
        scissorEnabled = true;
        scissorX = x;
        scissorY = y;
        scissorWidth = Math.max(width, 1);
        scissorHeight = Math.max(height, 1);
    }

    public static void onDisableScissor() {
        if (!isBridgeActive()) {
            return;
        }
        scissorEnabled = false;
    }

    public static void onViewport(int x, int y, int width, int height) {
        if (!isBridgeActive()) {
            return;
        }
        viewportX = x;
        viewportY = y;
        viewportWidth = Math.max(width, 1);
        viewportHeight = Math.max(height, 1);
    }

    public static void onStencilFunc(int function, int reference, int mask) {
        if (!isBridgeActive()) {
            return;
        }
        stencilEnabled = true;
        stencilFunction = function;
        stencilReference = reference;
        stencilCompareMask = mask;
    }

    public static void onStencilMask(int mask) {
        if (!isBridgeActive()) {
            return;
        }
        stencilEnabled = true;
        stencilWriteMask = mask;
    }

    public static void onStencilOp(int sfail, int dpfail, int dppass) {
        if (!isBridgeActive()) {
            return;
        }
        stencilEnabled = true;
        stencilSFail = sfail;
        stencilDpFail = dpfail;
        stencilDpPass = dppass;
    }

    public static void onDrawElements(int mode, int count, int indexType) {
        if (!isBridgeActive() || count <= 0) {
            return;
        }
        // Draw forwarding is added in subsequent Phase 2 checkpoints.
    }

    private static boolean isBridgeActive() {
        return HostPlatform.isMacOs() && MetalPhaseOneBridge.isInitialized();
    }
}
