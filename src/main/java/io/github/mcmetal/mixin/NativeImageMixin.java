package io.github.mcmetal.mixin;

import io.github.mcmetal.metal.MetalTextureUploadBridge;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
abstract class NativeImageMixin {
    @Inject(method = "upload(IIIZ)V", at = @At("HEAD"), require = 0)
    private void mcmetal$onSimpleUpload(
        int level,
        int xOffset,
        int yOffset,
        boolean close,
        CallbackInfo ci
    ) {
        NativeImage image = (NativeImage) (Object) this;
        MetalTextureUploadBridge.onNativeImageUpload(
            image,
            level,
            xOffset,
            yOffset,
            0,
            0,
            image.getWidth(),
            image.getHeight()
        );
    }

    @Inject(method = "upload(IIIIIIIZZ)V", at = @At("HEAD"), require = 0)
    private void mcmetal$onPartialUpload(
        int level,
        int xOffset,
        int yOffset,
        int skipPixels,
        int skipRows,
        int width,
        int height,
        boolean blur,
        boolean clamp,
        CallbackInfo ci
    ) {
        MetalTextureUploadBridge.onNativeImageUpload(
            (NativeImage) (Object) this,
            level,
            xOffset,
            yOffset,
            skipPixels,
            skipRows,
            width,
            height
        );
    }

    @Inject(method = "upload(IIIIIIIZZZZ)V", at = @At("HEAD"), require = 0)
    private void mcmetal$onExtendedUpload(
        int level,
        int xOffset,
        int yOffset,
        int skipPixels,
        int skipRows,
        int width,
        int height,
        boolean blur,
        boolean clamp,
        boolean mipmap,
        boolean close,
        CallbackInfo ci
    ) {
        MetalTextureUploadBridge.onNativeImageUpload(
            (NativeImage) (Object) this,
            level,
            xOffset,
            yOffset,
            skipPixels,
            skipRows,
            width,
            height
        );
    }
}
