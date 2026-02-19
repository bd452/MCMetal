# MCMetal Project Plan

Native implementation baseline: Swift-native runtime + C ABI JNI bridge.

- [x] 0. Project Governance, Scope, and Readiness
  - [x] Confirm supported target matrix (Minecraft version, Fabric loader/API version, macOS versions, Apple Silicon + Intel coverage).
  - [x] Finalize and publish scope boundaries (Metal backend substitution only, no non-rendering subsystem rewrite).
  - [x] Finalize and publish non-goals (no cross-platform graphics backend, no compatibility with rendering-overhaul mods).
  - [x] Define Definition of Done for MVP, performance, and release quality gates.
  - [x] Establish milestone owners and review cadence.
  - [x] Create and maintain a risk register with mitigation owners.

- [x] 1. Phase 0 - Foundation and Build Integration
  - [x] Create/validate Fabric mod skeleton and package structure.
  - [x] Add Java Metal bridge package with JNI method declarations.
  - [x] Create native module layout (`native/`, headers, Swift source folders, build scripts).
  - [x] Implement native library bootstrap (`libminecraft_metal.dylib`) build with CMake/Xcode + Swift tooling.
  - [x] Integrate native build into Gradle lifecycle (build, test, package, publish artifacts).
  - [x] Implement startup native loading and version handshake validation.
  - [x] Add structured startup logging for native initialization success/failure.
  - [x] Add initial CI pipeline to build Java and native artifacts on macOS runners.
  - [x] Document local development setup and build prerequisites.

- [x] 2. Phase 1 - Window and Surface Bring-Up
  - [x] Add window interception and retrieve Cocoa window handle from GLFW.
  - [x] Attach/configure `CAMetalLayer` on Minecraft window content view.
  - [x] Initialize `MTLDevice` and `MTLCommandQueue`.
  - [x] Implement frame clear + present demo path through Metal.
  - [x] Wire drawable resize handling (window resize, retina scale changes, fullscreen transitions).
  - [x] Ensure no runtime dependency on active OpenGL rendering context.
  - [x] Add debug labels and validation toggles for early bring-up diagnostics.

- [ ] 3. Phase 2 - RenderSystem State Translation
  - [x] Implement Java mixins for core `RenderSystem` state calls.
  - [ ] Build normalized Swift-native state tracker (blend, depth, stencil, cull, scissor, viewport).
  - [ ] Implement deterministic state-to-key conversion for pipeline/depth-stencil lookup.
  - [ ] Add command encoder setup based on tracked state snapshots.
  - [ ] Implement basic draw submission for simple world/UI primitives.
  - [ ] Validate state transition correctness with debug assertions and logs.
  - [ ] Add regression checks for frequent state-change scenarios.

- [ ] 4. Phase 3 - Buffers, Vertex Formats, and Draw Path
  - [ ] Intercept and map `BufferBuilder`/`VertexBuffer` data flows to native buffer APIs.
  - [ ] Implement static and dynamic `MTLBuffer` allocation strategy.
  - [ ] Add ring-buffer/staging strategy for per-frame uploads.
  - [ ] Implement vertex descriptor mapping from Blaze3D formats to Metal descriptors.
  - [ ] Support indexed and non-indexed draw variants with correct index-type handling.
  - [ ] Add deferred destruction/resource lifetime handling tied to frame completion.
  - [ ] Stress test buffer churn and high draw-count scenes.

- [ ] 5. Phase 4 - Shader Pipeline (GLSL -> SPIR-V -> MSL)
  - [ ] Intercept `ShaderInstance` load/compile lifecycle from Java.
  - [ ] Integrate GLSL-to-SPIR-V compilation path.
  - [ ] Integrate SPIR-V-to-MSL translation path.
  - [ ] Implement reflection-based binding map generation (uniforms, textures, samplers).
  - [ ] Build Swift-native shader program creation and pipeline compilation path.
  - [ ] Implement uniform update bridge with stable IDs (avoid per-frame string lookups).
  - [ ] Add disk cache for translated shaders and reflection metadata.
  - [ ] Add shader diagnostics (GLSL input, compile errors, translated MSL output in debug mode).
  - [ ] Validate representative vanilla shader corpus.

- [ ] 6. Phase 5 - Textures and Render Targets
  - [ ] Map Minecraft texture formats and usage flags to Metal pixel formats.
  - [ ] Implement texture creation and region update upload path (`AbstractTexture`, `NativeImage`).
  - [ ] Implement sampler state mapping and caching.
  - [ ] Implement mipmap generation/update policy.
  - [ ] Implement `RenderTarget` creation/bind/resize semantics.
  - [ ] Build render pass descriptor generation (color/depth/stencil load/store actions).
  - [ ] Validate framebuffer-equivalent behavior for off-screen passes.
  - [ ] Validate post-processing and shadow/depth workflows.
  - [ ] Add fallback/error handling for unsupported texture/attachment combinations.

- [ ] 7. Phase 6 - Feature Parity, Robustness, and Optimization
  - [ ] Close remaining visual parity gaps across world, UI, particles, transparency, and post effects.
  - [ ] Harden pipeline cache strategy (hit-rate tuning, cap/eviction policies).
  - [ ] Reduce redundant state/resource binds in command recording.
  - [ ] Optimize CPU-side submission and JNI call batching.
  - [ ] Add in-flight frame management and synchronization tuning.
  - [ ] Validate long-session stability (no leaks, no unbounded resource growth, no stalls).
  - [ ] Produce benchmark report versus OpenGL baseline.
  - [ ] Finalize release candidate compatibility matrix.

- [ ] 8. Cross-Cutting Workstream - Diagnostics and Developer Experience
  - [ ] Standardize log schema across Java and native layers.
  - [ ] Add per-frame telemetry counters (draw calls, pipeline cache hit/miss, frame times).
  - [ ] Add runtime debug mode controls and validation feature flags.
  - [ ] Add crash-context capture (active shader, pipeline key, render target state).
  - [ ] Add actionable user-facing error messages for critical initialization failures.
  - [ ] Document troubleshooting and log-collection workflow.

- [ ] 9. Cross-Cutting Workstream - Compatibility and Versioning
  - [ ] Implement startup conflict detection for known incompatible rendering mods.
  - [ ] Add safe-disable path when conflicts are detected.
  - [ ] Maintain compatibility policy document and mod interaction notes.
  - [ ] Create adaptation layer for Minecraft mapping/name changes.
  - [ ] Define version support/deprecation policy by release branch.
  - [ ] Track compatibility test runs by Minecraft/Fabric/macOS combination.

- [ ] 10. Cross-Cutting Workstream - Quality Engineering and Test Automation
  - [ ] Implement Swift native unit tests for pipeline key hashing/equality.
  - [ ] Implement unit tests for format mapping and resource binding metadata.
  - [ ] Implement tests for deferred destruction/resource lifetime queue behavior.
  - [ ] Add shader pipeline smoke tests (compile + translation + function creation).
  - [ ] Add automated integration scenarios (world render, UI, particles, transparency, post-processing).
  - [ ] Add golden-image or tolerance-based render validation where practical.
  - [ ] Add performance regression jobs with tracked historical metrics.
  - [ ] Define and enforce CI pass/fail thresholds for stability and performance.

- [ ] 11. Security, Reliability, and Operational Hardening
  - [ ] Audit JNI boundaries for pointer safety, bounds validation, and lifetime correctness.
  - [ ] Add guardrails for invalid state transitions and unsupported draw modes.
  - [ ] Add watchdog handling for catastrophic renderer initialization failure.
  - [ ] Validate graceful fallback behavior and user messaging on failure.
  - [ ] Add memory pressure monitoring and leak detection workflow.
  - [ ] Add release checklist for symbols, crash diagnostics, and reproducible builds.

- [ ] 12. Milestones and Release Plan
  - [ ] M1: Native bootstrap + `CAMetalLayer` clear/present loop complete.
  - [ ] M2: Basic world/UI geometry rendering through Metal complete.
  - [ ] M3: Shader transpilation and uniform/resource binding functional.
  - [ ] M4: Render target and post-processing parity complete.
  - [ ] M5: Stabilization, optimization, and public alpha readiness.
  - [ ] Internal alpha: run targeted hardware test cycle and triage top issues.
  - [ ] Public alpha: expand hardware/version coverage and prioritize crash/perf fixes.
  - [ ] Beta: compatibility hardening, performance tuning, and documentation polish.
  - [ ] Stable release: publish support matrix, known limitations, and migration notes.

- [ ] 13. Documentation and Project Closure
  - [ ] Keep architecture and implementation docs synchronized with delivered behavior.
  - [ ] Publish contributor guide for Java/native debugging workflows.
  - [ ] Publish user install/configuration/known-issues documentation.
  - [ ] Create final release readiness report covering quality gates and residual risks.
  - [ ] Archive completed milestones and roll over remaining items to next roadmap cycle.
