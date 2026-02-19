#ifndef MCMETAL_API_H
#define MCMETAL_API_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeGetBridgeVersion(
    JNIEnv *env,
    jclass clazz);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeInitialize(
    JNIEnv *env,
    jclass clazz,
    jlong cocoa_window_handle,
    jint width,
    jint height,
    jint debug_flags);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeResize(
    JNIEnv *env,
    jclass clazz,
    jint width,
    jint height,
    jfloat scale_factor,
    jboolean fullscreen);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeRenderDemoFrame(
    JNIEnv *env,
    jclass clazz,
    jfloat red,
    jfloat green,
    jfloat blue,
    jfloat alpha);

JNIEXPORT void JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeShutdown(
    JNIEnv *env,
    jclass clazz);

#ifdef __cplusplus
}
#endif

#endif  // MCMETAL_API_H
