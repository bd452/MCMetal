package io.github.mcmetal.mixin;

import io.github.mcmetal.metal.MetalTextureUploadBridge;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractTexture.class)
abstract class AbstractTextureMixin {
    @Shadow
    protected int glId;

    @Inject(method = "bindTexture", at = @At("TAIL"))
    private void mcmetal$onBindTexture(CallbackInfo ci) {
        MetalTextureUploadBridge.onTextureBound((AbstractTexture) (Object) this);
    }

    @Inject(method = "clearGlId", at = @At("HEAD"))
    private void mcmetal$onClearGlId(CallbackInfo ci) {
        MetalTextureUploadBridge.onTextureGlIdCleared((AbstractTexture) (Object) this, this.glId);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mcmetal$onTextureClose(CallbackInfo ci) {
        MetalTextureUploadBridge.onTextureClosed((AbstractTexture) (Object) this, this.glId);
    }
}
