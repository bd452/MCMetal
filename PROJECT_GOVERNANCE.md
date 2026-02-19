# Project Governance and Readiness Baseline

Status: Approved  
Effective date: 2026-02-19  
Applies to: MCMetal roadmap execution and `TODO.md` section 0

## 1. Supported target matrix (confirmed)

| Area | Supported target | Coverage requirement | Notes |
| --- | --- | --- | --- |
| Minecraft | `1.21.1` | Required for MVP and release | First locked delivery target. |
| Fabric Loader | `0.16.x` (latest patch in range) | Required for MVP and release | Patch updates in range are allowed after smoke validation. |
| Fabric API | `0.116.x + 1.21.1` compatible line | Required for MVP and release | Keep API pinned in release branch; bump only with compatibility checks. |
| Java runtime | `21 LTS` | Required for build, CI, and runtime support | Aligns with current Minecraft toolchain expectations. |
| macOS | `13 Ventura`, `14 Sonoma`, `15 Sequoia` | Required for release matrix | Minimum supported version is macOS 13. |
| Apple Silicon | `M1`, `M2`, `M3`, newer families | Required for release matrix | Primary performance target architecture. |
| Intel Macs | x86_64 Macs with Metal-capable GPUs | Required for release matrix | Must be validated before every stable release. |

## 2. Scope boundaries (final)

The project scope is strictly limited to rendering backend substitution:

1. Replace Minecraft macOS OpenGL rendering execution with a Metal backend.
2. Preserve existing gameplay semantics and non-rendering subsystem behavior.
3. Keep Java mixins/adapters thin and place rendering policy in native code.
4. Limit changes to rendering-adjacent classes and startup integration needed to load the native backend.

Native implementation baseline:

- Swift-first native runtime for Metal/Cocoa integration.
- Stable C ABI JNI boundary for Java interop and compatibility.

Out of scope for implementation in this roadmap:

- Rewriting non-rendering systems (audio, networking, world logic, input, persistence).
- Replacing LWJGL components unrelated to rendering.
- Building a multi-backend abstraction layer for non-macOS platforms.

## 3. Non-goals (final)

1. No cross-platform graphics backend implementation (Metal-only objective).
2. No compatibility promise for rendering-overhaul mods that replace Blaze3D internals.
3. No full renderer redesign independent of Minecraft/Blaze3D semantics.
4. No commitment to support unsupported Minecraft/Fabric version combinations outside the matrix above.

## 4. Definition of Done and quality gates

## 4.1 MVP gate

MVP is complete only when all items pass:

- World, UI, particles, and baseline post-processing render correctly on supported hardware.
- Renderer runs without requiring an active OpenGL context for frame submission.
- Runtime remains stable for extended sessions (no crash loops, no unbounded memory growth).
- Startup failure paths are user-actionable and fail safe.

## 4.2 Performance gate

Performance gate is complete only when all items pass:

- Average FPS is equal to or better than OpenGL baseline in representative scenes.
- 1% low frame pacing is equal to or better than OpenGL baseline.
- CPU submission overhead shows measurable reduction in profiling traces on Apple Silicon.

## 4.3 Release quality gate

Release candidate is complete only when all items pass:

- CI is green for branch protection checks.
- All required automated tests pass and no release-blocking regressions remain open.
- Compatibility matrix validation runs are complete for macOS + architecture coverage.
- Documentation is updated for support policy, known limitations, and troubleshooting.

## 5. Milestone owners and review cadence

Owner assignments are role-based to keep accountability stable across staffing changes.

| Milestone | Accountable owner | Required reviewers |
| --- | --- | --- |
| M0 Governance and readiness | Project Lead | QA Lead, Release Manager |
| M1 Foundation + native bootstrap | Native Lead | Build/CI Lead |
| M2 State translation + draw path | Rendering Lead | Native Lead |
| M3 Shader pipeline and bindings | Shader Lead | Rendering Lead |
| M4 Textures and render targets | Rendering Lead | QA Lead |
| M5 Stabilization and release readiness | Release Manager | Project Lead, QA Lead |

Review cadence:

- Weekly engineering implementation review (45 minutes).
- Bi-weekly risk and mitigation review (30 minutes) against `RISK_REGISTER.md`.
- Monthly release readiness review for milestone exit criteria.
- Mandatory post-milestone retrospective with actions captured before next milestone starts.
