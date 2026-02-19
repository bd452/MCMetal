#ifndef MCMETAL_SWIFT_BRIDGE_H
#define MCMETAL_SWIFT_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t mcmetal_swift_initialize(
    int64_t cocoa_window_handle,
    int32_t width,
    int32_t height,
    int32_t debug_flags);

int32_t mcmetal_swift_resize(
    int32_t width,
    int32_t height,
    float scale_factor,
    int32_t fullscreen);

int32_t mcmetal_swift_render_demo_frame(
    float red,
    float green,
    float blue,
    float alpha);

void mcmetal_swift_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif  // MCMETAL_SWIFT_BRIDGE_H
