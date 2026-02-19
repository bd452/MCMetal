#include "mcmetal_api.h"
#include "metal_context.h"
#include "mcmetal_version.h"

#include <cstdint>

JNIEXPORT jstring JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeGetBridgeVersion(
    JNIEnv *env,
    jclass clazz) {
  (void)clazz;
  return env->NewStringUTF(MCMETAL_BRIDGE_VERSION);
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
  return mcmetal::InitializeMetalContext(
      static_cast<std::int64_t>(cocoa_window_handle),
      static_cast<int>(width),
      static_cast<int>(height),
      static_cast<int>(debug_flags));
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
  return mcmetal::ResizeMetalContext(
      static_cast<int>(width),
      static_cast<int>(height),
      static_cast<float>(scale_factor),
      fullscreen == JNI_TRUE);
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
  return mcmetal::RenderDemoFrame(
      static_cast<float>(red),
      static_cast<float>(green),
      static_cast<float>(blue),
      static_cast<float>(alpha));
}

JNIEXPORT void JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeShutdown(
    JNIEnv *env,
    jclass clazz) {
  (void)env;
  (void)clazz;
  mcmetal::ShutdownMetalContext();
}
