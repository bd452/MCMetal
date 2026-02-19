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

int32_t mcmetal_swift_set_blend_enabled(int32_t enabled);

int32_t mcmetal_swift_set_blend_func(
    int32_t src_rgb,
    int32_t dst_rgb,
    int32_t src_alpha,
    int32_t dst_alpha);

int32_t mcmetal_swift_set_blend_equation(
    int32_t rgb_equation,
    int32_t alpha_equation);

int32_t mcmetal_swift_set_depth_state(
    int32_t depth_test_enabled,
    int32_t depth_write_enabled,
    int32_t depth_compare_function);

int32_t mcmetal_swift_set_stencil_state(
    int32_t stencil_enabled,
    int32_t stencil_function,
    int32_t stencil_reference,
    int32_t stencil_compare_mask,
    int32_t stencil_write_mask,
    int32_t stencil_sfail,
    int32_t stencil_dpfail,
    int32_t stencil_dppass);

int32_t mcmetal_swift_set_cull_state(
    int32_t cull_enabled,
    int32_t cull_mode);

int32_t mcmetal_swift_set_scissor_state(
    int32_t scissor_enabled,
    int32_t x,
    int32_t y,
    int32_t width,
    int32_t height);

int32_t mcmetal_swift_set_viewport_state(
    int32_t x,
    int32_t y,
    int32_t width,
    int32_t height,
    float min_depth,
    float max_depth);

int32_t mcmetal_swift_draw_indexed(
    int32_t mode,
    int32_t count,
    int32_t index_type);

void mcmetal_swift_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif  // MCMETAL_SWIFT_BRIDGE_H
