#ifndef MCMETAL_METAL_CONTEXT_H
#define MCMETAL_METAL_CONTEXT_H

#include <cstdint>

namespace mcmetal {

constexpr std::uint32_t kDebugFlagValidation = 1u << 0;
constexpr std::uint32_t kDebugFlagLabels = 1u << 1;

int InitializeMetalContext(std::int64_t cocoa_window_handle, int width, int height, int debug_flags);
int ResizeMetalContext(int width, int height, float scale_factor, bool fullscreen);
int RenderDemoFrame(float red, float green, float blue, float alpha);
void ShutdownMetalContext();

}  // namespace mcmetal

#endif  // MCMETAL_METAL_CONTEXT_H
