package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.HostPlatform;
import io.github.mcmetal.metal.bridge.NativeApi;
import io.github.mcmetal.metal.bridge.NativeBridgeException;
import io.github.mcmetal.metal.bridge.NativeDebugFlags;
import io.github.mcmetal.metal.bridge.NativeStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntSupplier;

/**
 * Coordinates Phase 1 bring-up for window/surface integration.
 */
public final class MetalPhaseOneBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetalPhaseOneBridge.class);

    private static final float DEMO_CLEAR_RED = 0.04F;
    private static final float DEMO_CLEAR_GREEN = 0.07F;
    private static final float DEMO_CLEAR_BLUE = 0.12F;
    private static final float DEMO_CLEAR_ALPHA = 1.0F;
    private static final boolean DEMO_FRAME_ENABLED = !Boolean.getBoolean("mcmetal.phase1.disableDemoFrame");

    private static boolean initialized;
    private static long glfwWindowHandle;
    private static long cocoaWindowHandle;
    private static int framebufferWidth = -1;
    private static int framebufferHeight = -1;
    private static float scaleFactor = 1.0F;
    private static boolean fullscreen;

    private MetalPhaseOneBridge() {
    }

    public static void onClientStarted(MinecraftClient client) {
        if (!HostPlatform.isMacOs()) {
            return;
        }

        initialize(client.getWindow());
    }

    public static void onClientTick(MinecraftClient client) {
        if (!initialized) {
            return;
        }

        Window window = client.getWindow();
        try {
            syncDrawableState(window);
            if (DEMO_FRAME_ENABLED) {
                renderDemoFrame();
            }
        } catch (RuntimeException runtimeException) {
            LOGGER.error(
                "event=metal_phase1 phase=failure operation=tick_loop error_type={} error_message={}",
                runtimeException.getClass().getSimpleName(),
                runtimeException.getMessage(),
                runtimeException
            );
            shutdown();
            throw runtimeException;
        }
    }

    public static void onClientStopping(MinecraftClient client) {
        if (client == null) {
            return;
        }
        shutdown();
    }

    private static void initialize(Window window) {
        if (initialized) {
            return;
        }

        glfwWindowHandle = window.getHandle();
        if (glfwWindowHandle == 0L) {
            throw new NativeBridgeException("Unable to initialize Metal bridge: GLFW window handle is zero.");
        }

        cocoaWindowHandle = GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindowHandle);
        if (cocoaWindowHandle == 0L) {
            throw new NativeBridgeException("Unable to initialize Metal bridge: Cocoa NSWindow handle is zero.");
        }

        int width = sanitizeDimension(window.getFramebufferWidth());
        int height = sanitizeDimension(window.getFramebufferHeight());
        float windowScaleFactor = sanitizeScale(window.getScaleFactor());
        boolean windowFullscreen = window.isFullscreen();
        int debugFlags = NativeDebugFlags.fromSystemProperties();

        int initStatus = callNativeWithoutOpenGlContext(
            () -> NativeApi.nativeInitialize(cocoaWindowHandle, width, height, debugFlags)
        );
        requireSuccess("nativeInitialize", initStatus);

        int resizeStatus = callNativeWithoutOpenGlContext(
            () -> NativeApi.nativeResize(width, height, windowScaleFactor, windowFullscreen)
        );
        requireSuccess("nativeResize", resizeStatus);

        framebufferWidth = width;
        framebufferHeight = height;
        scaleFactor = windowScaleFactor;
        fullscreen = windowFullscreen;
        initialized = true;

        LOGGER.info(
            "event=metal_phase1 phase=initialized glfw_window_handle={} cocoa_window_handle={} framebuffer_width={} framebuffer_height={} scale_factor={} fullscreen={} debug_flags={}",
            glfwWindowHandle,
            cocoaWindowHandle,
            framebufferWidth,
            framebufferHeight,
            scaleFactor,
            fullscreen,
            debugFlags
        );
    }

    private static void syncDrawableState(Window window) {
        int nextFramebufferWidth = sanitizeDimension(window.getFramebufferWidth());
        int nextFramebufferHeight = sanitizeDimension(window.getFramebufferHeight());
        float nextScaleFactor = sanitizeScale(window.getScaleFactor());
        boolean nextFullscreen = window.isFullscreen();

        boolean changed = nextFramebufferWidth != framebufferWidth
            || nextFramebufferHeight != framebufferHeight
            || Float.compare(nextScaleFactor, scaleFactor) != 0
            || nextFullscreen != fullscreen;
        if (!changed) {
            return;
        }

        int resizeStatus = callNativeWithoutOpenGlContext(
            () -> NativeApi.nativeResize(nextFramebufferWidth, nextFramebufferHeight, nextScaleFactor, nextFullscreen)
        );
        requireSuccess("nativeResize", resizeStatus);

        framebufferWidth = nextFramebufferWidth;
        framebufferHeight = nextFramebufferHeight;
        scaleFactor = nextScaleFactor;
        fullscreen = nextFullscreen;

        LOGGER.info(
            "event=metal_phase1 phase=resize framebuffer_width={} framebuffer_height={} scale_factor={} fullscreen={}",
            framebufferWidth,
            framebufferHeight,
            scaleFactor,
            fullscreen
        );
    }

    private static void renderDemoFrame() {
        int renderStatus = callNativeWithoutOpenGlContext(
            () -> NativeApi.nativeRenderDemoFrame(
                DEMO_CLEAR_RED,
                DEMO_CLEAR_GREEN,
                DEMO_CLEAR_BLUE,
                DEMO_CLEAR_ALPHA
            )
        );
        requireSuccess("nativeRenderDemoFrame", renderStatus);
    }

    private static int callNativeWithoutOpenGlContext(IntSupplier nativeCall) {
        long previousContext = GLFW.glfwGetCurrentContext();
        boolean detachedContext = previousContext != 0L;
        if (detachedContext) {
            GLFW.glfwMakeContextCurrent(0L);
            LOGGER.debug(
                "event=metal_phase1 phase=gl_context_detach previous_context={}",
                previousContext
            );
        }

        try {
            return nativeCall.getAsInt();
        } finally {
            if (detachedContext) {
                GLFW.glfwMakeContextCurrent(previousContext);
                LOGGER.debug(
                    "event=metal_phase1 phase=gl_context_restore restored_context={}",
                    previousContext
                );
            }
        }
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

    private static int sanitizeDimension(int dimension) {
        return Math.max(dimension, 1);
    }

    private static float sanitizeScale(double rawScaleFactor) {
        return rawScaleFactor > 0.0D ? (float) rawScaleFactor : 1.0F;
    }

    private static void shutdown() {
        if (!initialized) {
            return;
        }

        callNativeWithoutOpenGlContext(() -> {
            NativeApi.nativeShutdown();
            return NativeStatus.OK;
        });

        initialized = false;
        glfwWindowHandle = 0L;
        cocoaWindowHandle = 0L;
        framebufferWidth = -1;
        framebufferHeight = -1;
        scaleFactor = 1.0F;
        fullscreen = false;

        LOGGER.info("event=metal_phase1 phase=shutdown");
    }
}
