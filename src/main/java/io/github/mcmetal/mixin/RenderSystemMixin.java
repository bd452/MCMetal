package io.github.mcmetal.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.mcmetal.metal.MetalRenderSystemBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
abstract class RenderSystemMixin {
    @Inject(method = "enableBlend", at = @At("TAIL"))
    private static void mcmetal$enableBlend(CallbackInfo ci) {
        MetalRenderSystemBridge.onEnableBlend();
    }

    @Inject(method = "disableBlend", at = @At("TAIL"))
    private static void mcmetal$disableBlend(CallbackInfo ci) {
        MetalRenderSystemBridge.onDisableBlend();
    }

    @Inject(method = "blendFunc(II)V", at = @At("TAIL"))
    private static void mcmetal$blendFunc(int srcFactor, int dstFactor, CallbackInfo ci) {
        MetalRenderSystemBridge.onBlendFunc(srcFactor, dstFactor);
    }

    @Inject(method = "blendFuncSeparate(IIII)V", at = @At("TAIL"))
    private static void mcmetal$blendFuncSeparate(
        int srcFactorRgb,
        int dstFactorRgb,
        int srcFactorAlpha,
        int dstFactorAlpha,
        CallbackInfo ci
    ) {
        MetalRenderSystemBridge.onBlendFuncSeparate(srcFactorRgb, dstFactorRgb, srcFactorAlpha, dstFactorAlpha);
    }

    @Inject(method = "blendEquation", at = @At("TAIL"))
    private static void mcmetal$blendEquation(int mode, CallbackInfo ci) {
        MetalRenderSystemBridge.onBlendEquation(mode);
    }

    @Inject(method = "enableDepthTest", at = @At("TAIL"))
    private static void mcmetal$enableDepthTest(CallbackInfo ci) {
        MetalRenderSystemBridge.onEnableDepthTest();
    }

    @Inject(method = "disableDepthTest", at = @At("TAIL"))
    private static void mcmetal$disableDepthTest(CallbackInfo ci) {
        MetalRenderSystemBridge.onDisableDepthTest();
    }

    @Inject(method = "depthFunc", at = @At("TAIL"))
    private static void mcmetal$depthFunc(int function, CallbackInfo ci) {
        MetalRenderSystemBridge.onDepthFunc(function);
    }

    @Inject(method = "depthMask", at = @At("TAIL"))
    private static void mcmetal$depthMask(boolean mask, CallbackInfo ci) {
        MetalRenderSystemBridge.onDepthMask(mask);
    }

    @Inject(method = "enableCull", at = @At("TAIL"))
    private static void mcmetal$enableCull(CallbackInfo ci) {
        MetalRenderSystemBridge.onEnableCull();
    }

    @Inject(method = "disableCull", at = @At("TAIL"))
    private static void mcmetal$disableCull(CallbackInfo ci) {
        MetalRenderSystemBridge.onDisableCull();
    }

    @Inject(method = "enableScissor", at = @At("TAIL"))
    private static void mcmetal$enableScissor(int x, int y, int width, int height, CallbackInfo ci) {
        MetalRenderSystemBridge.onEnableScissor(x, y, width, height);
    }

    @Inject(method = "disableScissor", at = @At("TAIL"))
    private static void mcmetal$disableScissor(CallbackInfo ci) {
        MetalRenderSystemBridge.onDisableScissor();
    }

    @Inject(method = "viewport", at = @At("TAIL"))
    private static void mcmetal$viewport(int x, int y, int width, int height, CallbackInfo ci) {
        MetalRenderSystemBridge.onViewport(x, y, width, height);
    }

    @Inject(method = "stencilFunc", at = @At("TAIL"))
    private static void mcmetal$stencilFunc(int function, int reference, int mask, CallbackInfo ci) {
        MetalRenderSystemBridge.onStencilFunc(function, reference, mask);
    }

    @Inject(method = "stencilMask", at = @At("TAIL"))
    private static void mcmetal$stencilMask(int mask, CallbackInfo ci) {
        MetalRenderSystemBridge.onStencilMask(mask);
    }

    @Inject(method = "stencilOp", at = @At("TAIL"))
    private static void mcmetal$stencilOp(int sfail, int dpfail, int dppass, CallbackInfo ci) {
        MetalRenderSystemBridge.onStencilOp(sfail, dpfail, dppass);
    }

    @Inject(method = "drawElements", at = @At("TAIL"))
    private static void mcmetal$drawElements(int mode, int count, int indexType, CallbackInfo ci) {
        MetalRenderSystemBridge.onDrawElements(mode, count, indexType);
    }
}
