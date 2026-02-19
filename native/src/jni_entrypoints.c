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

JNIEXPORT void JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeShutdown(
    JNIEnv *env,
    jclass clazz) {
  (void)env;
  (void)clazz;
  mcmetal_swift_shutdown();
}
