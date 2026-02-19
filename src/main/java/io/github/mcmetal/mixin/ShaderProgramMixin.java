package io.github.mcmetal.mixin;

import io.github.mcmetal.metal.MetalShaderLifecycleBridge;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderStage;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderProgram.class)
abstract class ShaderProgramMixin {
    @Shadow
    @Final
    private String name;

    @Inject(
        method = "<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V",
        at = @At("HEAD")
    )
    private void mcmetal$onProgramLoadStart(ResourceFactory factory, String name, VertexFormat format, CallbackInfo ci) {
        MetalShaderLifecycleBridge.onShaderProgramLoadStart(name);
    }

    @Inject(
        method = "<init>(Lnet/minecraft/resource/ResourceFactory;Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;)V",
        at = @At("RETURN")
    )
    private void mcmetal$onProgramLoadComplete(ResourceFactory factory, String name, VertexFormat format, CallbackInfo ci) {
        MetalShaderLifecycleBridge.onShaderProgramLoadComplete(name);
    }

    @Inject(
        method = "loadShader(Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/client/gl/ShaderStage$Type;Ljava/lang/String;)Lnet/minecraft/client/gl/ShaderStage;",
        at = @At("HEAD"),
        require = 0
    )
    private static void mcmetal$onLoadShaderStart(
        ResourceFactory factory,
        ShaderStage.Type type,
        String name,
        CallbackInfoReturnable<ShaderStage> cir
    ) {
        MetalShaderLifecycleBridge.onShaderStageCompileStart(name, type.name());
    }

    @Inject(
        method = "loadShader(Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/client/gl/ShaderStage$Type;Ljava/lang/String;)Lnet/minecraft/client/gl/ShaderStage;",
        at = @At("RETURN"),
        require = 0
    )
    private static void mcmetal$onLoadShaderComplete(
        ResourceFactory factory,
        ShaderStage.Type type,
        String name,
        CallbackInfoReturnable<ShaderStage> cir
    ) {
        MetalShaderLifecycleBridge.onShaderStageCompileComplete(name, type.name());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mcmetal$onProgramClose(CallbackInfo ci) {
        MetalShaderLifecycleBridge.onShaderProgramClose(this.name);
    }
}
