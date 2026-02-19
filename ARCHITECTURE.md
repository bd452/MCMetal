# Minecraft Metal Renderer (Fabric Mod) - Architecture

## 1. Architectural Principles

1. **Backend substitution, not renderer rewrite**
   - Preserve Blaze3D-facing semantics from the Java side.
   - Implement Metal as a backend behind familiar call patterns.

2. **Thin Java, heavy native**
   - Mixins should mostly forward and adapt data.
   - Native layer owns GPU policy, caching, synchronization, and diagnostics.

3. **Swift-native implementation with stable C ABI**
   - Cocoa/Metal orchestration is implemented in Swift.
   - Java interop remains a narrow JNI boundary through C ABI symbols.

4. **Deterministic state translation**
   - Convert OpenGL's implicit mutable state into explicit immutable pipeline/pass descriptors.
   - Make state transitions auditable and cacheable.

5. **Fail loudly in development, fail safely in production**
   - Rich debug logging and assertions in dev mode.
   - User-safe fallback behavior (error messaging, graceful disable) in production mode.

---

## 2. High-Level Component Model

```text
Minecraft/Blaze3D
   |
Fabric mixins (Java interception layer)
   |
Java Metal API facade (JNI declarations + marshaling helpers)
   |
JNI boundary (C ABI exports)
   |
Native bridge (libminecraft_metal.dylib)
   |-- C JNI shim (jni_entrypoints.c)
   |-- SwiftMetalRuntime (device/queue/layer/frame lifecycle)
   |-- SwiftStateTracker (GL-like state model snapshot)
   |-- SwiftPipelineCache (render/depth/stencil/blend/vertex format keys)
   |-- SwiftBufferManager (dynamic/static MTLBuffer allocation)
   |-- SwiftTextureManager (textures, samplers, upload path)
   |-- SwiftRenderTargetManager (off-screen attachments and pass descriptors)
   |-- SwiftShaderManager (GLSL->SPIR-V->MSL + reflection + function cache)
   |-- SwiftCommandRecorder (encoders, resource binding, draw submission)
   |
Apple Metal runtime (MTLDevice, MTLCommandQueue, CAMetalLayer)
```

---

## 3. Java Layer Design (Fabric + Mixins)

### 3.1 Intercept Targets

Priority interception points:

- `RenderSystem`
  - Blend/depth/stencil/scissor/viewport/clear state
  - Draw dispatch and frame boundary calls
- `BufferBuilder` / `VertexBuffer`
  - Vertex/index data upload lifecycle
- `ShaderInstance`
  - Shader source load, compile/init, uniform updates
- `RenderTarget`
  - Off-screen target create/bind/clear/read semantics
- `AbstractTexture` / `NativeImage`
  - Texture allocation and pixel upload
- `Window`
  - Native surface initialization and resize flow

### 3.2 Java Module Responsibilities

1. **Mixin adapters**
   - Hook methods at stable call sites.
   - Convert object-rich Java calls into compact primitive/native handles.

2. **Native handle registry**
   - Map Java object identity to native resource IDs (`long handle` pattern).
   - Ensure explicit lifecycle notifications (`create`, `update`, `destroy`).

3. **Thread assertions**
   - Ensure rendering JNI calls originate from expected render thread(s).
   - Report violations early to avoid undefined Metal behavior.

4. **Error forwarding**
   - Convert native error/status into Java exceptions/logging.
   - Include shader names, pipeline keys, and pass context in messages.

---

## 4. JNI Interface Contract

Use a narrow, explicit C ABI surface. Java methods should avoid frequent tiny calls when batching is possible.

### 4.1 Core API Groups

1. **Context and frame lifecycle**
   - `nativeInitialize(windowHandle, width, height, debugFlags)`
   - `nativeBeginFrame(frameIndex)`
   - `nativeEndFrame(frameIndex)`
   - `nativeResize(width, height, scaleFactor, fullscreen)`
   - `nativeShutdown()`

2. **Render state**
   - `nativeSetBlendState(...)`
   - `nativeSetDepthStencilState(...)`
   - `nativeSetRasterState(...)`
   - `nativeSetScissor(...)`
   - `nativeSetViewport(...)`
   - `nativeClear(...)`

3. **Buffers**
   - `nativeCreateBuffer(usage, size, initialDataPtr?) -> handle`
   - `nativeUpdateBuffer(handle, offset, dataPtr, length)`
   - `nativeDestroyBuffer(handle)`

4. **Shaders/pipelines**
   - `nativeCreateShaderProgram(vertexSrc, fragmentSrc, metadata) -> handle`
   - `nativeSetUniform(programHandle, uniformId, dataPtr, size)`
   - `nativeDestroyShaderProgram(handle)`

5. **Textures/render targets**
   - `nativeCreateTexture(desc, initialData) -> handle`
   - `nativeUpdateTexture(handle, region, data)`
   - `nativeCreateRenderTarget(desc) -> handle`
   - `nativeBindRenderTarget(handle)`
   - `nativeDestroyTexture/RenderTarget(handle)`

6. **Draw**
   - `nativeBindPipeline(shaderHandle, vertexFormat, fixedStateKey)`
   - `nativeBindVertexIndexBuffers(...)`
   - `nativeDraw(mode, first, count)`
   - `nativeDrawIndexed(mode, indexType, indexOffset, count, baseVertex)`

### 4.2 Marshaling Rules

- Prefer direct `ByteBuffer` and pinned memory for bulk transfers.
- Pass immutable descriptors as POD-like structs (packed fields) to native.
- Avoid Java object traversal from native; resolve all references in Java first.
- Maintain stable integer IDs for uniforms/attributes/textures to avoid per-frame string lookups.

---

## 5. Native Swift Layer Internal Architecture

### 5.1 SwiftMetalRuntime

Owns process-global rendering primitives:

- `MTLDevice` device
- `MTLCommandQueue` queue
- `CAMetalLayer` layer
- Current frame resources:
  - drawable
  - command buffer
  - active render command encoder

Key operations:
- Initialize Metal and attach layer to GLFW's Cocoa window.
- Handle resize/pixel ratio updates.
- Create/present command buffers with debug labels and completion handlers.

### 5.2 SwiftStateTracker

Tracks a normalized GL-like state snapshot:

- Blend enable/factors/equation
- Depth test/write/compare
- Stencil front/back
- Cull mode/front face/fill mode
- Viewport and scissor
- Bound shader program, vertex format, render target

The tracker generates:

1. `PipelineKey` (immutable key for `MTLRenderPipelineState`)
2. `DepthStencilKey` (for `MTLDepthStencilState`)
3. Pass descriptor parameters (color/depth/stencil load/store actions)

### 5.3 SwiftPipelineCache

Caches expensive immutable objects:

- `MTLRenderPipelineState` keyed by:
  - shader function pair
  - vertex descriptor hash
  - color/depth formats
  - blend config
  - sample count
- `MTLDepthStencilState` keyed by depth/stencil config
- `MTLSamplerState` keyed by filter/address/aniso modes

Cache behavior:
- Low-contention synchronization strategy where possible.
- LRU/size cap guardrails for pathological shader churn.
- Optional on-disk metadata cache for warm startup.

### 5.4 SwiftBufferManager

Buffer classes:

1. Static buffers for long-lived meshes.
2. Dynamic/ring buffers for per-frame streaming geometry and uniforms.
3. Staging uploads where direct write is not ideal.

Policies:
- Triple-buffered dynamic regions to reduce CPU/GPU contention.
- Deferred destruction queue tied to command buffer completion.
- Alignment guarantees for Metal requirements (including uniform offsets).

### 5.5 SwiftTextureManager

Responsibilities:
- Pixel format mapping (Minecraft/GL formats -> `MTLPixelFormat`)
- 2D texture creation/update/mipmap generation policy
- Sampler association and reuse
- Atlas update support with region-based uploads

Special handling:
- sRGB correctness for UI and world textures
- Depth and depth-stencil textures for shadow and post passes

### 5.6 SwiftRenderTargetManager

Maps `RenderTarget` semantics to Metal attachments:

- Create color/depth textures backing each target
- Build render pass descriptors on bind
- Handle clear/load/store behavior equivalent to Blaze3D expectations
- Support target resizing and reallocation on window size change

### 5.7 SwiftShaderManager

Pipeline:

1. Parse/collect GLSL stage sources.
2. Compile GLSL -> SPIR-V (glslang).
3. Translate SPIR-V -> MSL (SPIRV-Cross).
4. Reflect resources and emit binding map metadata.
5. Create `MTLLibrary` and `MTLFunction` objects.

Outputs:
- ProgramHandle with:
  - vertex/fragment function refs
  - uniform/resource binding table
  - specialization/preamble variants

Caching:
- Hash by shader source + defines + target profile.
- Persist translated MSL/reflection artifacts on disk in cache directory.

### 5.8 SwiftCommandRecorder

Converts state + resource bindings + draw requests into Metal commands:

- Ensures encoder is created with correct pass descriptor.
- Applies pipeline and depth/stencil only when state changes.
- Binds buffers/textures/samplers by precomputed binding indices.
- Emits draw calls and debug groups per logical pass.

---

## 6. Frame Lifecycle

1. **BeginFrame**
   - Acquire drawable from `CAMetalLayer`.
   - Create command buffer and initial pass descriptor.
   - Reset transient allocators/ring pointers for current frame slot.

2. **Record passes**
   - Apply RenderSystem state deltas via `SwiftStateTracker`.
   - Bind program/pipeline/resources.
   - Execute draw calls for world, UI, particles, post-processing.

3. **Finalize**
   - End active encoder.
   - Present drawable.
   - Commit command buffer.
   - Process completed-frame callbacks (resource reclamation).

---

## 7. OpenGL -> Metal Semantic Mapping

### 7.1 State Model Translation

- OpenGL mutable global state -> explicit key/value snapshot in `SwiftStateTracker`.
- `glUseProgram` + fixed state -> pipeline cache lookup/creation.
- `glBindFramebuffer` -> render pass descriptor selection.
- `glClear*` -> render pass load actions or explicit clear draws depending on timing.

### 7.2 Resource Binding

- GLSL uniform names/locations are transformed into stable binding indices.
- Java sets uniform by logical ID; native writes into:
  - argument buffer slots, or
  - dedicated constant buffers with offsets.
- Texture units become explicit texture/sampler bindings in encoder state.

### 7.3 Draw Modes

Map Minecraft draw modes to `MTLPrimitiveType`, validating unsupported combinations early.

---

## 8. Windowing and Surface Integration

1. Obtain `NSWindow*` via GLFW (`glfwGetCocoaWindow`).
2. Attach/configure `CAMetalLayer` on content view in Swift:
   - pixel format
   - drawable size
   - framebufferOnly and vsync/present mode policy
3. Keep GLFW for input/event/window management unchanged.
4. On resize/fullscreen transitions:
   - update layer drawable size
   - rebuild dependent render targets/pipelines as needed.

---

## 9. Threading and Synchronization

- Rendering commands are expected from Minecraft render thread.
- AppKit-facing window/layer mutations run on the macOS main thread.
- Metal object creation may occur on worker threads only where safe and controlled.
- CPU/GPU sync approach:
  - avoid `waitUntilCompleted` in hot path,
  - use in-flight frame count limits,
  - reclaim temporary resources from completion handlers.

---

## 10. Error Handling, Diagnostics, and Observability

1. **Runtime modes**
   - Debug mode: validation, shader dumps, verbose pipeline logs.
   - Release mode: concise logs, hard failures converted to user-readable errors.

2. **Diagnostic artifacts**
   - Shader transpilation logs (input GLSL, SPIR-V diagnostics, output MSL).
   - Pipeline key and attachment format dumps on creation failure.
   - Frame counters and cache hit/miss telemetry.

3. **Failure strategy**
   - If critical initialization fails, disable renderer cleanly with clear guidance.
   - Avoid partial undefined rendering states.

---

## 11. Mod Compatibility Model

- Explicitly incompatible with mods that rewrite Blaze3D rendering internals (Sodium, Iris, similar).
- Compatible by default with non-rendering mods that do not intercept the same render classes.
- Include startup conflict detection:
  - inspect loaded mods,
  - refuse initialization with actionable error text.

---

## 12. Repository Layout (Swift-Native)

```text
/src/main/java/.../metal/
  MetalBootstrap.java
  MetalPhaseOneBridge.java
  bridge/
    NativeApi.java
    NativeStatus.java
    NativeDebugFlags.java

/native/
  CMakeLists.txt
  include/
    mcmetal_api.h
    mcmetal_swift_bridge.h
    mcmetal_version.h
  src/
    jni_entrypoints.c
    MetalContext.swift
  scripts/
    build_macos.sh
```

---

## 13. Verification and Test Strategy

### 13.1 Unit/Component Tests (native)

- Pipeline key hashing and equality correctness.
- Format mapping tables.
- Shader reflection and binding map generation.
- Resource lifecycle/deferred free queue logic.

### 13.2 Integration Tests

- Native smoke tests for shader compile and pipeline creation.
- In-game automated scenarios:
  - world render,
  - UI overlays,
  - particles/transparency,
  - post-processing passes.

### 13.3 Performance Validation

- Baseline OpenGL vs Metal on identical scenes/settings.
- Capture:
  - average FPS,
  - 1% lows,
  - CPU frame time,
  - GPU frame time,
  - memory use.

---

## 14. Incremental Delivery Architecture Checkpoints

1. **Checkpoint A**
   - Metal layer attached; clear/present loop works.
2. **Checkpoint B**
   - Basic geometry path + state translation stable.
3. **Checkpoint C**
   - Shader transpilation + uniform bindings correct.
4. **Checkpoint D**
   - Render targets/post effects feature-complete.
5. **Checkpoint E**
   - Optimization, diagnostics polish, release readiness.

---

## 15. Open Questions to Resolve Early

1. Minimum supported macOS version and Metal feature set.
2. Required behavior for edge-case GL features used by specific shader packs/resource packs.
3. Disk cache invalidation strategy across Minecraft/mod updates.
4. Whether to expose optional debug UI/commands for renderer metrics.
5. How strictly to preserve byte-for-byte visual parity versus "close enough" tolerance.
