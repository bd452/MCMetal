package io.github.mcmetal.mixin;

import io.github.mcmetal.metal.MetalShaderLifecycleBridge;
import net.minecraft.client.gl.GlImportProcessor;
import net.minecraft.client.gl.ShaderStage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

@Mixin(ShaderStage.class)
abstract class ShaderStageMixin {
    @Shadow
    @Final
    private ShaderStage.Type type;

    @Shadow
    @Final
    private String name;

    @Inject(
        method = "load(Lnet/minecraft/client/gl/ShaderStage$Type;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lnet/minecraft/client/gl/GlImportProcessor;)I",
        at = @At("HEAD"),
        require = 0
    )
    private static void mcmetal$onCompileStart(
        ShaderStage.Type type,
        String name,
        InputStream stream,
        String domain,
        GlImportProcessor loader,
        CallbackInfoReturnable<Integer> cir
    ) {
        String stageName = type.name();
        MetalShaderLifecycleBridge.onShaderStageCompileStart(name, stageName);
        MetalShaderLifecycleBridge.onShaderStageCompileSource(name, stageName, stream);
    }

    @Inject(
        method = "load(Lnet/minecraft/client/gl/ShaderStage$Type;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lnet/minecraft/client/gl/GlImportProcessor;)I",
        at = @At("RETURN"),
        require = 0
    )
    private static void mcmetal$onCompileComplete(
        ShaderStage.Type type,
        String name,
        InputStream stream,
        String domain,
        GlImportProcessor loader,
        CallbackInfoReturnable<Integer> cir
    ) {
        MetalShaderLifecycleBridge.onShaderStageCompileComplete(name, type.name());
    }

    @Inject(method = "release", at = @At("HEAD"), require = 0)
    private void mcmetal$onRelease(CallbackInfo ci) {
        MetalShaderLifecycleBridge.onShaderStageRelease(this.name, this.type.name());
    }
}
