# Minecraft Metal Renderer (Fabric Mod) - Overview

## 1. Vision

Deliver a Fabric mod for macOS that replaces Minecraft's OpenGL rendering path with Apple Metal while preserving vanilla gameplay behavior and compatibility with non-rendering Fabric mods.

The purpose is to remove dependence on Apple's deprecated OpenGL 4.1 stack and achieve better frame pacing, throughput, and long-term platform viability.

Governance decisions for scope boundaries, support matrix, release quality gates, and milestone ownership are published in [PROJECT_GOVERNANCE.md](PROJECT_GOVERNANCE.md). Active mitigation tracking is maintained in [RISK_REGISTER.md](RISK_REGISTER.md).

## 2. Product Goals

1. **Functional parity with vanilla rendering** on supported Minecraft + Fabric versions.
2. **Lower CPU overhead and better GPU utilization** versus OpenGL on macOS.
3. **Stable, debuggable architecture** that can evolve with Minecraft updates.
4. **Clear compatibility boundaries** (incompatible with rendering-overhaul mods, compatible with most gameplay/UI mods).

## 3. Non-Goals

- Replacing non-rendering LWJGL components (OpenAL, stb_image, stb_truetype).
- Building a cross-platform renderer abstraction (scope is macOS + Metal only).
- Maintaining compatibility with Sodium/Iris/other Blaze3D-rewriting mods.
- Rewriting Minecraft rendering logic from scratch; the intent is backend substitution.

## 4. Scope Summary

The implementation has three major layers:

1. **Java interception layer (Fabric mixins)**
   - Hooks Blaze3D classes (`RenderSystem`, `VertexBuffer`, `BufferBuilder`, `ShaderInstance`, `RenderTarget`, texture classes, `Window`).
   - Forwards calls to a JNI bridge API.
   - Stays thin: no heavy renderer policy in mixins.

2. **Native bridge (`libminecraft_metal.dylib`)**
   - Owns `MTLDevice`, command queue, frame lifecycle, resource managers.
   - Translates OpenGL-style implicit state to explicit Metal pipeline/encoder state.
   - Maintains caches for render pipeline states and reusable GPU resources.

3. **Shader transpilation subsystem**
   - Converts Minecraft GLSL to MSL (GLSL -> SPIR-V -> MSL via glslang + SPIRV-Cross).
   - Builds/reflection-maps uniforms and resource bindings for Metal.
   - Caches transpilation results to reduce startup/runtime overhead.

## 5. Success Criteria (Definition of Done)

### MVP success

- Main world, UI, and basic post-processing render correctly on macOS.
- No OpenGL context required at runtime.
- Stable across extended gameplay sessions without crashes or unbounded memory growth.

### Performance success

- Equal or better FPS than OpenGL baseline in representative scenes.
- Improved frame-time consistency (lower jitter/stutter in 1% low metrics).
- Reduced driver overhead visible in profiling traces.

### Engineering success

- Reproducible build for Java + native artifacts.
- Automated smoke tests and basic rendering validation in CI.
- Clear logs/diagnostics for pipeline creation failures and shader translation errors.

## 6. Implementation Plan

## Phase 0 - Foundation and Build Integration

**Outcomes**
- Fabric mod skeleton with native loader hook.
- Native macOS library build (CMake/Xcode or equivalent) integrated into Gradle packaging.
- Basic JNI handshake and version checks.

**Deliverables**
- `metal-bridge` Java package with native method stubs.
- `native/` module producing `libminecraft_metal.dylib`.
- Startup log proving native library initialization.

## Phase 1 - Window/Surface Bring-Up

**Outcomes**
- Prevent OpenGL context usage where possible.
- Attach `CAMetalLayer` to GLFW Cocoa window content view.
- Acquire drawable and present per frame.

**Deliverables**
- Native window bootstrap using `glfwGetCocoaWindow`.
- Frame clear color demo rendered through Metal.
- Resize handling wired to drawable size updates.

## Phase 2 - Core RenderSystem State Translation

**Outcomes**
- Render state tracker for blend/depth/stencil/cull/scissor/viewport.
- Metal command encoder setup from tracked state.
- Draw call path for simple vertex/index buffers.

**Deliverables**
- Initial `RenderSystem` mixins forwarding state changes.
- Native state cache and command submission for world/UI primitives.
- Validation layer support and debug labels.

## Phase 3 - Buffers, Vertex Formats, and Draw Path

**Outcomes**
- `BufferBuilder` and `VertexBuffer` pipeline mapped to `MTLBuffer`.
- Dynamic upload strategy (ring buffers/staging).
- Vertex descriptor mapping from Blaze3D formats.

**Deliverables**
- Stable geometry upload path.
- Correct index type handling and draw variants.
- Stress-tested dynamic buffer allocations.

## Phase 4 - Shader Pipeline (GLSL -> MSL)

**Outcomes**
- Shader loading interception in `ShaderInstance`.
- GLSL to SPIR-V to MSL conversion + reflection-based binding map.
- `MTLLibrary`/`MTLFunction` creation and pipeline compilation cache.

**Deliverables**
- Transpilation diagnostics (compile logs, source dump in debug mode).
- Uniform update API from Java to native.
- Disk cache for transpiled shader artifacts.

## Phase 5 - Textures and Render Targets

**Outcomes**
- `AbstractTexture`/`NativeImage` upload path to `MTLTexture`.
- `RenderTarget` mapping for off-screen passes.
- Support for depth/color attachments and load/store semantics.

**Deliverables**
- Correct framebuffer-like behavior for shadow maps/post FX.
- Texture format mapping table and fallback rules.
- Render pass descriptors generated from Minecraft render target state.

## Phase 6 - Feature Parity, Robustness, and Optimization

**Outcomes**
- Fill remaining rendering edge cases.
- Pipeline warm-up/caching improvements and command submission tuning.
- Memory and resource lifetime hardening.

**Deliverables**
- Benchmark report against OpenGL baseline.
- Regression suite for representative scenes.
- Candidate release with compatibility matrix.

## 7. Cross-Cutting Engineering Workstreams

1. **Diagnostics and crash triage**
   - Structured logs for JNI calls, pipeline builds, shader failures.
   - Optional debug HUD/counters (pipeline cache hits, draw calls, frame time).

2. **Version strategy**
   - Pin supported Minecraft/Fabric versions per release branch.
   - Keep thin adaptation layer for Mojang obfuscation/name changes.

3. **Compatibility policy**
   - Fail fast when conflicting rendering mods are present.
   - Provide user-facing explanation and safe disable behavior.

4. **Performance discipline**
   - Continuous profiling on Apple Silicon and Intel macOS.
   - Budget targets for CPU frame time, GPU frame time, and memory usage.

## 8. Major Risks and Mitigations

1. **Mismatch between OpenGL implicit semantics and Metal explicit model**
   - Mitigate with a strict internal state tracker and deterministic pipeline keying.

2. **Shader transpilation edge cases**
   - Mitigate with robust reflection, per-shader test corpus, and fallback diagnostics.

3. **Minecraft internal changes across versions**
   - Mitigate by minimizing invasive mixins and centralizing mapping logic.

4. **Resource lifetime bugs (use-after-free, leaks, stalls)**
   - Mitigate with frame fences, deferred destruction queues, and validation tooling.

5. **Mod conflicts**
   - Mitigate with explicit compatibility checks and clear mod metadata constraints.

## 9. Initial Milestone Schedule (Suggested)

- **M1:** Native bootstrap + CAMetalLayer clear pass.
- **M2:** Basic world/UI geometry rendering through Metal.
- **M3:** Shader transpilation + uniform binding functional.
- **M4:** RenderTarget/post-processing parity.
- **M5:** Stabilization + performance pass + public alpha.

## 10. Release Strategy

1. **Internal alpha:** limited tester group, heavy diagnostics enabled.
2. **Public alpha:** wider hardware coverage, issue triage by GPU/OS/Minecraft version.
3. **Beta:** compatibility hardening and performance tuning.
4. **Stable:** documented support matrix and known limitations.

