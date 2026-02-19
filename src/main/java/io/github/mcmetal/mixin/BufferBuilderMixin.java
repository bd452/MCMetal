package io.github.mcmetal.mixin;

import io.github.mcmetal.metal.MetalBufferUploadBridge;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin {
    @Inject(method = "end", at = @At("RETURN"))
    private void mcmetal$onBufferBuilt(CallbackInfoReturnable<BuiltBuffer> cir) {
        MetalBufferUploadBridge.onBufferBuilderEnd(cir.getReturnValue());
    }
}
