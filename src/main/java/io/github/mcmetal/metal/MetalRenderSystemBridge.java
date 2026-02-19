package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;
import io.github.mcmetal.metal.bridge.NativeApi;
import io.github.mcmetal.metal.bridge.NativeBridgeException;
import io.github.mcmetal.metal.bridge.NativeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntSupplier;

/**
 * Thin Java-side bridge used by RenderSystem mixins.
 *
 * <p>Phase 2 progressively wires these callbacks to the native state tracker.
 */
public final class MetalRenderSystemBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetalRenderSystemBridge.class);
    private static final boolean DRAW_SUBMISSION_ENABLED = Boolean.getBoolean("mcmetal.phase2.enableDrawSubmission");
    private static final boolean DEBUG_STATE_LOGS = Boolean.getBoolean("mcmetal.phase2.debugStateTransitions");

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
        if (blendEnabled) {
            return;
        }
        blendEnabled = true;
        submitState("nativeSetBlendEnabled", () -> NativeApi.nativeSetBlendEnabled(blendEnabled));
    }

    public static void onDisableBlend() {
        if (!isBridgeActive()) {
            return;
        }
        if (!blendEnabled) {
            return;
        }
        blendEnabled = false;
        submitState("nativeSetBlendEnabled", () -> NativeApi.nativeSetBlendEnabled(blendEnabled));
    }

    public static void onBlendFunc(int srcFactor, int dstFactor) {
        if (!isBridgeActive()) {
            return;
        }
        if (blendSrcRgb == srcFactor && blendDstRgb == dstFactor
            && blendSrcAlpha == srcFactor && blendDstAlpha == dstFactor) {
            return;
        }
        blendSrcRgb = srcFactor;
        blendDstRgb = dstFactor;
        blendSrcAlpha = srcFactor;
        blendDstAlpha = dstFactor;
        submitState(
            "nativeSetBlendFunc",
            () -> NativeApi.nativeSetBlendFunc(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
        );
    }

    public static void onBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (!isBridgeActive()) {
            return;
        }
        if (blendSrcRgb == srcRgb && blendDstRgb == dstRgb
            && blendSrcAlpha == srcAlpha && blendDstAlpha == dstAlpha) {
            return;
        }
        blendSrcRgb = srcRgb;
        blendDstRgb = dstRgb;
        blendSrcAlpha = srcAlpha;
        blendDstAlpha = dstAlpha;
        submitState(
            "nativeSetBlendFunc",
            () -> NativeApi.nativeSetBlendFunc(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
        );
    }

    public static void onBlendEquation(int mode) {
        if (!isBridgeActive()) {
            return;
        }
        if (blendEquationRgb == mode && blendEquationAlpha == mode) {
            return;
        }
        blendEquationRgb = mode;
        blendEquationAlpha = mode;
        submitState(
            "nativeSetBlendEquation",
            () -> NativeApi.nativeSetBlendEquation(blendEquationRgb, blendEquationAlpha)
        );
    }

    public static void onEnableDepthTest() {
        if (!isBridgeActive()) {
            return;
        }
        if (depthTestEnabled) {
            return;
        }
        depthTestEnabled = true;
        submitState(
            "nativeSetDepthState",
            () -> NativeApi.nativeSetDepthState(depthTestEnabled, depthWriteMask, depthCompareFunction)
        );
    }

    public static void onDisableDepthTest() {
        if (!isBridgeActive()) {
            return;
        }
        if (!depthTestEnabled) {
            return;
        }
        depthTestEnabled = false;
        submitState(
            "nativeSetDepthState",
            () -> NativeApi.nativeSetDepthState(depthTestEnabled, depthWriteMask, depthCompareFunction)
        );
    }

    public static void onDepthFunc(int function) {
        if (!isBridgeActive()) {
            return;
        }
        if (depthCompareFunction == function) {
            return;
        }
        depthCompareFunction = function;
        submitState(
            "nativeSetDepthState",
            () -> NativeApi.nativeSetDepthState(depthTestEnabled, depthWriteMask, depthCompareFunction)
        );
    }

    public static void onDepthMask(boolean mask) {
        if (!isBridgeActive()) {
            return;
        }
        if (depthWriteMask == mask) {
            return;
        }
        depthWriteMask = mask;
        submitState(
            "nativeSetDepthState",
            () -> NativeApi.nativeSetDepthState(depthTestEnabled, depthWriteMask, depthCompareFunction)
        );
    }

    public static void onEnableCull() {
        if (!isBridgeActive()) {
            return;
        }
        if (cullEnabled) {
            return;
        }
        cullEnabled = true;
        submitState("nativeSetCullState", () -> NativeApi.nativeSetCullState(cullEnabled, cullMode));
    }

    public static void onDisableCull() {
        if (!isBridgeActive()) {
            return;
        }
        if (!cullEnabled) {
            return;
        }
        cullEnabled = false;
        submitState("nativeSetCullState", () -> NativeApi.nativeSetCullState(cullEnabled, cullMode));
    }

    public static void onEnableScissor(int x, int y, int width, int height) {
        if (!isBridgeActive()) {
            return;
        }
        int clampedWidth = Math.max(width, 1);
        int clampedHeight = Math.max(height, 1);
        if (DEBUG_STATE_LOGS && (clampedWidth != width || clampedHeight != height)) {
            LOGGER.debug(
                "event=metal_phase2 phase=state_validation operation=scissor_clamp x={} y={} width={} height={} clamped_width={} clamped_height={}",
                x,
                y,
                width,
                height,
                clampedWidth,
                clampedHeight
            );
        }
        if (scissorEnabled && scissorX == x && scissorY == y
            && scissorWidth == clampedWidth && scissorHeight == clampedHeight) {
            return;
        }
        scissorEnabled = true;
        scissorX = x;
        scissorY = y;
        scissorWidth = clampedWidth;
        scissorHeight = clampedHeight;
        submitState(
            "nativeSetScissorState",
            () -> NativeApi.nativeSetScissorState(scissorEnabled, scissorX, scissorY, scissorWidth, scissorHeight)
        );
    }

    public static void onDisableScissor() {
        if (!isBridgeActive()) {
            return;
        }
        if (!scissorEnabled) {
            return;
        }
        scissorEnabled = false;
        submitState(
            "nativeSetScissorState",
            () -> NativeApi.nativeSetScissorState(scissorEnabled, scissorX, scissorY, scissorWidth, scissorHeight)
        );
    }

    public static void onViewport(int x, int y, int width, int height) {
        if (!isBridgeActive()) {
            return;
        }
        int clampedWidth = Math.max(width, 1);
        int clampedHeight = Math.max(height, 1);
        if (DEBUG_STATE_LOGS && (clampedWidth != width || clampedHeight != height)) {
            LOGGER.debug(
                "event=metal_phase2 phase=state_validation operation=viewport_clamp x={} y={} width={} height={} clamped_width={} clamped_height={}",
                x,
                y,
                width,
                height,
                clampedWidth,
                clampedHeight
            );
        }
        if (viewportX == x && viewportY == y && viewportWidth == clampedWidth && viewportHeight == clampedHeight) {
            return;
        }
        viewportX = x;
        viewportY = y;
        viewportWidth = clampedWidth;
        viewportHeight = clampedHeight;
        submitState(
            "nativeSetViewportState",
            () -> NativeApi.nativeSetViewportState(viewportX, viewportY, viewportWidth, viewportHeight, 0.0F, 1.0F)
        );
    }

    public static void onStencilFunc(int function, int reference, int mask) {
        if (!isBridgeActive()) {
            return;
        }
        if (stencilEnabled && stencilFunction == function
            && stencilReference == reference && stencilCompareMask == mask) {
            return;
        }
        stencilEnabled = true;
        stencilFunction = function;
        stencilReference = reference;
        stencilCompareMask = mask;
        submitState(
            "nativeSetStencilState",
            () -> NativeApi.nativeSetStencilState(
                stencilEnabled,
                stencilFunction,
                stencilReference,
                stencilCompareMask,
                stencilWriteMask,
                stencilSFail,
                stencilDpFail,
                stencilDpPass
            )
        );
    }

    public static void onStencilMask(int mask) {
        if (!isBridgeActive()) {
            return;
        }
        if (stencilEnabled && stencilWriteMask == mask) {
            return;
        }
        stencilEnabled = true;
        stencilWriteMask = mask;
        submitState(
            "nativeSetStencilState",
            () -> NativeApi.nativeSetStencilState(
                stencilEnabled,
                stencilFunction,
                stencilReference,
                stencilCompareMask,
                stencilWriteMask,
                stencilSFail,
                stencilDpFail,
                stencilDpPass
            )
        );
    }

    public static void onStencilOp(int sfail, int dpfail, int dppass) {
        if (!isBridgeActive()) {
            return;
        }
        if (stencilEnabled && stencilSFail == sfail && stencilDpFail == dpfail && stencilDpPass == dppass) {
            return;
        }
        stencilEnabled = true;
        stencilSFail = sfail;
        stencilDpFail = dpfail;
        stencilDpPass = dppass;
        submitState(
            "nativeSetStencilState",
            () -> NativeApi.nativeSetStencilState(
                stencilEnabled,
                stencilFunction,
                stencilReference,
                stencilCompareMask,
                stencilWriteMask,
                stencilSFail,
                stencilDpFail,
                stencilDpPass
            )
        );
    }

    public static void onDrawElements(int mode, int count, int indexType) {
        if (!isBridgeActive() || count <= 0) {
            return;
        }
        if (!DRAW_SUBMISSION_ENABLED) {
            return;
        }
        submitState("nativeDrawIndexed", () -> NativeApi.nativeDrawIndexed(mode, count, indexType));
    }

    private static boolean isBridgeActive() {
        return HostPlatform.isMacOs() && MetalPhaseOneBridge.isInitialized();
    }

    private static void submitState(String operation, IntSupplier nativeCall) {
        if (DEBUG_STATE_LOGS) {
            LOGGER.debug("event=metal_phase2 phase=state_submit operation={}", operation);
        }
        int statusCode = nativeCall.getAsInt();
        if (NativeStatus.isSuccess(statusCode)) {
            return;
        }
        throw new NativeBridgeException(
            "Native operation " + operation + " failed with status "
                + NativeStatus.describe(statusCode) + " (" + statusCode + ")."
        );
    }
}
