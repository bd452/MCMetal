#ifndef MCMETAL_SWIFT_BRIDGE_H
#define MCMETAL_SWIFT_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C"
{
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

    int32_t mcmetal_swift_draw(
        int32_t mode,
        int32_t first,
        int32_t count);

    int32_t mcmetal_swift_draw_indexed(
        int32_t mode,
        int32_t count,
        int32_t index_type);

    int64_t mcmetal_swift_create_buffer(
        int32_t usage,
        int32_t size,
        const void *initial_data,
        int32_t initial_data_length);

    int32_t mcmetal_swift_update_buffer(
        int64_t handle,
        int32_t offset,
        const void *data,
        int32_t data_length);

    int32_t mcmetal_swift_destroy_buffer(
        int64_t handle);

    int64_t mcmetal_swift_register_vertex_descriptor(
        int32_t stride_bytes,
        int32_t attribute_count,
        const int32_t *packed_elements,
        int32_t packed_int_count);

    int64_t mcmetal_swift_create_shader_program(
        const char *program_name,
        const char *vertex_msl_source,
        const char *fragment_msl_source);

    int32_t mcmetal_swift_compile_shader_pipeline(
        int64_t program_handle,
        int64_t vertex_descriptor_handle);

    int64_t mcmetal_swift_register_uniform(
        int64_t program_handle,
        const char *uniform_name,
        int32_t set,
        int32_t binding);

    int32_t mcmetal_swift_update_uniform_float4(
        int64_t uniform_handle,
        float x,
        float y,
        float z,
        float w);

    int32_t mcmetal_swift_destroy_shader_program(
        int64_t program_handle);

    void mcmetal_swift_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif // MCMETAL_SWIFT_BRIDGE_H
