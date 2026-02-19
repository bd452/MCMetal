package io.github.mcmetal.mixin;

import io.github.mcmetal.metal.MetalBufferUploadBridge;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
abstract class VertexBufferMixin {
    @Shadow
    @Final
    private VertexBuffer.Usage usage;

    @Inject(method = "upload", at = @At("HEAD"))
    private void mcmetal$forwardUploadToNative(BuiltBuffer data, CallbackInfo ci) {
        MetalBufferUploadBridge.onVertexBufferUpload((VertexBuffer) (Object) this, this.usage, data);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mcmetal$releaseNativeBuffers(CallbackInfo ci) {
        MetalBufferUploadBridge.onVertexBufferClose((VertexBuffer) (Object) this);
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void mcmetal$submitNativeDraw(CallbackInfo ci) {
        MetalBufferUploadBridge.onVertexBufferDraw((VertexBuffer) (Object) this);
    }
}
