package io.github.mcmetal.metal;

import io.github.mcmetal.metal.bridge.NativeStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetalRenderSystemBridgeTest {
    private final Map<String, Integer> operationCounts = new HashMap<>();

    @BeforeEach
    void setUp() {
        MetalRenderSystemBridge.resetForTests();
        MetalRenderSystemBridge.setBridgeActiveForTests(true);
        MetalRenderSystemBridge.setStateSubmissionHookForTests((operation, nativeCall) -> {
            operationCounts.merge(operation, 1, Integer::sum);
            return NativeStatus.OK;
        });
    }

    @AfterEach
    void tearDown() {
        MetalRenderSystemBridge.resetForTests();
    }

    @Test
    void repeatedBlendUpdatesSubmitOnlyOnActualTransitions() {
        MetalRenderSystemBridge.onEnableBlend();
        MetalRenderSystemBridge.onEnableBlend();

        MetalRenderSystemBridge.onBlendFunc(770, 771);
        MetalRenderSystemBridge.onBlendFunc(770, 771);

        MetalRenderSystemBridge.onBlendEquation(0x8006);
        MetalRenderSystemBridge.onBlendEquation(0x800B);
        MetalRenderSystemBridge.onBlendEquation(0x800B);

        assertEquals(1, count("nativeSetBlendEnabled"));
        assertEquals(1, count("nativeSetBlendFunc"));
        assertEquals(1, count("nativeSetBlendEquation"));
    }

    @Test
    void rapidViewportAndScissorChurnMaintainsDeterministicSubmissionCounts() {
        for (int i = 0; i < 50; i++) {
            MetalRenderSystemBridge.onViewport(0, 0, 1920, 1080);
            MetalRenderSystemBridge.onViewport(0, 0, 1280, 720);

            MetalRenderSystemBridge.onEnableScissor(10, 20, 300, 400);
            MetalRenderSystemBridge.onEnableScissor(10, 20, 300, 400);
            MetalRenderSystemBridge.onDisableScissor();
            MetalRenderSystemBridge.onDisableScissor();
        }

        assertEquals(100, count("nativeSetViewportState"));
        assertEquals(100, count("nativeSetScissorState"));
    }

    @Test
    void depthAndStencilTransitionsDeduplicateSteadyStates() {
        MetalRenderSystemBridge.onEnableDepthTest();
        MetalRenderSystemBridge.onEnableDepthTest();
        MetalRenderSystemBridge.onDepthMask(false);
        MetalRenderSystemBridge.onDepthMask(false);
        MetalRenderSystemBridge.onDepthMask(true);

        MetalRenderSystemBridge.onStencilMask(0xFF);
        MetalRenderSystemBridge.onStencilMask(0xFF);
        MetalRenderSystemBridge.onStencilFunc(0x0207, 0, 0xFF);
        MetalRenderSystemBridge.onStencilOp(0x1E00, 0x1E00, 0x1E00);
        MetalRenderSystemBridge.onStencilFunc(0x0201, 1, 0xFF);

        assertEquals(3, count("nativeSetDepthState"));
        assertEquals(2, count("nativeSetStencilState"));
    }

    private int count(String operation) {
        return operationCounts.getOrDefault(operation, 0);
    }
}
