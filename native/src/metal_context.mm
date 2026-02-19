#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>

#include "metal_context.h"

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <mutex>

namespace {

constexpr int kStatusOk = 0;
constexpr int kStatusAlreadyInitialized = 1;
constexpr int kStatusInvalidArgument = 2;
constexpr int kStatusInitializationFailed = 3;

struct MetalContextState {
  NSWindow *window = nil;
  NSView *content_view = nil;
  CAMetalLayer *layer = nil;
  id<MTLDevice> device = nil;
  id<MTLCommandQueue> command_queue = nil;
  int width = 1;
  int height = 1;
  float scale_factor = 1.0F;
  bool fullscreen = false;
  std::uint32_t debug_flags = 0;
  bool initialized = false;
};

std::mutex g_state_mutex;
MetalContextState g_state;

int ClampDimension(int value) {
  return std::max(value, 1);
}

float ClampScale(float value) {
  return value > 0.0F ? value : 1.0F;
}

double ClampColor(float value) {
  return std::clamp(static_cast<double>(value), 0.0, 1.0);
}

void ApplyValidationEnvironment(std::uint32_t debug_flags) {
  if ((debug_flags & mcmetal::kDebugFlagValidation) == 0u) {
    return;
  }

  (void)setenv("MTL_DEBUG_LAYER", "1", 0);
  (void)setenv("METAL_DEVICE_WRAPPER_TYPE", "1", 0);
}

void ApplyLayerGeometryLocked() {
  if (g_state.content_view == nil || g_state.layer == nil) {
    return;
  }

  g_state.layer.frame = g_state.content_view.bounds;
  g_state.layer.contentsScale = static_cast<CGFloat>(g_state.scale_factor);
  g_state.layer.drawableSize =
      CGSizeMake(static_cast<CGFloat>(ClampDimension(g_state.width)),
                 static_cast<CGFloat>(ClampDimension(g_state.height)));
}

void ApplyDebugLabelsLocked() {
  if ((g_state.debug_flags & mcmetal::kDebugFlagLabels) == 0u) {
    return;
  }

  g_state.layer.name = @"MCMetal Main Layer";
  g_state.command_queue.label = @"MCMetal Main Queue";
}

}  // namespace

namespace mcmetal {

int InitializeMetalContext(std::int64_t cocoa_window_handle,
                           int width,
                           int height,
                           int debug_flags) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (g_state.initialized) {
    return kStatusAlreadyInitialized;
  }

  if (cocoa_window_handle == 0 || width <= 0 || height <= 0) {
    return kStatusInvalidArgument;
  }

  ApplyValidationEnvironment(static_cast<std::uint32_t>(debug_flags));

  auto *window = reinterpret_cast<NSWindow *>(
      static_cast<std::uintptr_t>(cocoa_window_handle));
  if (window == nil) {
    return kStatusInvalidArgument;
  }

  NSView *content_view = [window contentView];
  if (content_view == nil) {
    return kStatusInitializationFailed;
  }

  id<MTLDevice> device = MTLCreateSystemDefaultDevice();
  if (device == nil) {
    return kStatusInitializationFailed;
  }

  id<MTLCommandQueue> command_queue = [device newCommandQueue];
  if (command_queue == nil) {
    return kStatusInitializationFailed;
  }

  CAMetalLayer *layer = [CAMetalLayer layer];
  if (layer == nil) {
    return kStatusInitializationFailed;
  }

  layer.device = device;
  layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
  layer.framebufferOnly = YES;
  layer.opaque = YES;
  layer.presentsWithTransaction = NO;
  layer.allowsNextDrawableTimeout = NO;

  if (@available(macOS 10.13, *)) {
    layer.displaySyncEnabled = YES;
  }

  [content_view setWantsLayer:YES];
  content_view.layer = layer;

  g_state.window = window;
  g_state.content_view = content_view;
  g_state.layer = layer;
  g_state.device = device;
  g_state.command_queue = command_queue;
  g_state.width = ClampDimension(width);
  g_state.height = ClampDimension(height);
  g_state.scale_factor = ClampScale(static_cast<float>(window.backingScaleFactor));
  g_state.fullscreen = ([window styleMask] & NSWindowStyleMaskFullScreen) != 0;
  g_state.debug_flags = static_cast<std::uint32_t>(debug_flags);
  g_state.initialized = true;

  ApplyLayerGeometryLocked();
  ApplyDebugLabelsLocked();
  return kStatusOk;
}

int ResizeMetalContext(int width, int height, float scale_factor, bool fullscreen) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!g_state.initialized) {
    return kStatusInitializationFailed;
  }

  if (width <= 0 || height <= 0) {
    return kStatusInvalidArgument;
  }

  g_state.width = ClampDimension(width);
  g_state.height = ClampDimension(height);
  g_state.scale_factor = ClampScale(scale_factor);
  g_state.fullscreen = fullscreen;
  ApplyLayerGeometryLocked();
  return kStatusOk;
}

int RenderDemoFrame(float red, float green, float blue, float alpha) {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!g_state.initialized || g_state.layer == nil || g_state.command_queue == nil) {
    return kStatusInitializationFailed;
  }

  @autoreleasepool {
    id<CAMetalDrawable> drawable = [g_state.layer nextDrawable];
    if (drawable == nil) {
      return kStatusInitializationFailed;
    }

    MTLRenderPassDescriptor *render_pass = [MTLRenderPassDescriptor renderPassDescriptor];
    MTLRenderPassColorAttachmentDescriptor *color_attachment = render_pass.colorAttachments[0];
    color_attachment.texture = drawable.texture;
    color_attachment.loadAction = MTLLoadActionClear;
    color_attachment.storeAction = MTLStoreActionStore;
    color_attachment.clearColor = MTLClearColorMake(
        ClampColor(red), ClampColor(green), ClampColor(blue), ClampColor(alpha));

    id<MTLCommandBuffer> command_buffer = [g_state.command_queue commandBuffer];
    if (command_buffer == nil) {
      return kStatusInitializationFailed;
    }

    if ((g_state.debug_flags & kDebugFlagLabels) != 0u) {
      command_buffer.label = @"MCMetal Demo Command Buffer";
    }

    id<MTLRenderCommandEncoder> encoder =
        [command_buffer renderCommandEncoderWithDescriptor:render_pass];
    if (encoder == nil) {
      return kStatusInitializationFailed;
    }

    if ((g_state.debug_flags & kDebugFlagLabels) != 0u) {
      encoder.label = @"MCMetal Demo Clear Encoder";
      [encoder pushDebugGroup:@"MCMetal Demo Clear"];
      [encoder popDebugGroup];
    }

    [encoder endEncoding];
    [command_buffer presentDrawable:drawable];
    [command_buffer commit];
  }

  return kStatusOk;
}

void ShutdownMetalContext() {
  std::lock_guard<std::mutex> lock(g_state_mutex);
  if (!g_state.initialized) {
    return;
  }

  if (g_state.content_view != nil && g_state.content_view.layer == g_state.layer) {
    g_state.content_view.layer = nil;
  }

  g_state = MetalContextState{};
}

}  // namespace mcmetal
