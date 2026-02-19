#include "mcmetal_api.h"
#include "mcmetal_swift_bridge.h"
#include "mcmetal_version.h"

JNIEXPORT jstring JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeGetBridgeVersion(
    JNIEnv *env,
    jclass clazz) {
  (void)clazz;
  return (*env)->NewStringUTF(env, MCMETAL_BRIDGE_VERSION);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeInitialize(
    JNIEnv *env,
    jclass clazz,
    jlong cocoa_window_handle,
    jint width,
    jint height,
    jint debug_flags) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_initialize(
      (int64_t)cocoa_window_handle,
      (int32_t)width,
      (int32_t)height,
      (int32_t)debug_flags);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeResize(
    JNIEnv *env,
    jclass clazz,
    jint width,
    jint height,
    jfloat scale_factor,
    jboolean fullscreen) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_resize(
      (int32_t)width,
      (int32_t)height,
      (float)scale_factor,
      fullscreen == JNI_TRUE ? 1 : 0);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeRenderDemoFrame(
    JNIEnv *env,
    jclass clazz,
    jfloat red,
    jfloat green,
    jfloat blue,
    jfloat alpha) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_render_demo_frame(
      (float)red,
      (float)green,
      (float)blue,
      (float)alpha);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetBlendEnabled(
    JNIEnv *env,
    jclass clazz,
    jboolean enabled) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_blend_enabled(enabled == JNI_TRUE ? 1 : 0);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetBlendFunc(
    JNIEnv *env,
    jclass clazz,
    jint src_rgb,
    jint dst_rgb,
    jint src_alpha,
    jint dst_alpha) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_blend_func(
      (int32_t)src_rgb,
      (int32_t)dst_rgb,
      (int32_t)src_alpha,
      (int32_t)dst_alpha);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetBlendEquation(
    JNIEnv *env,
    jclass clazz,
    jint rgb_equation,
    jint alpha_equation) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_blend_equation(
      (int32_t)rgb_equation,
      (int32_t)alpha_equation);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetDepthState(
    JNIEnv *env,
    jclass clazz,
    jboolean depth_test_enabled,
    jboolean depth_write_enabled,
    jint depth_compare_function) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_depth_state(
      depth_test_enabled == JNI_TRUE ? 1 : 0,
      depth_write_enabled == JNI_TRUE ? 1 : 0,
      (int32_t)depth_compare_function);
}

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
    jint stencil_dppass) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_stencil_state(
      stencil_enabled == JNI_TRUE ? 1 : 0,
      (int32_t)stencil_function,
      (int32_t)stencil_reference,
      (int32_t)stencil_compare_mask,
      (int32_t)stencil_write_mask,
      (int32_t)stencil_sfail,
      (int32_t)stencil_dpfail,
      (int32_t)stencil_dppass);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetCullState(
    JNIEnv *env,
    jclass clazz,
    jboolean cull_enabled,
    jint cull_mode) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_cull_state(
      cull_enabled == JNI_TRUE ? 1 : 0,
      (int32_t)cull_mode);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetScissorState(
    JNIEnv *env,
    jclass clazz,
    jboolean scissor_enabled,
    jint x,
    jint y,
    jint width,
    jint height) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_scissor_state(
      scissor_enabled == JNI_TRUE ? 1 : 0,
      (int32_t)x,
      (int32_t)y,
      (int32_t)width,
      (int32_t)height);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeSetViewportState(
    JNIEnv *env,
    jclass clazz,
    jint x,
    jint y,
    jint width,
    jint height,
    jfloat min_depth,
    jfloat max_depth) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_set_viewport_state(
      (int32_t)x,
      (int32_t)y,
      (int32_t)width,
      (int32_t)height,
      (float)min_depth,
      (float)max_depth);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeDrawIndexed(
    JNIEnv *env,
    jclass clazz,
    jint mode,
    jint count,
    jint index_type) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_draw_indexed(
      (int32_t)mode,
      (int32_t)count,
      (int32_t)index_type);
}

JNIEXPORT jlong JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeCreateBuffer(
    JNIEnv *env,
    jclass clazz,
    jint usage,
    jint size,
    jobject initial_data,
    jint initial_data_length) {
  (void)clazz;
  const void *initial_data_ptr = NULL;
  if (initial_data != NULL) {
    initial_data_ptr = (*env)->GetDirectBufferAddress(env, initial_data);
    if (initial_data_ptr == NULL && initial_data_length > 0) {
      return (jlong)0;
    }
  }
  return (jlong)mcmetal_swift_create_buffer(
      (int32_t)usage,
      (int32_t)size,
      initial_data_ptr,
      (int32_t)initial_data_length);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeUpdateBuffer(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jint offset,
    jobject data,
    jint data_length) {
  (void)clazz;
  const void *data_ptr = NULL;
  if (data != NULL) {
    data_ptr = (*env)->GetDirectBufferAddress(env, data);
    if (data_ptr == NULL && data_length > 0) {
      return (jint)2;
    }
  }
  return (jint)mcmetal_swift_update_buffer(
      (int64_t)handle,
      (int32_t)offset,
      data_ptr,
      (int32_t)data_length);
}

JNIEXPORT jint JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeDestroyBuffer(
    JNIEnv *env,
    jclass clazz,
    jlong handle) {
  (void)env;
  (void)clazz;
  return (jint)mcmetal_swift_destroy_buffer((int64_t)handle);
}

JNIEXPORT void JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeShutdown(
    JNIEnv *env,
    jclass clazz) {
  (void)env;
  (void)clazz;
  mcmetal_swift_shutdown();
}
