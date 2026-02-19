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

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetBlendEnabled(
    JNIEnv *env,
    jclass clazz,
    jboolean enabled);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetBlendFunc(
    JNIEnv *env,
    jclass clazz,
    jint src_rgb,
    jint dst_rgb,
    jint src_alpha,
    jint dst_alpha);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetBlendEquation(
    JNIEnv *env,
    jclass clazz,
    jint rgb_equation,
    jint alpha_equation);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetDepthState(
    JNIEnv *env,
    jclass clazz,
    jboolean depth_test_enabled,
    jboolean depth_write_enabled,
    jint depth_compare_function);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetStencilState(
    JNIEnv *env,
    jclass clazz,
    jboolean stencil_enabled,
    jint stencil_function,
    jint stencil_reference,
    jint stencil_compare_mask,
    jint stencil_write_mask,
    jint stencil_sfail,
    jint stencil_dpfail,
    jint stencil_dppass);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetCullState(
    JNIEnv *env,
    jclass clazz,
    jboolean cull_enabled,
    jint cull_mode);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetScissorState(
    JNIEnv *env,
    jclass clazz,
    jboolean scissor_enabled,
    jint x,
    jint y,
    jint width,
    jint height);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetViewportState(
    JNIEnv *env,
    jclass clazz,
    jint x,
    jint y,
    jint width,
    jint height,
    jfloat min_depth,
    jfloat max_depth);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeDrawIndexed(
    JNIEnv *env,
    jclass clazz,
    jint mode,
    jint count,
    jint index_type);

JNIEXPORT jlong JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeCreateBuffer(
    JNIEnv *env,
    jclass clazz,
    jint usage,
    jint size,
    jobject initial_data,
    jint initial_data_length);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeUpdateBuffer(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jint offset,
    jobject data,
    jint data_length);

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeDestroyBuffer(
    JNIEnv *env,
    jclass clazz,
    jlong handle);

JNIEXPORT void JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeShutdown(
    JNIEnv *env,
    jclass clazz);

#ifdef __cplusplus
}
#endif

#endif  // MCMETAL_API_H
