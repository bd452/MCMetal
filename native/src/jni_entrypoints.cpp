#include "mcmetal_api.h"
#include "mcmetal_version.h"

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
  (void)cocoa_window_handle;
  (void)width;
  (void)height;
  (void)debug_flags;
  return 0;
}

JNIEXPORT void JNICALL Java_io_github_mcmetal_metal_bridge_NativeApi_nativeShutdown(
    JNIEnv *env,
    jclass clazz) {
  (void)env;
  (void)clazz;
}
